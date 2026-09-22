param(
    [string]$ServiceName = 'postgresql-x64-18'
)

$ErrorActionPreference = 'Stop'

$service = Get-Service -Name $ServiceName -ErrorAction Stop
if ($service.Status -eq 'Running') {
    Restart-Service -Name $ServiceName -Force -ErrorAction Stop
} else {
    Start-Service -Name $ServiceName -ErrorAction Stop
}

(Get-Service -Name $ServiceName).WaitForStatus(
    [System.ServiceProcess.ServiceControllerStatus]::Running,
    [TimeSpan]::FromSeconds(30)
)

