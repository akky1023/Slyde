param([string]$ExpectedVersion = '')
$ErrorActionPreference = 'Stop'
if ($ExpectedVersion -and $ExpectedVersion -notmatch '^\d+\.\d+\.\d+$') { throw '正式版のタグはvMAJOR.MINOR.PATCHで指定してください。' }
if (-not $env:GLIDE_KEYSTORE_BASE64 -or -not $env:GLIDE_STORE_PASSWORD) {
    throw 'GitHub SecretsにGLIDE_KEYSTORE_BASE64とGLIDE_STORE_PASSWORDを登録してください。'
}
$workspace = Split-Path -Parent $PSScriptRoot
$version = (Get-Content -Raw -LiteralPath (Join-Path $workspace 'version.txt')).Trim()
if ($ExpectedVersion -and $ExpectedVersion -ne $version) { throw 'タグとソースのバージョンが一致しません。' }
$keyPath = Join-Path $env:RUNNER_TEMP ('slyde-signing-' + [guid]::NewGuid() + '.p12')
try {
    [IO.File]::WriteAllBytes($keyPath, [Convert]::FromBase64String($env:GLIDE_KEYSTORE_BASE64))
    $env:GLIDE_KEYSTORE_BASE64 = $null
    $env:GLIDE_KEYSTORE = $keyPath
    & (Join-Path $PSScriptRoot 'build-release.ps1') -JavaHome $env:JAVA_HOME_17_X64 -AndroidSdk $env:ANDROID_HOME -ExpectedVersion $version -ExpectedSigningFingerprint 'e9970e3491494b00ad296fefcf8838f6e333c0fba9d20ccbbec2e7b61ff1d612'
} finally {
    $env:GLIDE_KEYSTORE = $null
    $env:GLIDE_STORE_PASSWORD = $null
    $env:GLIDE_KEYSTORE_BASE64 = $null
    if (Test-Path -LiteralPath $keyPath) { Remove-Item -LiteralPath $keyPath -Force }
}
