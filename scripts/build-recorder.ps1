param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$GradleExecutable = '',
    [string]$ReadOnlyCache = '',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) { throw 'JDK 17のパスを -JavaHome で指定してください。' }
if (-not $AndroidSdk) { throw 'Android SDKのパスを -AndroidSdk で指定してください。' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_USER_HOME = Join-Path $workspace '.tools/android'
$env:GRADLE_USER_HOME = Join-Path $workspace '.tools/gradle-home'
if ($ReadOnlyCache) { $env:GRADLE_RO_DEP_CACHE = $ReadOnlyCache }
if (-not $GradleExecutable) { $GradleExecutable = Join-Path $workspace 'gradlew.bat' }
Push-Location $workspace
try {
    $gradleArgs = @(':recorder:assembleDebug', ':recorder:lintDebug', '--no-daemon', '--console=plain')
    if ($Offline) { $gradleArgs += '--offline' }
    & $GradleExecutable @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "ビルドが失敗しました: $LASTEXITCODE" }
    New-Item -ItemType Directory -Force -Path (Join-Path $workspace 'dist') | Out-Null
    Copy-Item -LiteralPath (Join-Path $workspace 'recorder/build/outputs/apk/debug/recorder-debug.apk') -Destination (Join-Path $workspace 'dist/arte-input-recorder-debug.apk')
    Write-Output (Join-Path $workspace 'dist/arte-input-recorder-debug.apk')
} finally { Pop-Location }
