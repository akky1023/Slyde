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
if (-not $JavaHome -or -not $AndroidSdk) { throw 'JDK 17とAndroid SDKのパスを指定してください。' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_USER_HOME = Join-Path $workspace '.tools/android'
$env:GRADLE_USER_HOME = Join-Path $workspace '.tools/gradle-home'
if ($ReadOnlyCache) { $env:GRADLE_RO_DEP_CACHE = $ReadOnlyCache }
if (-not $GradleExecutable) { $GradleExecutable = Join-Path $workspace 'gradlew.bat' }
$signingDir = Join-Path $workspace '.tools/signing'
$keyFile = Join-Path $signingDir 'glide-release.p12'
$passwordFile = Join-Path $signingDir 'password.clixml'
$previousKey = $env:GLIDE_KEYSTORE
$previousPassword = $env:GLIDE_STORE_PASSWORD
Push-Location $workspace
try {
    New-Item -ItemType Directory -Force -Path $signingDir | Out-Null
    # Limit local signing material to this Windows account and SYSTEM.
    $account = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $signingDir /inheritance:r /grant:r "${account}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '署名フォルダのアクセス制限に失敗しました。' }
    if ((Test-Path -LiteralPath $keyFile) -xor (Test-Path -LiteralPath $passwordFile)) { throw '署名鍵とパスワードが揃っていません。既存の署名情報を復旧してください。' }
    if (-not (Test-Path -LiteralPath $passwordFile)) {
        $randomBytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
        $password = ConvertTo-SecureString ([Convert]::ToBase64String($randomBytes)) -AsPlainText -Force
        # Export-Clixml uses Windows DPAPI for SecureString, not plaintext.
        $password | Export-Clixml -LiteralPath $passwordFile
    }
    $secret = Import-Clixml -LiteralPath $passwordFile
    $env:GLIDE_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
    $env:GLIDE_KEYSTORE = $keyFile
    if (-not (Test-Path -LiteralPath $keyFile)) {
        & (Join-Path $JavaHome 'bin/keytool.exe') -genkeypair -keystore $keyFile -storetype PKCS12 -alias glide -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=akky_1023, O=Neomenia, C=JP' -ext 'SAN=DNS:neomenia.net' -storepass:env GLIDE_STORE_PASSWORD -keypass:env GLIDE_STORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw '正式版の署名鍵を作成できませんでした。' }
    }
    & (Join-Path $PSScriptRoot 'prepare-zenz.ps1') -Offline:$Offline
    $gradleArgs = @(':input-core:test', ':sumire-engine:test', ':prototype:testDebugUnitTest', ':prototype:assembleRelease', ':prototype:lintRelease', ':zenz:lintRelease', '--no-daemon', '--console=plain')
    if (-not $NativeSdk) {
        $NativeSdk = $AndroidSdk
        $siblingSdk = Join-Path $workspace '../timetable-app/.tools/android-sdk'
        if (-not (Test-Path -LiteralPath (Join-Path $NativeSdk 'ndk/28.2.13676358')) -and (Test-Path -LiteralPath (Join-Path $siblingSdk 'ndk/28.2.13676358'))) { $NativeSdk = (Resolve-Path -LiteralPath $siblingSdk).Path }
    }
    if (Test-Path -LiteralPath (Join-Path $NativeSdk 'ndk/28.2.13676358')) { $gradleArgs += '-PzenzNdkPath=' + (Join-Path $NativeSdk 'ndk/28.2.13676358') }
    if (Test-Path -LiteralPath (Join-Path $NativeSdk 'cmake/3.22.1/bin')) { $env:PATH = (Join-Path $NativeSdk 'cmake/3.22.1/bin') + ';' + $env:PATH }
    if ($Offline) { $gradleArgs += '--offline' }
    & $GradleExecutable @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "ビルドが失敗しました: $LASTEXITCODE" }
    $metadata = Get-Content -Raw -LiteralPath (Join-Path $workspace 'prototype/build/outputs/apk/release/output-metadata.json') | ConvertFrom-Json
    $apkName = 'Glide-' + $metadata.elements[0].versionName + '.apk'
    $apk = Join-Path $workspace ('dist/' + $apkName)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $apk) | Out-Null
    Copy-Item -LiteralPath (Join-Path $workspace 'prototype/build/outputs/apk/release/prototype-release.apk') -Destination $apk
    $buildTools = Join-Path $AndroidSdk 'build-tools/35.0.0'
    $badging = & (Join-Path $buildTools 'aapt.exe') dump badging $apk
    if ($LASTEXITCODE -ne 0 -or -not ($badging -match "package: name='net.neomenia.japaneseinput.glide' ")) { throw 'APKのアプリIDを確認できませんでした。' }
    & (Join-Path $buildTools 'apksigner.bat') verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APKの署名検証に失敗しました。' }
    (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLower() + '  ' + $apkName | Set-Content -LiteralPath ($apk + '.sha256') -Encoding ascii
    Write-Output $apk
} finally {
    $env:GLIDE_KEYSTORE = $previousKey
    $env:GLIDE_STORE_PASSWORD = $previousPassword
    Pop-Location
}
