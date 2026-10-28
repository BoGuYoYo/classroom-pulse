param(
    [string]$Path = (Join-Path (Split-Path $PSScriptRoot -Parent) ".env.local"),
    [switch]$Force
)

$ErrorActionPreference = "Stop"
if ((Test-Path $Path) -and -not $Force) {
    throw "配置文件已存在：$Path。如需覆盖请加 -Force。"
}

function New-RandomSecret([int]$Bytes = 36) {
    $buffer = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    return [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$mysqlPassword = New-RandomSecret 24
$mysqlRootPassword = New-RandomSecret 24
$jwtSecret = New-RandomSecret 48
$teacherPassword = New-RandomSecret 18

$content = @"
# This file is generated locally and ignored by Git.
MYSQL_PASSWORD=$mysqlPassword
MYSQL_ROOT_PASSWORD=$mysqlRootPassword
JWT_SECRET=$jwtSecret
BOOTSTRAP_TEACHER_USERNAME=admin
BOOTSTRAP_TEACHER_PASSWORD=$teacherPassword
BOOTSTRAP_TEACHER_NAME=教师
CORS_ALLOWED_ORIGINS=
"@
Set-Content -Path $Path -Value $content -Encoding UTF8

Write-Host ""
Write-Host "Generated $Path" -ForegroundColor Green
Write-Host "Bootstrap teacher (local only):" -ForegroundColor Cyan
Write-Host "  username: admin"
Write-Host "  password: $teacherPassword"
Write-Host ""
Write-Host "Start the stack:"
Write-Host "  docker compose --env-file .env.local -f docker-compose.local.yml up -d --build"
