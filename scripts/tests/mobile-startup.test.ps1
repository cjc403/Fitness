$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot '..\start-mobile.ps1'
$tools = Join-Path $PSScriptRoot '..\mobile-dev-tools.ps1'
if (Test-Path -LiteralPath $tools) { . $tools }
$tokens = $null; $errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($launcher, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Mobile launcher syntax error.' }
$try = $ast.EndBlock.Statements | Where-Object { $_ -is [System.Management.Automation.Language.TryStatementAst] } | Select-Object -First 1
$branch = $try.Body.Statements | Where-Object { $_ -is [System.Management.Automation.Language.IfStatementAst] -and $_.Clauses[0].Item1.Extent.Text -eq '$listener' } | Select-Object -First 1
if (-not $branch) { throw 'Listener ownership branch is missing.' }
$checkOwner = [scriptblock]::Create($branch.Extent.Text)
$frontendRoot = 'C:\fixture\fitness-frontend'
$listener = [pscustomobject]@{ OwningProcess = 7788; LocalAddress = '0.0.0.0' }
$listeners = @($listener)
$sha = [Security.Cryptography.SHA256]::Create()
try { $expectedWorkspace = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($frontendRoot.Replace('\','/').ToLowerInvariant())))).Replace('-','').ToLowerInvariant() } finally { $sha.Dispose() }
function Get-CimInstance { param($ClassName, $Filter, $ErrorAction); return $script:owner }
function Invoke-RestMethod { param($Uri, $TimeoutSec, $ErrorAction); if ($script:probeFails) { throw 'HTTP not available' }; return $script:identity }
$cases = @(
    @{ Name='Reuse exact workspace even when elevated process command line is unreadable'; Command=$null; Fails=$false }
    @{ Name='Reuse readable exact-workspace listener'; Command='node C:\fixture\fitness-frontend\node_modules\vite\bin\vite.js'; Fails=$false }
    @{ Name='A different workspace is rejected even with a convincing command line'; Command='node C:\fixture\fitness-frontend\node_modules\vite\bin\vite.js'; Workspace='different'; Fails=$true }
    @{ Name='A stale or mismatched PID is rejected'; Command=$null; Pid=7789; Fails=$true }
    @{ Name='A generic HTML or unavailable endpoint cannot prove ownership'; Command='node C:\fixture\fitness-frontend\vite.js'; ProbeFails=$true; Fails=$true }
    @{ Name='A desktop-only loopback listener cannot serve a phone'; Command='node C:\fixture\fitness-frontend\vite.js'; Address='127.0.0.1'; Fails=$true }
)
$failed = 0
foreach ($case in $cases) {
    $script:owner = [pscustomobject]@{ Name='node.exe'; CommandLine=$case.Command; ProcessId=7788 }
    $script:identity = [pscustomobject]@{ app='fitchain-vite'; workspace=if($case.Workspace){$case.Workspace}else{$expectedWorkspace}; pid=if($case.Pid){$case.Pid}else{7788}; port=5174 }
    $script:probeFails = [bool]$case.ProbeFails
    $listener.LocalAddress = if($case.Address){$case.Address}else{'0.0.0.0'}
    $caught = $false
    try { & $checkOwner } catch { $caught = $true }
    if ($caught -ne $case.Fails) { $failed++; Write-Host "FAIL: $($case.Name)" } else { Write-Host "PASS: $($case.Name)" }
}

# Fake only OS inventory; run the real address selection, never modify a network adapter.
function Get-NetAdapter { param([switch]$Physical, $ErrorAction); return @(
    [pscustomobject]@{ ifIndex=8; Status='Up'; HardwareInterface=$true; Virtual=$false },
    [pscustomobject]@{ ifIndex=4; Status='Up'; HardwareInterface=$false; Virtual=$true },
    [pscustomobject]@{ ifIndex=20; Status='Disconnected'; HardwareInterface=$true; Virtual=$false }) }
function Get-NetIPAddress { param($AddressFamily, $ErrorAction); return @(
    [pscustomobject]@{ InterfaceIndex=4; IPAddress='198.18.0.1'; AddressState='Preferred' },
    [pscustomobject]@{ InterfaceIndex=8; IPAddress='192.168.1.71'; AddressState='Preferred' },
    [pscustomobject]@{ InterfaceIndex=20; IPAddress='169.254.1.1'; AddressState='Preferred' },
    [pscustomobject]@{ InterfaceIndex=8; IPAddress='192.168.1.72'; AddressState='Tentative' }) }
try {
    $addresses = @(Get-MobileLanAddresses)
    if ($addresses.Count -ne 1 -or $addresses[0] -ne '192.168.1.71') { throw 'Wrong preferred phone addresses.' }
    Write-Host 'PASS: Only ready addresses on connected physical adapters are recommended'
} catch { $failed++; Write-Host 'FAIL: Only ready addresses on connected physical adapters are recommended' }
Write-Host "Mobile startup tests: $((7)-$failed)/7 passed."
if ($failed) { exit 1 }
