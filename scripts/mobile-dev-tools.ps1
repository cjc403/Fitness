# Read-only discovery helpers. Never kill a listener or change networking.
function Get-MobileWorkspaceFingerprint([string]$FrontendRoot) {
    $normalized = [IO.Path]::GetFullPath($FrontendRoot).TrimEnd('\','/').Replace('\','/').ToLowerInvariant()
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($normalized)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Assert-MobileFrontendListener($Listeners, [string]$FrontendRoot) {
    $owners = @($Listeners)
    if (-not ($owners | Where-Object { $_.LocalAddress -in @('0.0.0.0', '::') })) {
        throw 'Port 5174 only listens on loopback. The phone needs a LAN listener. No process has been stopped.'
    }
    try { $identity = Invoke-RestMethod -Uri 'http://127.0.0.1:5174/__fitchain/dev-health' -TimeoutSec 3 -ErrorAction Stop }
    catch { throw 'Port 5174 is listening, but this FitChain workspace could not be verified. Check logs/mobile-frontend.log. No process has been stopped.' }
    $expected = Get-MobileWorkspaceFingerprint $FrontendRoot
    if ($identity.app -ne 'fitchain-vite' -or $identity.workspace -cne $expected -or $identity.port -ne 5174 -or
        @($owners | Where-Object { $_.OwningProcess -ne $identity.pid }).Count -gt 0) {
        throw 'Port 5174 belongs to an unverified or different project. No process has been stopped.'
    }
}

function Get-MobileLanAddresses {
    $physical = @(Get-NetAdapter -Physical -ErrorAction SilentlyContinue | Where-Object {
        $_.Status -eq 'Up' -and $_.HardwareInterface -and -not $_.Virtual
    } | Select-Object -ExpandProperty ifIndex)
    Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object {
        $_.InterfaceIndex -in $physical -and $_.AddressState -eq 'Preferred' -and
        $_.IPAddress -notmatch '^(0\.|127\.|169\.254\.)'
    } | Select-Object -ExpandProperty IPAddress -Unique | Sort-Object
}
