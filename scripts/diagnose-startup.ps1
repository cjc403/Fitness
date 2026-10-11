$ErrorActionPreference = 'Continue'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logsRoot = Join-Path $projectRoot 'logs'
$reportPath = Join-Path $logsRoot 'startup-diagnostics.txt'
$report = New-Object System.Collections.Generic.List[string]

New-Item -ItemType Directory -Path $logsRoot -Force | Out-Null
. (Join-Path $PSScriptRoot 'postgres-tools.ps1')

function Add-Line([string]$Text = '') {
    $report.Add($Text)
    Write-Host $Text
}

function Test-Endpoint([string]$Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 4
        return "HTTP $($response.StatusCode)"
    } catch {
        if ($_.Exception.Response) { return "HTTP $([int]$_.Exception.Response.StatusCode)" }
        return "UNREACHABLE ($($_.Exception.Message))"
    }
}

function Add-ToolVersion([string]$Name, [string]$PreferredPath, [string]$CommandName, [string[]]$Arguments) {
    try {
        $path = if ($PreferredPath -and (Test-Path -LiteralPath $PreferredPath)) {
            $PreferredPath
        } else {
            (Get-Command $CommandName -ErrorAction Stop | Select-Object -First 1).Source
        }
        $version = (& $path @Arguments 2>&1 | Select-Object -First 1) -join ''
        Add-Line "${Name}: OK - $version"
    } catch {
        Add-Line "${Name}: MISSING OR BROKEN - $($_.Exception.Message)"
    }
}

Add-Line 'FitChain startup diagnostic report'
Add-Line "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"
Add-Line "Project: $projectRoot"
Add-Line
Add-Line '=== Required tools ==='
Add-ToolVersion 'Java' (Join-Path $projectRoot '.tools\jdk-17\bin\java.exe') 'java.exe' @('-version')
Add-ToolVersion 'Maven' (Join-Path $projectRoot '.tools\maven\bin\mvn.cmd') 'mvn.cmd' @('-version')
Add-ToolVersion 'Node.js' $null 'node.exe' @('--version')
Add-ToolVersion 'npm' $null 'npm.cmd' @('--version')

Add-Line
Add-Line '=== Configuration (values are never shown) ==='
foreach ($name in @('DB_PASSWORD', 'JWT_SECRET', 'APP_ADMIN_PASSWORD', 'DEEPSEEK_API_KEY')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not $value) { $value = [Environment]::GetEnvironmentVariable($name, 'User') }
    Add-Line "${name}: present=$([bool]$value), length=$($value.Length)"
}

Add-Line
Add-Line '=== PostgreSQL ==='
$service = Get-Service -Name 'postgresql-x64-18' -ErrorAction SilentlyContinue
if ($service) {
    Add-Line "Service: $($service.Status), start type=$($service.StartType)"
} else {
    Add-Line 'Service: NOT FOUND (expected postgresql-x64-18)'
}
try {
    $pgIsReady = Find-PostgresTool 'pg_isready'
    & $pgIsReady '-h' '127.0.0.1' '-p' '5433' '-d' 'fitness_db' '-t' '3' '-q' 2>$null
    Add-Line "Network readiness: $(if ($LASTEXITCODE -eq 0) { 'OK' } else { "FAILED (exit $LASTEXITCODE)" })"
} catch {
    Add-Line "Network readiness: CHECK FAILED - $($_.Exception.Message)"
}
$dbPassword = [Environment]::GetEnvironmentVariable('DB_PASSWORD', 'Process')
if (-not $dbPassword) { $dbPassword = [Environment]::GetEnvironmentVariable('DB_PASSWORD', 'User') }
if ($dbPassword) {
    try {
        $psql = Find-PostgresTool 'psql'
        $oldPassword = $env:PGPASSWORD
        $oldSslMode = $env:PGSSLMODE
        $env:PGPASSWORD = $dbPassword
        $env:PGSSLMODE = 'disable'
        $result = & $psql '-h' '127.0.0.1' '-p' '5433' '-U' 'fitness_user' '-d' 'fitness_db' '-w' '-tA' '-c' 'SELECT 1;' 2>$null
        Add-Line "Authenticated query: $(if ($LASTEXITCODE -eq 0 -and ($result -join '').Trim() -eq '1') { 'OK' } else { 'FAILED' })"
        if ($null -eq $oldPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue } else { $env:PGPASSWORD = $oldPassword }
        if ($null -eq $oldSslMode) { Remove-Item Env:PGSSLMODE -ErrorAction SilentlyContinue } else { $env:PGSSLMODE = $oldSslMode }
    } catch {
        Add-Line "Authenticated query: CHECK FAILED - $($_.Exception.Message)"
    }
} else {
    Add-Line 'Authenticated query: SKIPPED (DB_PASSWORD is missing)'
}

Add-Line
Add-Line '=== Ports and process owners ==='
foreach ($port in @(5433, 8080, 9091, 5173)) {
    $connections = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    if ($connections.Count -eq 0) {
        Add-Line "Port ${port}: free"
        continue
    }
    $owners = @($connections | Select-Object -ExpandProperty OwningProcess -Unique)
    foreach ($ownerId in $owners) {
        $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerId" -ErrorAction SilentlyContinue
        Add-Line "Port ${port}: listening - PID $ownerId ($($owner.Name))"
    }
}

Add-Line
Add-Line '=== HTTP endpoints ==='
Add-Line "Backend health: $(Test-Endpoint 'http://127.0.0.1:9091/actuator/health')"
Add-Line "Backend public venues API: $(Test-Endpoint 'http://127.0.0.1:8080/api/v1/auth/venues')"
Add-Line "Backend API docs (optional; HTTP 404 is expected when disabled in production): $(Test-Endpoint 'http://127.0.0.1:8080/api-docs')"
Add-Line "Frontend login: $(Test-Endpoint 'http://localhost:5173/login')"

Add-Line
Add-Line '=== Log files ==='
foreach ($name in @('startup.log', 'backend-launcher.log', 'fitness-backend.log', 'frontend.log')) {
    $path = Join-Path $logsRoot $name
    if (Test-Path -LiteralPath $path) {
        $file = Get-Item -LiteralPath $path
        Add-Line "${name}: present, $($file.Length) bytes, modified $($file.LastWriteTime)"
    } else {
        Add-Line "${name}: missing"
    }
}

$report | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host ''
Write-Host "Diagnostic report saved to: $reportPath" -ForegroundColor Cyan
