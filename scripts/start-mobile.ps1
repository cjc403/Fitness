param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontendRoot = Join-Path $projectRoot 'fitness-frontend'
$logPath = Join-Path $projectRoot 'logs\mobile-frontend.log'
$errorLog = Join-Path $projectRoot 'logs\mobile-frontend-error.log'
. (Join-Path $PSScriptRoot 'mobile-dev-tools.ps1')
try {
    # Reuse the existing checked local launcher. No deployment, firewall or certificate changes.
    & (Join-Path $PSScriptRoot 'start-fitchain.ps1') -NoBrowser
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort 5174 -ErrorAction SilentlyContinue)
    $listener = $listeners | Select-Object -First 1
    if ($listener) {
        Assert-MobileFrontendListener -Listeners $listeners -FrontendRoot $frontendRoot
        Write-Host 'Reusing the verified FitChain mobile frontend.' -ForegroundColor Green
    } else {
        $node = (Get-Command node.exe -ErrorAction Stop).Source
        $vite = Join-Path $frontendRoot 'node_modules\vite\bin\vite.js'
        if (-not (Test-Path -LiteralPath $vite)) { throw 'Vite is missing. Complete the normal start.bat startup first.' }
        $arguments = @(('"{0}"' -f $vite), '--host', '0.0.0.0', '--port', '5174', '--strictPort')
        Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $frontendRoot -WindowStyle Hidden -RedirectStandardOutput $logPath -RedirectStandardError $errorLog | Out-Null
    }
    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try { $response = Invoke-WebRequest 'http://127.0.0.1:5174/login' -UseBasicParsing -TimeoutSec 2; if ($response.StatusCode -eq 200) { $ready = $true; break } } catch {}
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Mobile frontend did not become ready. Check the mobile frontend logs.' }
    Assert-MobileFrontendListener -Listeners @(Get-NetTCPConnection -State Listen -LocalPort 5174 -ErrorAction SilentlyContinue) -FrontendRoot $frontendRoot
    Write-Host 'Mobile H5 is ready. Connect the phone and computer to the same trusted network.' -ForegroundColor Cyan
    $addresses = @(Get-MobileLanAddresses)
    if ($addresses.Count -eq 0) { Write-Host 'No connected physical Wi-Fi/Ethernet IPv4 address was found. Connect a network and retry.' -ForegroundColor Yellow }
    $addresses | ForEach-Object { Write-Host ("Phone URL: http://{0}:5174/login" -f $_) }
    Write-Host 'Desktop: http://localhost:5173/login'
    Write-Host 'Install/offline features require HTTPS or localhost; LAN HTTP supports online H5.'
    Write-Host 'If Windows blocks LAN access, review its prompt. This script does not change firewall rules.'
    if (-not $NoBrowser) { Start-Process 'http://localhost:5174/login' }
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
