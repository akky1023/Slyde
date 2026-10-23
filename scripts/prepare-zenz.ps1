param([switch]$Offline)
$ErrorActionPreference='Stop'
$zenzRoot=Split-Path -Parent $PSScriptRoot
$llamaCommit='88b97a47dc7f5892e2d5a6856fbe9cfe237f9e5c'
$modelRevision='4f5423f0fad41a73b1242eb96fe5c12ae4fdca83'
$modelSha='00c64b3d318045a708d0cad5434faccab10f5481a49e6362864551fd0995fa58'
$sourceDir=Join-Path $zenzRoot '.tools/zenz-source'
$modelDir=Join-Path $zenzRoot 'prototype/src/main/assets/zenz'
New-Item -ItemType Directory -Force $sourceDir,$modelDir | Out-Null
if(-not (Test-Path -LiteralPath (Join-Path $sourceDir "llama.cpp-$llamaCommit/CMakeLists.txt"))){
    if($Offline){throw 'zenzのllama.cppソースがありません。オンラインでprepare-zenz.ps1を実行してください。'}
    $archive=Join-Path $zenzRoot '.tools/zenz-llama.tar.gz'
    Invoke-WebRequest -Uri "https://codeload.github.com/azooKey/llama.cpp/tar.gz/$llamaCommit" -OutFile $archive
    & tar -xzf $archive -C $sourceDir
    if($LASTEXITCODE -ne 0){throw 'llama.cppの展開に失敗しました。'}
}
$model=Join-Path $modelDir 'zenz-v3.2-xsmall-Q5_K_M.gguf'
if(-not (Test-Path -LiteralPath $model)){
    if($Offline){throw 'zenzモデルがありません。オンラインでprepare-zenz.ps1を実行してください。'}
    $temporary=$model+'.download'
    Invoke-WebRequest -Uri "https://huggingface.co/Miwa-Keita/zenz-v3.2-xsmall-gguf/resolve/$modelRevision/ggml-model-Q5_K_M.gguf" -OutFile $temporary
    if((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $modelSha){throw 'zenzモデルのSHA-256が一致しません。'}
    Move-Item -LiteralPath $temporary -Destination $model
}
if((Get-FileHash -LiteralPath $model -Algorithm SHA256).Hash.ToLowerInvariant() -ne $modelSha){throw 'zenzモデルのSHA-256が一致しません。'}
Write-Output "zenzモデル検証済み: $modelSha"
