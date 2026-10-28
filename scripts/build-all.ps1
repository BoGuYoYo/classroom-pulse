$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $root "backend"
$android = Join-Path $root "android"
$artifacts = Join-Path $root "artifacts"
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $java) {
        throw "未找到 JDK。请安装 JDK 17+ 并设置 JAVA_HOME。"
    }
    $env:JAVA_HOME = Split-Path (Split-Path $java.Source -Parent) -Parent
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "==> 后端测试" -ForegroundColor Cyan
Push-Location $backend
try {
    & .\gradlew.bat test bootJar --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "后端构建失败" }
    $jar = Get-ChildItem "build\libs\*.jar" | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1
    if ($null -eq $jar) { throw "未找到后端 bootJar" }
    Copy-Item $jar.FullName (Join-Path $artifacts "classroom-pulse-server-1.0.0.jar") -Force
} finally {
    Pop-Location
}

Write-Host "==> Android APK" -ForegroundColor Cyan
Push-Location $android
try {
    & .\gradlew.bat assembleDebug --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Android 构建失败" }
    Copy-Item "app\build\outputs\apk\debug\app-debug.apk" (Join-Path $artifacts "classroom-pulse-v1.0.0-debug.apk") -Force
} finally {
    Pop-Location
}

$files = @(
    (Join-Path $artifacts "classroom-pulse-server-1.0.0.jar"),
    (Join-Path $artifacts "classroom-pulse-v1.0.0-debug.apk")
)
$lines = foreach ($file in $files) {
    $hash = Get-FileHash -Algorithm SHA256 -Path $file
    "$($hash.Hash.ToLower())  $([IO.Path]::GetFileName($file))"
}
Set-Content -Path (Join-Path $artifacts "SHA256SUMS.txt") -Value $lines -Encoding ASCII

Get-ChildItem $files | Select-Object Name, Length, LastWriteTime
Write-Host "构建产物已写入 $artifacts" -ForegroundColor Green
