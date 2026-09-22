param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$HostName = 'localhost',
    [int]$Port = 5433,
    [string]$Database = 'fitness_db',
    [string]$Username = 'fitness_user',
    [string]$Password,
    [switch]$Clean,
    [switch]$Force
)

. (Join-Path $PSScriptRoot 'postgres-tools.ps1')
Assert-SqlIdentifier $Database 'database name'
Assert-SqlIdentifier $Username 'database username'
$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile -ErrorAction Stop).Path
if (-not $resolvedBackup.EndsWith('.dump', [StringComparison]::OrdinalIgnoreCase)) { throw 'Select a backup file in .dump format.' }
if ($Clean -and -not $Force) {
    $answer = Read-Host "Existing objects in $Database will be removed before restore. Type RESTORE to continue"
    if ($answer -ne 'RESTORE') { throw 'Restore cancelled.' }
}
$pgRestore = Find-PostgresTool 'pg_restore'
$Password = Resolve-Secret $Password 'DB_PASSWORD' 'Database password'
$arguments = @('-h', $HostName, '-p', "$Port", '-U', $Username, '-d', $Database, '-w', '--exit-on-error', '--no-owner')
if ($Clean) { $arguments += @('--clean', '--if-exists') }
$arguments += $resolvedBackup
$env:PGPASSWORD = $Password
try {
    Invoke-PostgresTool $pgRestore $arguments
    Write-Host "Restore complete: $resolvedBackup -> $Database" -ForegroundColor Green
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
