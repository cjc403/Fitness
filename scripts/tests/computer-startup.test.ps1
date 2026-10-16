$ErrorActionPreference='Stop'
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot '..\start-local-agent.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Computer launcher parse failed'}
foreach($s in $ast.EndBlock.Statements){if($s -is [Management.Automation.Language.FunctionDefinitionAst]){. ([scriptblock]::Create($s.Extent.Text))}}
$fingerprint=Get-ComputerProjectFingerprint 'C:\fixture\FitChain'
if($fingerprint -cne (Get-ComputerProjectFingerprint 'c:/fixture/fitchain/')){throw 'Fingerprint normalization failed'}
$identity=[pscustomobject]@{code=0;data=[pscustomobject]@{app='fitchain-computer-runner';projectFingerprint=$fingerprint;pid=7788;port=5188}}
$listener=[pscustomobject]@{OwningProcess=7788;LocalAddress='127.0.0.1'}
if(-not(Test-ComputerRunnerIdentity $identity @($listener) $fingerprint)){throw 'Own runner not recognized'}
if(Test-ComputerRunnerIdentity ([pscustomobject]@{code=503;data=$identity.data}) @($listener) $fingerprint){throw 'Unhealthy runner accepted'}
foreach($bad in @([pscustomobject]@{OwningProcess=7789;LocalAddress='127.0.0.1'},[pscustomobject]@{OwningProcess=7788;LocalAddress='0.0.0.0'})){
 if(Test-ComputerRunnerIdentity $identity @($bad) $fingerprint){throw 'Unknown or LAN runner accepted'}
}
if(Test-ComputerRunnerIdentity $identity @($listener) ('f'*64)){throw 'Different workspace accepted'}

$env:FITCHAIN_TEST_API_KEY='fixture-secret'
function Start-Process {
 param($FilePath,$ArgumentList,$WorkingDirectory,$WindowStyle,[switch]$PassThru,$RedirectStandardOutput,$RedirectStandardError)
 if(Test-Path Env:FITCHAIN_TEST_API_KEY){throw 'Secret inherited by companion'}
 if($WindowStyle -ne 'Hidden'){throw 'Background launcher opens visible console'}
 if(($ArgumentList -join ' ') -notlike '*--config*'){throw 'Missing private configuration argument'}
 return [pscustomobject]@{Id=7788}
}
try {
 $p=Start-ComputerRunnerProcess 'node.exe' 'C:\fixture\runner.mjs' 'C:\private\bridge.json' 'C:\fixture' 'C:\logs'
 if($p.Id -ne 7788 -or $env:FITCHAIN_TEST_API_KEY -ne 'fixture-secret'){throw 'Secret restoration or runner result failed'}
}finally{Remove-Item Env:FITCHAIN_TEST_API_KEY -ErrorAction SilentlyContinue}
Write-Host 'Computer startup boundaries: 7/7 passed (fingerprint, own PID, health envelope, foreign PID, LAN binding, other workspace, secret-free hidden child).'
