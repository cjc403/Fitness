param(
    [string]$HealthUrl = 'http://127.0.0.1:9091/actuator/health',
    [string]$DatabaseHost = 'localhost',
    [int]$DatabasePort = 5433,
    [string]$BackupDirectory = (Join-Path $PSScriptRoot '..\backups'),
    [int]$MaximumBackupAgeHours = 30,
    [switch]$Watch,
    [int]$IntervalSeconds = 30
)

. (Join-Path $PSScriptRoot 'postgres-tools.ps1')
$pgIsReady = Find-PostgresTool 'pg_isready'

do {
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    & $pgIsReady -h $DatabaseHost -p $DatabasePort -q
    $databaseHealthy = $LASTEXITCODE -eq 0
    try {
        $health = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 5
        $applicationHealthy = $health.status -eq 'UP'
    } catch { $applicationHealthy = $false }

    $latestBackup = if (Test-Path -LiteralPath $BackupDirectory) {
        Get-ChildItem -LiteralPath $BackupDirectory -Filter '*.dump' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    } else { $null }
    $backupHealthy = $latestBackup -and $latestBackup.LastWriteTime -gt (Get-Date).AddHours(-$MaximumBackupAgeHours)
    $status = if ($databaseHealthy -and $applicationHealthy) { 'UP' } else { 'DOWN' }
    Write-Host "[$timestamp] APP=$applicationHealthy DB=$databaseHealthy BACKUP=$([bool]$backupHealthy) STATUS=$status"

    if (-not $Watch) {
        if (-not ($databaseHealthy -and $applicationHealthy)) { exit 1 }
        exit 0
    }
    Start-Sleep -Seconds ([Math]::Max(5, $IntervalSeconds))
} while ($true)
