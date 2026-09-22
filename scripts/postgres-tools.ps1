Set-StrictMode -Version Latest

function Find-PostgresTool {
    param([Parameter(Mandatory = $true)][string]$Name)
    $candidates = @()
    if ($env:PG_BIN) { $candidates += (Join-Path $env:PG_BIN "$Name.exe") }
    $candidates += @(
        "E:\PostgreSQL\bin\$Name.exe",
        "C:\Program Files\PostgreSQL\18\bin\$Name.exe",
        "C:\Program Files\PostgreSQL\16\bin\$Name.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw "PostgreSQL tool '$Name' was not found. Install PostgreSQL 18 or set PG_BIN."
}

function Assert-SqlIdentifier {
    param([Parameter(Mandatory = $true)][string]$Value, [string]$Label = 'identifier')
    if ($Value -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}$') {
        throw "$Label must contain only letters, digits, and underscores, and cannot start with a digit."
    }
}

function ConvertFrom-SecureValue {
    param([Parameter(Mandatory = $true)][Security.SecureString]$SecureValue)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Resolve-Secret {
    param([string]$Value, [string]$EnvironmentName, [string]$Prompt)
    if ($Value) { return $Value }
    $environmentValue = [Environment]::GetEnvironmentVariable($EnvironmentName)
    if (-not $environmentValue) {
        $environmentValue = [Environment]::GetEnvironmentVariable($EnvironmentName, 'User')
    }
    if ($environmentValue) { return $environmentValue }
    return ConvertFrom-SecureValue (Read-Host $Prompt -AsSecureString)
}

function Invoke-PostgresTool {
    param(
        [Parameter(Mandatory = $true)][string]$Tool,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed with exit code $LASTEXITCODE." }
}
