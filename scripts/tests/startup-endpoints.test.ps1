$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot '..\start-fitchain.ps1'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($launcher, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) { throw "Launcher parsing failed: $parseErrors" }

# Load definitions and endpoint settings without running the launcher or reading credentials.
foreach ($statement in $ast.EndBlock.Statements) {
    if ($statement -is [System.Management.Automation.Language.FunctionDefinitionAst] -or
        ($statement -is [System.Management.Automation.Language.AssignmentStatementAst] -and
         $statement.Left.Extent.Text -in @('$backendHealthUrl', '$backendApiUrl', '$frontendUrl'))) {
        . ([scriptblock]::Create($statement.Extent.Text))
    }
}
$main = $ast.EndBlock.Statements | Where-Object { $_ -is [System.Management.Automation.Language.TryStatementAst] }
$statements = @($main.Body.Statements)
$start = -1
$end = -1
for ($i = 0; $i -lt $statements.Count; $i++) {
    if ($statements[$i].Extent.Text -like "Write-Event '[[]6/6[]]*") { $start = $i + 1 }
    if ($statements[$i].Extent.Text -like "Write-Event 'FitChain is ready:*") { $end = $i - 1 }
}
if ($start -lt 0 -or $end -lt $start) { throw 'Could not locate the actual final startup checks.' }
$finalChecks = [scriptblock]::Create(($statements[$start..$end].Extent.Text -join "`n"))

# Only the HTTP boundary is replaced. The real launcher validation and failure paths run.
function Invoke-RestMethod([string]$Uri, [int]$TimeoutSec) {
    switch ($Uri) {
        'http://127.0.0.1:9091/actuator/health' { return [pscustomobject]@{ status = $script:health } }
        'http://127.0.0.1:8080/api/v1/auth/venues' {
            if ($script:apiUnavailable) { throw 'HTTP 503' }
            return ConvertFrom-Json $script:apiJson
        }
        default { throw 'HTTP 404: API documentation is disabled in production.' }
    }
}
function Invoke-WebRequest([string]$Uri, [switch]$UseBasicParsing, [int]$TimeoutSec) {
    if ($Uri -ne 'http://localhost:5173/login') { throw 'HTTP 404: API documentation is disabled in production.' }
    if ($script:frontendStatus -ge 400) { throw "HTTP $script:frontendStatus" }
    return [pscustomobject]@{ StatusCode = $script:frontendStatus }
}

$cases = @(
    @{ Name = 'Healthy startup succeeds with production API docs disabled'; Json = '{"code":0,"message":"success","data":[{"id":1,"name":"Test venue"}]}'; Fails = $false }
    @{ Name = 'A valid empty venues list does not block startup'; Json = '{"code":0,"message":"success","data":[]}'; Fails = $false }
    @{ Name = 'An unhealthy backend blocks startup'; Json = '{"code":0,"data":[]}'; Health = 'DOWN'; Fails = $true; ErrorLike = '*health check*' }
    @{ Name = 'An unavailable public API blocks startup'; Json = '{"code":0,"data":[]}'; Unavailable = $true; Fails = $true; ErrorLike = '*public venues API*' }
    @{ Name = 'A business error blocks startup despite HTTP 200'; Json = '{"code":500,"message":"failed","data":[]}'; Fails = $true; ErrorLike = '*public venues API*' }
    @{ Name = 'A missing result code blocks startup'; Json = '{"data":[]}'; Fails = $true; ErrorLike = '*public venues API*' }
    @{ Name = 'Null venues data blocks startup'; Json = '{"code":0,"data":null}'; Fails = $true; ErrorLike = '*public venues API*' }
    @{ Name = 'An object instead of a venues list blocks startup'; Json = '{"code":0,"data":{}}'; Fails = $true; ErrorLike = '*public venues API*' }
    @{ Name = 'Non-JSON API content blocks startup'; Json = '<html>Unavailable</html>'; Fails = $true; ErrorLike = '*public venues API*' }
    @{ Name = 'An unavailable login page blocks startup'; Json = '{"code":0,"data":[]}'; Frontend = 503; Fails = $true; ErrorLike = '*login page*' }
)
$failures = 0
foreach ($case in $cases) {
    $script:health = if ($case.Health) { $case.Health } else { 'UP' }
    $script:apiJson = $case.Json
    $script:apiUnavailable = [bool]$case.Unavailable
    $script:frontendStatus = if ($case.Frontend) { $case.Frontend } else { 200 }
    $failed = $false
    $failureMessage = ''
    try { & $finalChecks } catch { $failed = $true; $failureMessage = $_.Exception.Message }
    if ($failed -ne $case.Fails -or ($case.ErrorLike -and $failureMessage -notlike $case.ErrorLike)) {
        $failures++
        Write-Host "FAIL: $($case.Name) ($failureMessage)" -ForegroundColor Red
    } else {
        Write-Host "PASS: $($case.Name)" -ForegroundColor Green
    }
}
Write-Host "Startup endpoint tests: $($cases.Count - $failures)/$($cases.Count) passed."
if ($failures -gt 0) { exit 1 }
