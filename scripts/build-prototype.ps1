param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$GradleExecutable = '',
    [string]$ReadOnlyCache = '',
    [string]$NativeSdk = '',
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
    $gradleArgs = @(':input-core:test', ':sumire-engine:test', ':prototype:testDebugUnitTest', ':prototype:assembleDebug', ':prototype:lintDebug', ':zenz:lintDebug', '--no-daemon', '--console=plain')
    & (Join-Path $PSScriptRoot 'prepare-zenz.ps1') -Offline:$Offline
    if(-not $NativeSdk){
        $NativeSdk=$AndroidSdk
        $siblingSdk=Join-Path $workspace '../timetable-app/.tools/android-sdk'
        if(-not (Test-Path -LiteralPath (Join-Path $NativeSdk 'ndk/28.2.13676358')) -and (Test-Path -LiteralPath (Join-Path $siblingSdk 'ndk/28.2.13676358'))){$NativeSdk=(Resolve-Path -LiteralPath $siblingSdk).Path}
    }
    if(Test-Path -LiteralPath (Join-Path $NativeSdk 'ndk/28.2.13676358')){$gradleArgs += '-PzenzNdkPath='+(Join-Path $NativeSdk 'ndk/28.2.13676358')}
    if(Test-Path -LiteralPath (Join-Path $NativeSdk 'cmake/3.22.1/bin')){$env:PATH=(Join-Path $NativeSdk 'cmake/3.22.1/bin')+';'+$env:PATH}
    if ($Offline) { $gradleArgs += '--offline' }
    & $GradleExecutable @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "ビルドが失敗しました: $LASTEXITCODE" }
    New-Item -ItemType Directory -Force -Path (Join-Path $workspace 'dist') | Out-Null
    Copy-Item -LiteralPath (Join-Path $workspace 'prototype/build/outputs/apk/debug/prototype-debug.apk') -Destination (Join-Path $workspace 'dist/romaji-glide-prototype-debug.apk')
    Write-Output (Join-Path $workspace 'dist/romaji-glide-prototype-debug.apk')
} finally { Pop-Location }
