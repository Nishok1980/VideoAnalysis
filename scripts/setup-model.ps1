$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$modelDirectory = Join-Path $projectRoot "models"
New-Item -ItemType Directory -Force -Path $modelDirectory | Out-Null
$target = Join-Path $modelDirectory "yolov10n.onnx"
$partial = "$target.partial"
$expected = "a77dd863933f184a19e84361c64b788228a7c7dacc2c78939239a96ad3efca3b"
$url = "https://huggingface.co/onnx-community/YOLOv10/resolve/main/yolov10n.onnx"
Write-Host "Model: YOLOv10 nano, AGPL-3.0. See models/README.md."
if ((Test-Path $target) -and ((Get-FileHash $target -Algorithm SHA256).Hash.ToLowerInvariant() -eq $expected)) {
    Write-Host "Verified model is already installed."
    exit 0
}
try {
    Invoke-WebRequest -Uri $url -OutFile $partial -UseBasicParsing
    $actual = (Get-FileHash $partial -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        throw "Model checksum mismatch. Download was rejected."
    }
    Move-Item -Force $partial $target
    Write-Host "Model downloaded and SHA-256 verified."
} finally {
    if (Test-Path $partial) { Remove-Item $partial }
}
