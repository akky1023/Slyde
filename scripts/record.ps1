param(
    [string]$Python = 'python',
    [string]$Adb = 'adb',
    [string]$Serial = '',
    [switch]$Install,
    [switch]$CollectOnly
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
if ($Python -eq 'python' -and -not (Get-Command python -ErrorAction SilentlyContinue)) {
    $bundledPython = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
    if (Test-Path -LiteralPath $bundledPython) { $Python = $bundledPython }
    else { throw 'Python 3.10以降を用意し、-Python で実行ファイルを指定してください。' }
}
$captureArgs = @((Join-Path $workspace 'tools/capture.py'), '--adb', $Adb, '--output', (Join-Path $workspace 'captures'))
if ($Serial) { $captureArgs += @('--serial', $Serial) }
if ($Install) { $captureArgs += @('--apk', (Join-Path $workspace 'dist/arte-input-recorder-debug.apk')) }
if ($CollectOnly) { $captureArgs += '--collect-only' }
$captureDirectory = Join-Path $workspace 'captures'
New-Item -ItemType Directory -Force -Path $captureDirectory | Out-Null
$launchLog = Join-Path $captureDirectory ('launcher-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.log')
Write-Host "起動ログ: $launchLog"
$previousPreference = $ErrorActionPreference
$previousPythonEncoding = $env:PYTHONIOENCODING
$previousConsoleEncoding = [Console]::OutputEncoding
try {
    $env:PYTHONIOENCODING = 'utf-8'
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
    # Windows PowerShell 5.1 wraps native stderr in ErrorRecord; keep reading it.
    $ErrorActionPreference = 'Continue'
    & $Python -u @captureArgs 2>&1 | ForEach-Object {
        $line = $_.ToString()
        $line | Out-File -LiteralPath $launchLog -Encoding utf8 -Append
        Write-Host $line
    }
    $toolExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousPreference
    $env:PYTHONIOENCODING = $previousPythonEncoding
    [Console]::OutputEncoding = $previousConsoleEncoding
}
if ($toolExit -ne 0) {
    Write-Host "記録を開始・完了できませんでした (終了コード $toolExit)。上のエラーまたは $launchLog を確認してください。" -ForegroundColor Red
    exit $toolExit
}
