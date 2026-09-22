param(
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontendRoot = Join-Path $projectRoot 'fitness-frontend'
$backendRoot = Join-Path $projectRoot 'fitness-backend'
$logsRoot = Join-Path $projectRoot 'logs'
$startupLog = Join-Path $logsRoot 'startup.log'
$backendLog = Join-Path $logsRoot 'backend-launcher.log'
$frontendLog = Join-Path $logsRoot 'frontend.log'
$backendRunner = Join-Path $PSScriptRoot 'run-production-backend.bat'
$frontendRunner = Join-Path $PSScriptRoot 'run-production-frontend.bat'
$postgresRestartHelper = Join-Path $PSScriptRoot 'restart-postgres-service.ps1'
$postgresServiceName = 'postgresql-x64-18'
$postgresHost = '127.0.0.1'
$postgresPort = 5433
$postgresDatabase = 'fitness_db'
$postgresUser = 'fitness_user'
$backendHealthUrl = 'http://127.0.0.1:9091/actuator/health'
$backendApiUrl = 'http://127.0.0.1:8080/api-docs'
$frontendUrl = 'http://localhost:5173/login'

New-Item -ItemType Directory -Path $logsRoot -Force | Out-Null
. (Join-Path $PSScriptRoot 'postgres-tools.ps1')

function Write-Event([string]$Message, [ConsoleColor]$Color = [ConsoleColor]::Gray) {
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $Message"
    Add-Content -LiteralPath $startupLog -Value $line -Encoding UTF8
    Write-Host $Message -ForegroundColor $Color
}

function Assert-File([string]$Path, [string]$Description) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description is missing: $Path. Restore the project file and try again."
    }
}

function Resolve-Executable([string]$PreferredPath, [string]$CommandName, [string]$DisplayName) {
    if ($PreferredPath -and (Test-Path -LiteralPath $PreferredPath -PathType Leaf)) {
        return $PreferredPath
    }
    $command = Get-Command $CommandName -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $command) {
        throw "$DisplayName was not found. Install it, or restore the project .tools directory."
    }
    return $command.Source
}

function Get-ConfiguredSecret([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not $value) { $value = [Environment]::GetEnvironmentVariable($Name, 'User') }
    return $value
}

function Import-RequiredSecret([string]$Name, [string]$Prompt, [int]$MinimumLength) {
    $value = Get-ConfiguredSecret $Name
    if (-not $value) {
        Write-Host "First-time configuration: $Prompt" -ForegroundColor Yellow
        $secureValue = Read-Host -Prompt $Name -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
        try { $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
        if (-not $value) { throw "$Name cannot be empty." }
        [Environment]::SetEnvironmentVariable($Name, $value, 'User')
        Write-Event "$Name has been saved for the current Windows user." DarkGray
    }
    if ($value.Length -lt $MinimumLength) {
        throw "$Name is configured, but it must contain at least $MinimumLength characters."
    }
    Set-Item -Path "Env:$Name" -Value $value
}

function Test-Url([string]$Url, [int]$TimeoutSeconds = 3) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSeconds
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch {
        return $false
    }
}

function Test-BackendHealth {
    try {
        $response = Invoke-RestMethod -Uri $backendHealthUrl -TimeoutSec 3
        return $response.status -eq 'UP'
    } catch {
        return $false
    }
}

function Get-PortProcesses([int]$Port) {
    $processIds = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($processId in $processIds) {
        Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
    }
}

function Test-IsFitChainProcess($Process, [string]$Component) {
    if (-not $Process) { return $false }
    $commandLine = [string]$Process.CommandLine
    if ($Component -eq 'backend') {
        return $Process.Name -eq 'java.exe' -and
            ($commandLine.IndexOf('fitness-backend-1.0.0.jar', [StringComparison]::OrdinalIgnoreCase) -ge 0 -or
             $commandLine.IndexOf($backendRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0)
    }
    return $Process.Name -eq 'node.exe' -and
        $commandLine.IndexOf($frontendRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function Clear-StalePort([int]$Port, [string]$Component) {
    $owners = @(Get-PortProcesses $Port)
    if ($owners.Count -eq 0) { return }
    $foreignOwners = @($owners | Where-Object { -not (Test-IsFitChainProcess $_ $Component) })
    if ($foreignOwners.Count -gt 0) {
        $details = ($foreignOwners | ForEach-Object { "PID $($_.ProcessId) ($($_.Name))" }) -join ', '
        throw "Port $Port is used by another program: $details. FitChain will not terminate unrelated programs. Close it and retry."
    }
    foreach ($owner in $owners) {
        Write-Event "Removing stale FitChain $component process PID $($owner.ProcessId) from port $Port..." Yellow
        Stop-Process -Id $owner.ProcessId -Force -ErrorAction SilentlyContinue
    }
    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-PortProcesses $Port) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 250 }
    if (Get-PortProcesses $Port) { throw "The stale FitChain process on port $Port could not be stopped." }
}

function Wait-ForComponent(
    [scriptblock]$Condition,
    [Diagnostics.Process]$Process,
    [int]$TimeoutSeconds,
    [string]$ComponentName
) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (& $Condition) { return $true }
        if ($Process) {
            $Process.Refresh()
            if ($Process.HasExited) {
                Write-Event "$ComponentName exited early with code $($Process.ExitCode)." Yellow
                return $false
            }
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    Write-Event "$ComponentName did not become ready within $TimeoutSeconds seconds." Yellow
    return $false
}

function Get-LogTail([string]$Path, [int]$Lines = 35) {
    if (-not (Test-Path -LiteralPath $Path)) { return '(log file was not created)' }
    return (Get-Content -LiteralPath $Path -Tail $Lines -ErrorAction SilentlyContinue) -join [Environment]::NewLine
}

function Test-PostgresReady {
    try {
        $pgIsReady = Find-PostgresTool 'pg_isready'
        & $pgIsReady '-h' $postgresHost '-p' $postgresPort '-d' $postgresDatabase '-t' '3' '-q' 2>$null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Restart-Postgres {
    $service = Get-Service -Name $postgresServiceName -ErrorAction SilentlyContinue
    if (-not $service) {
        throw "PostgreSQL service '$postgresServiceName' was not found. Reinstall PostgreSQL 18 or update the launcher configuration."
    }
    Write-Event 'PostgreSQL is installed but not responding. Approve the Windows administrator prompt to repair it.' Yellow
    try {
        $arguments = @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass',
            '-File', "`"$postgresRestartHelper`"",
            '-ServiceName', $postgresServiceName
        )
        $repair = Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $arguments -PassThru -Wait
        if ($repair.ExitCode -ne 0) { throw "repair process exit code $($repair.ExitCode)" }
    } catch {
        throw "PostgreSQL could not be restarted. Approve the administrator prompt and retry. Details: $($_.Exception.Message)"
    }
    $deadline = (Get-Date).AddSeconds(45)
    while (-not (Test-PostgresReady) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 1 }
    if (-not (Test-PostgresReady)) {
        throw 'PostgreSQL is still unavailable after automatic restart. Run diagnose-startup.bat and inspect the PostgreSQL service log.'
    }
    Write-Event 'PostgreSQL recovered successfully.' Green
}

function Test-PostgresLogin([string]$Password) {
    $psql = Find-PostgresTool 'psql'
    $oldPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    $oldSslMode = [Environment]::GetEnvironmentVariable('PGSSLMODE', 'Process')
    try {
        $env:PGPASSWORD = $Password
        $env:PGSSLMODE = 'disable'
        $output = & $psql '-h' $postgresHost '-p' $postgresPort '-U' $postgresUser '-d' $postgresDatabase '-w' '-tA' '-c' 'SELECT 1;' 2>$null
        return $LASTEXITCODE -eq 0 -and ($output -join '').Trim() -eq '1'
    } finally {
        if ($null -eq $oldPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue } else { $env:PGPASSWORD = $oldPassword }
        if ($null -eq $oldSslMode) { Remove-Item Env:PGSSLMODE -ErrorAction SilentlyContinue } else { $env:PGSSLMODE = $oldSslMode }
    }
}

function Ensure-Postgres {
    Write-Event '[2/6] Checking PostgreSQL service, network and login...' Yellow
    if (-not (Test-PostgresReady)) { Restart-Postgres }
    $dbPassword = Get-ConfiguredSecret 'DB_PASSWORD'
    if (-not (Test-PostgresLogin $dbPassword)) {
        Restart-Postgres
        if (-not (Test-PostgresLogin $dbPassword)) {
            throw 'PostgreSQL accepts network connections, but fitness_user authentication failed. DB_PASSWORD may not match the database account. Run diagnose-startup.bat; no password was printed or logged.'
        }
    }
    Write-Event '      PostgreSQL is healthy and fitness_db login succeeded.' Green
}

function Install-FrontendDependencies([string]$Npm) {
    Write-Event 'Repairing frontend dependencies with npm install...' Yellow
    Push-Location $frontendRoot
    try {
        & $Npm install
        if ($LASTEXITCODE -ne 0) { throw "npm install exited with code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
}

function Start-HiddenBatch([string]$BatchFile) {
    $command = "call `"$BatchFile`""
    return Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c', $command) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru
}

function Start-Backend {
    if (Test-BackendHealth) {
        Write-Event '[4/6] Backend is already healthy.' Green
        return
    }
    Clear-StalePort 8080 'backend'
    Clear-StalePort 9091 'backend'
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        Write-Event "[4/6] Starting backend (attempt $attempt/2)..." Yellow
        $process = Start-HiddenBatch $backendRunner
        if (Wait-ForComponent { Test-BackendHealth } $process 120 'Backend') {
            Write-Event '      Backend health check passed.' Green
            return
        }
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        Clear-StalePort 8080 'backend'
        Clear-StalePort 9091 'backend'
        if ($attempt -eq 1) {
            Ensure-Postgres
            Write-Event '      Backend will retry once after self-repair.' Yellow
        }
    }
    throw "Backend failed twice. Recent backend output:`r`n$(Get-LogTail $backendLog)"
}

function Start-Frontend([string]$Npm) {
    if (Test-Url $frontendUrl) {
        Write-Event '[5/6] Frontend is already healthy.' Green
        return
    }
    Clear-StalePort 5173 'frontend'
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        Write-Event "[5/6] Starting frontend (attempt $attempt/2)..." Yellow
        $process = Start-HiddenBatch $frontendRunner
        if (Wait-ForComponent { Test-Url $frontendUrl } $process 45 'Frontend') {
            Write-Event '      Frontend health check passed.' Green
            return
        }
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        Clear-StalePort 5173 'frontend'
        if ($attempt -eq 1) {
            Install-FrontendDependencies $Npm
            Write-Event '      Frontend will retry once after dependency repair.' Yellow
        }
    }
    throw "Frontend failed twice. Recent frontend output:`r`n$(Get-LogTail $frontendLog)"
}

$mutex = New-Object System.Threading.Mutex($false, 'Local\FitChainLauncher')
$lockTaken = $false
try {
    try { $lockTaken = $mutex.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $lockTaken = $true }
    if (-not $lockTaken) {
        Write-Host 'FitChain is already being started. Please wait for the first launcher.' -ForegroundColor Yellow
        exit 0
    }

    Write-Host ''
    Write-Host '========================================' -ForegroundColor DarkCyan
    Write-Host ' FitChain Self-Healing Launcher v2.0' -ForegroundColor Cyan
    Write-Host '========================================' -ForegroundColor DarkCyan
    Write-Event 'Launcher started.' DarkGray

    Write-Event '[1/6] Checking project files, tools and configuration...' Yellow
    Assert-File $backendRunner 'Backend launcher'
    Assert-File $frontendRunner 'Frontend launcher'
    Assert-File $postgresRestartHelper 'PostgreSQL repair helper'
    Assert-File (Join-Path $frontendRoot 'package.json') 'Frontend package.json'
    Assert-File (Join-Path $backendRoot 'pom.xml') 'Backend pom.xml'

    $bundledJavaHome = Join-Path $projectRoot '.tools\jdk-17'
    $java = Resolve-Executable (Join-Path $bundledJavaHome 'bin\java.exe') 'java' 'Java 17'
    $maven = Resolve-Executable (Join-Path $projectRoot '.tools\maven\bin\mvn.cmd') 'mvn.cmd' 'Maven'
    $node = Resolve-Executable $null 'node.exe' 'Node.js 18+'
    $npm = Resolve-Executable $null 'npm.cmd' 'npm'
    $nodeVersion = (& $node '--version').Trim()
    if ($nodeVersion -notmatch '^v(\d+)\.' -or [int]$Matches[1] -lt 18) {
        throw "Node.js 18 or newer is required; detected $nodeVersion."
    }
    if (Test-Path -LiteralPath $bundledJavaHome -PathType Container) { $env:JAVA_HOME = $bundledJavaHome }
    $javaCheck = Start-Process -FilePath $java -ArgumentList '-version' -WindowStyle Hidden -Wait -PassThru
    if ($javaCheck.ExitCode -ne 0) { throw 'The bundled Java runtime cannot start.' }
    $mavenCheck = Start-Process -FilePath $maven -ArgumentList '-version' -WindowStyle Hidden -Wait -PassThru
    if ($mavenCheck.ExitCode -ne 0) { throw 'The bundled Maven runtime cannot start.' }

    Import-RequiredSecret 'DB_PASSWORD' 'enter the PostgreSQL fitness_user password' 1
    Import-RequiredSecret 'JWT_SECRET' 'enter a JWT secret of at least 32 characters' 32
    Import-RequiredSecret 'APP_ADMIN_PASSWORD' 'enter the initial administrator password' 8
    Write-Event "      Java, Maven, Node.js $nodeVersion, npm and required settings are ready." Green

    Ensure-Postgres

    Write-Event '[3/6] Checking frontend dependencies...' Yellow
    if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'node_modules\vite\bin\vite.js'))) {
        Install-FrontendDependencies $npm
    }
    Write-Event '      Frontend dependencies are ready.' Green

    Start-Backend
    Start-Frontend $npm

    Write-Event '[6/6] Running final end-to-end checks...' Yellow
    if (-not (Test-BackendHealth)) { throw 'Final backend health check failed.' }
    if (-not (Test-Url $backendApiUrl 5)) { throw 'Backend started, but the API documentation endpoint is unreachable.' }
    if (-not (Test-Url $frontendUrl 5)) { throw 'Frontend started, but the login page is unreachable.' }
    Write-Event 'FitChain is ready: http://localhost:5173/login' Cyan
    Write-Host "Startup log: $startupLog" -ForegroundColor DarkGray
    Write-Host 'You may close this window; the system will keep running.' -ForegroundColor DarkGray
    if (-not $NoBrowser) { Start-Process $frontendUrl }
} catch {
    $message = $_.Exception.Message
    Write-Event "STARTUP FAILED: $message" Red
    Write-Host ''
    Write-Host 'FitChain did not open, but the exact cause has been recorded.' -ForegroundColor Red
    Write-Host 'Run diagnose-startup.bat if you need a shareable report.' -ForegroundColor Yellow
    Write-Host "Startup log: $startupLog" -ForegroundColor Yellow
    exit 1
} finally {
    if ($lockTaken) { try { $mutex.ReleaseMutex() } catch {} }
    $mutex.Dispose()
    'DB_PASSWORD','JWT_SECRET','APP_ADMIN_PASSWORD','JAVA_HOME' | ForEach-Object { Remove-Item "Env:$_" -ErrorAction SilentlyContinue }
}
