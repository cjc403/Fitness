param(
    [string]$DbUrl = 'jdbc:postgresql://127.0.0.1:5433/fitness_db?sslmode=disable&connectTimeout=5&socketTimeout=30',
    [string]$DbUser = 'fitness_user',
    [string]$DbPassword,
    [string]$JwtSecret,
    [string]$AdminPassword,
    [switch]$SeedDemoData,
    [switch]$SeedSampleData,
    [switch]$SkipBuild
)

. (Join-Path $PSScriptRoot 'postgres-tools.ps1')
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendRoot = Join-Path $projectRoot 'fitness-backend'
if ($SeedDemoData -and $SeedSampleData) { throw 'SeedDemoData and SeedSampleData cannot be used together.' }
$DbPassword = Resolve-Secret $DbPassword 'DB_PASSWORD' 'Database password for fitness_user'
$JwtSecret = Resolve-Secret $JwtSecret 'JWT_SECRET' 'JWT secret (at least 32 characters)'
if ($JwtSecret.Length -lt 32) { throw 'The JWT secret must be at least 32 characters.' }
$AdminPassword = if ($SeedDemoData) { '' } else { Resolve-Secret $AdminPassword 'APP_ADMIN_PASSWORD' 'Initial admin password (at least 8 characters)' }
if (-not $SeedDemoData -and $AdminPassword.Length -lt 8) { throw 'The admin password must be at least 8 characters.' }

$javaCandidate = Join-Path $projectRoot '.tools\jdk-17\bin\java.exe'
$java = if (Test-Path -LiteralPath $javaCandidate) { $javaCandidate } else { (Get-Command java -ErrorAction Stop).Source }
$mavenCandidate = Join-Path $projectRoot '.tools\maven\bin\mvn.cmd'
$maven = if (Test-Path -LiteralPath $mavenCandidate) { $mavenCandidate } else { (Get-Command mvn -ErrorAction Stop).Source }
$jar = Join-Path $backendRoot 'target\fitness-backend-1.0.0.jar'

if (-not $SkipBuild -or -not (Test-Path -LiteralPath $jar)) {
    Write-Host 'Building the production backend...' -ForegroundColor Cyan
    $previousJavaHome = $env:JAVA_HOME
    $bundledJavaHome = Join-Path $projectRoot '.tools\jdk-17'
    if (Test-Path -LiteralPath $bundledJavaHome -PathType Container) { $env:JAVA_HOME = $bundledJavaHome }
    Push-Location $backendRoot
    try { & $maven '-DskipTests' 'clean' 'package'; if ($LASTEXITCODE -ne 0) { throw 'The production build failed.' } }
    finally {
        Pop-Location
        if ($null -eq $previousJavaHome) { Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue } else { $env:JAVA_HOME = $previousJavaHome }
    }
}

$env:DB_URL = $DbUrl
$env:DB_USERNAME = $DbUser
$env:DB_PASSWORD = $DbPassword
$env:JWT_SECRET = $JwtSecret
$env:APP_ADMIN_PASSWORD = $AdminPassword
$env:SPRING_PROFILES_ACTIVE = if ($SeedDemoData) { 'prod,demo' } elseif ($SeedSampleData) { 'prod,sample' } else { 'prod' }
$deepSeekApiKey = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY')
if (-not $deepSeekApiKey) {
    $deepSeekApiKey = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'User')
}
if ($deepSeekApiKey) { $env:DEEPSEEK_API_KEY = $deepSeekApiKey }
try {
    Write-Host "Starting FitChain with profile $($env:SPRING_PROFILES_ACTIVE)..." -ForegroundColor Green
    & $java '-jar' $jar
    exit $LASTEXITCODE
} finally {
    'DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','APP_ADMIN_PASSWORD','SPRING_PROFILES_ACTIVE','DEEPSEEK_API_KEY' | ForEach-Object {
        Remove-Item "Env:$_" -ErrorAction SilentlyContinue
    }
}
