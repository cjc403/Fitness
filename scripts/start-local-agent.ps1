param([switch]$NoInstall)
$ErrorActionPreference = 'Stop'

function Get-ComputerProjectFingerprint([string]$Root) {
    $normalized = [IO.Path]::GetFullPath($Root).TrimEnd('\','/').Replace('\','/').ToLowerInvariant()
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($normalized)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Start-ComputerRunnerProcess([string]$Node, [string]$Entry, [string]$Config, [string]$WorkingDirectory, [string]$LogDirectory) {
    # The companion needs a local bridge capability, never database/model secrets.
    $saved = @{}
    Get-ChildItem Env: | Where-Object { $_.Name -match '(?i)(^DB_|PASSWORD|SECRET|TOKEN|API_KEY)' } | ForEach-Object {
        $saved[$_.Name] = $_.Value; Remove-Item -LiteralPath ('Env:' + $_.Name)
    }
    try {
        return Start-Process -FilePath $Node -ArgumentList @(('"{0}"' -f $Entry),'--config',('"{0}"' -f $Config)) -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $LogDirectory 'computer-agent.log') -RedirectStandardError (Join-Path $LogDirectory 'computer-agent-error.log')
    } finally { foreach ($name in $saved.Keys) { Set-Item -LiteralPath ('Env:' + $name) -Value $saved[$name] } }
}

function Test-ComputerRunnerIdentity($Identity, $Listeners, [string]$Fingerprint) {
    $health = $Identity.data
    return $null -ne $Identity -and $Identity.code -eq 0 -and $null -ne $health -and $health.app -eq 'fitchain-computer-runner' -and
        $health.projectFingerprint -ceq $Fingerprint -and $health.port -eq 5188 -and
        @($Listeners).Count -gt 0 -and
        @($Listeners | Where-Object { $_.OwningProcess -ne $health.pid -or $_.LocalAddress -notin @('127.0.0.1','::1') }).Count -eq 0
}

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runner = Join-Path $repo 'fitness-agent-runner'
$entry = Join-Path $runner 'src\index.mjs'
$fingerprint = Get-ComputerProjectFingerprint $repo
$mutex = New-Object Threading.Mutex($false, ('Local\FitChainComputerAgent-' + $fingerprint))
$locked = $false
try {
    $locked = $mutex.WaitOne(15000)
    if (-not $locked) { throw 'Computer executor startup is still in progress. Retry shortly.' }
    $listeners = @(Get-NetTCPConnection -LocalPort 5188 -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count) {
        $identity = $null
        try { $identity = Invoke-RestMethod 'http://127.0.0.1:5188/v1/health' -TimeoutSec 3 } catch {}
        if (-not (Test-ComputerRunnerIdentity $identity $listeners $fingerprint)) { throw 'Port 5188 could not be verified as this local executor. No process has been stopped.' }
        Write-Host 'Local file capability is already healthy (optional). Page tasks run in the current tab.' -ForegroundColor Green
        return
    }
    if (-not (Test-Path -LiteralPath $entry)) { throw 'Computer executor files are missing.' }
    $node = (Get-Command node.exe -ErrorAction Stop).Source
    if (-not (Test-Path -LiteralPath (Join-Path $runner 'node_modules\playwright-core\package.json'))) {
        if ($NoInstall) { throw 'Computer executor dependencies are missing. Run npm ci inside fitness-agent-runner.' }
        Push-Location $runner
        try { & (Get-Command npm.cmd -ErrorAction Stop).Source ci --ignore-scripts; if ($LASTEXITCODE -ne 0) { throw 'Computer executor dependency installation failed.' } }
        finally { Pop-Location }
    }
    $edge = Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe'
    if (-not (Test-Path -LiteralPath $edge)) { throw 'Microsoft Edge was not found. Install Edge before starting the computer executor.' }
    $privateDirectory = Join-Path $env:LOCALAPPDATA ('FitChainAgent\' + $fingerprint)
    New-Item -ItemType Directory -Path $privateDirectory -Force | Out-Null
    $acl = Get-Acl -LiteralPath $privateDirectory
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value, 'S-1-5-18')) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule([Security.Principal.SecurityIdentifier]::new($sid), 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $privateDirectory -AclObject $acl
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $token = ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
    $outputName = 'FitChain' + [char]0x4efb + [char]0x52a1 + [char]0x6210 + [char]0x679c
    $config = [ordered]@{ token=$token; port=5188; pid=0; projectFingerprint=$fingerprint; frontendOrigin='http://localhost:5173'; backendOrigin='http://localhost:8080'; outputDirectory=(Join-Path ([Environment]::GetFolderPath('Desktop')) $outputName); executablePath=$edge }
    $configPath = Join-Path $privateDirectory 'bridge.json'
    [IO.File]::WriteAllText($configPath, ($config | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    $token = $null
    $logs = Join-Path $repo 'logs'
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    $process = Start-ComputerRunnerProcess $node $entry $configPath $runner $logs
    $config.pid = $process.Id
    [IO.File]::WriteAllText($configPath, ($config | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    $ready = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        try {
            $identity = Invoke-RestMethod 'http://127.0.0.1:5188/v1/health' -TimeoutSec 1
            $listeners = @(Get-NetTCPConnection -LocalPort 5188 -State Listen -ErrorAction SilentlyContinue)
            if (Test-ComputerRunnerIdentity $identity $listeners $fingerprint) { $ready=$true; break }
        } catch {}
        if ($process.HasExited) { break }
        Start-Sleep -Milliseconds 400
    }
    if (-not $ready) { throw 'Computer executor did not become ready. Check logs/computer-agent-error.log.' }
    Write-Host 'Local file capability is ready (optional). Page tasks run in the current tab.' -ForegroundColor Cyan
} finally {
    if ($locked) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
