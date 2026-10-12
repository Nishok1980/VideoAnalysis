$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
foreach ($command in @("java", "ffmpeg", "ffprobe")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command is missing from PATH. Follow README setup steps."
    }
}
$jar = "videoanalysis-app/target/videoanalysis-app-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) { throw "Build first: mvn clean verify" }
if (-not (Test-Path "models/yolov10n.onnx") -and -not $env:VIDEO_MODEL) {
    throw "Install the model first: .\scripts\setup-model.ps1"
}
Write-Host "Open http://localhost:8080 after the Started message. Ctrl+C stops."
& java --enable-native-access=ALL-UNNAMED -jar $jar
exit $LASTEXITCODE
