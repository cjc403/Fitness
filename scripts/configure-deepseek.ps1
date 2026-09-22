param(
    [string]$Model = 'deepseek-v4-flash'
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'postgres-tools.ps1')

$secureKey = Read-Host 'Enter DeepSeek API key (input is hidden)' -AsSecureString
$apiKey = ConvertFrom-SecureValue $secureKey
try {
    if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey.Length -lt 16) {
        throw 'The API key is empty or too short.'
    }
    if ($Model -notmatch '^[A-Za-z0-9._-]{2,80}$') {
        throw 'The model name contains unsupported characters.'
    }
    [Environment]::SetEnvironmentVariable('DEEPSEEK_API_KEY', $apiKey, 'User')
    [Environment]::SetEnvironmentVariable('DEEPSEEK_MODEL', $Model, 'User')
    Write-Host 'DeepSeek settings saved to Windows user environment variables.' -ForegroundColor Green
    Write-Host 'Restart the backend to apply the new settings.'
}
finally {
    $apiKey = $null
}
