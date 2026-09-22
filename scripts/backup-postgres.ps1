param(
    [string]$HostName = 'localhost',
    [int]$Port = 5433,
    [string]$Database = 'fitness_db',
    [string]$Username = 'fitness_user',
    [string]$Password,
    [string]$BackupDirectory = (Join-Path $PSScriptRoot '..\backups'),
    [int]$RetentionDays = 14
)

. (Join-Path $PSScriptRoot 'postgres-tools.ps1')
Assert-SqlIdentifier $Database 'database name'
Assert-SqlIdentifier $Username 'database username'
$pgDump = Find-PostgresTool 'pg_dump'
$Password = Resolve-Secret $Password 'DB_PASSWORD' 'Database password'
$resolvedBackupDirectory = [IO.Path]::GetFullPath($BackupDirectory)
New-Item -ItemType Directory -Path $resolvedBackupDirectory -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupFile = Join-Path $resolvedBackupDirectory "$Database-$stamp.dump"
$env:PGPASSWORD = $Password
try {
    Invoke-PostgresTool $pgDump @('-h', $HostName, '-p', "$Port", '-U', $Username, '-d', $Database, '-w', '--format=custom', '--compress=9', '--no-owner', '--file', $backupFile)
    $hash = Get-FileHash -LiteralPath $backupFile -Algorithm SHA256
    Set-Content -LiteralPath "$backupFile.sha256" -Value "$($hash.Hash)  $([IO.Path]::GetFileName($backupFile))" -Encoding ascii

    $cutoff = (Get-Date).AddDays(-[Math]::Abs($RetentionDays))
    Get-ChildItem -LiteralPath $resolvedBackupDirectory -File | Where-Object {
        $_.LastWriteTime -lt $cutoff -and ($_.Extension -eq '.dump' -or $_.Name.EndsWith('.dump.sha256'))
    } | ForEach-Object {
        if (-not $_.FullName.StartsWith($resolvedBackupDirectory, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove a file outside the backup directory: $($_.FullName)"
        }
        Remove-Item -LiteralPath $_.FullName -Force
    }
    Write-Host "Backup complete: $backupFile" -ForegroundColor Green
    Write-Host "SHA256: $($hash.Hash)"
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
