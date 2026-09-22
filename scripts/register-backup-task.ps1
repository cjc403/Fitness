param(
    [string]$TaskName = 'FitChain PostgreSQL Backup',
    [string]$DailyAt = '02:00'
)

if (-not [Environment]::GetEnvironmentVariable('DB_PASSWORD', 'User')) {
    throw 'Set DB_PASSWORD as a user environment variable before registering unattended backups.'
}
$backupScript = (Resolve-Path (Join-Path $PSScriptRoot 'backup-postgres.ps1')).Path
$powerShell = (Get-Command powershell.exe -ErrorAction Stop).Source
$action = New-ScheduledTaskAction -Execute $powerShell -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$backupScript`""
$trigger = New-ScheduledTaskTrigger -Daily -At $DailyAt
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 2)
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Description 'FitChain PostgreSQL daily compressed backup; retains 14 days by default.' -Force | Out-Null
Write-Host "Registered daily backup task: $TaskName ($DailyAt)" -ForegroundColor Green
