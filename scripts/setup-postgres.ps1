param(
    [string]$HostName = 'localhost',
    [int]$Port = 5433,
    [string]$AdminUser = 'postgres',
    [string]$AdminPassword,
    [string]$AppDatabase = 'fitness_db',
    [string]$AppUser = 'fitness_user',
    [string]$AppPassword
)

. (Join-Path $PSScriptRoot 'postgres-tools.ps1')
Assert-SqlIdentifier $AdminUser 'admin username'
Assert-SqlIdentifier $AppDatabase 'database name'
Assert-SqlIdentifier $AppUser 'application username'
$psql = Find-PostgresTool 'psql'
$createdb = Find-PostgresTool 'createdb'
$AdminPassword = Resolve-Secret $AdminPassword 'POSTGRES_ADMIN_PASSWORD' 'PostgreSQL postgres administrator password'
$AppPassword = Resolve-Secret $AppPassword 'DB_PASSWORD' 'New database password for fitness_user'
if ($AppPassword.Length -lt 12) { throw 'The database password must be at least 12 characters.' }
$escapedPassword = $AppPassword.Replace("'", "''")
$env:PGPASSWORD = $AdminPassword

try {
    Write-Host "[1/3] Checking PostgreSQL connection..." -ForegroundColor Cyan
    Invoke-PostgresTool $psql @('-h', $HostName, '-p', "$Port", '-U', $AdminUser, '-d', 'postgres', '-w', '-v', 'ON_ERROR_STOP=1', '-c', 'SELECT version();')

    $roleExists = (& $psql -h $HostName -p $Port -U $AdminUser -d postgres -w -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$AppUser'" | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Failed to query the application role.' }
    $roleExists = $roleExists.Trim()
    if ($roleExists -eq '1') {
        Invoke-PostgresTool $psql @('-h', $HostName, '-p', "$Port", '-U', $AdminUser, '-d', 'postgres', '-w', '-v', 'ON_ERROR_STOP=1', '-c', "ALTER ROLE `"$AppUser`" WITH LOGIN PASSWORD '$escapedPassword';")
        Write-Host "[2/3] Updated application role $AppUser." -ForegroundColor Green
    } else {
        Invoke-PostgresTool $psql @('-h', $HostName, '-p', "$Port", '-U', $AdminUser, '-d', 'postgres', '-w', '-v', 'ON_ERROR_STOP=1', '-c', "CREATE ROLE `"$AppUser`" LOGIN PASSWORD '$escapedPassword';")
        Write-Host "[2/3] Created application role $AppUser." -ForegroundColor Green
    }

    $databaseExists = (& $psql -h $HostName -p $Port -U $AdminUser -d postgres -w -tAc "SELECT 1 FROM pg_database WHERE datname = '$AppDatabase'" | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Failed to query the application database.' }
    $databaseExists = $databaseExists.Trim()
    if ($databaseExists -ne '1') {
        Invoke-PostgresTool $createdb @('-h', $HostName, '-p', "$Port", '-U', $AdminUser, '-O', $AppUser, $AppDatabase)
        Write-Host "[3/3] Created database $AppDatabase." -ForegroundColor Green
    } else {
        Invoke-PostgresTool $psql @('-h', $HostName, '-p', "$Port", '-U', $AdminUser, '-d', 'postgres', '-w', '-v', 'ON_ERROR_STOP=1', '-c', "ALTER DATABASE `"$AppDatabase`" OWNER TO `"$AppUser`";")
        Write-Host "[3/3] Database $AppDatabase already exists; owner corrected." -ForegroundColor Green
    }
    Invoke-PostgresTool $psql @('-h', $HostName, '-p', "$Port", '-U', $AdminUser, '-d', $AppDatabase, '-w', '-v', 'ON_ERROR_STOP=1', '-c', "GRANT ALL ON SCHEMA public TO `"$AppUser`";")
    Write-Host "PostgreSQL setup is complete. Next: .\start-production.ps1 -SeedDemoData" -ForegroundColor Green
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
