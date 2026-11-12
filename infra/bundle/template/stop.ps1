# 발자국 실행 묶음을 끕니다. Windows PowerShell용입니다. macOS·Linux는 stop.sh를 씁니다.
# 데이터(올린 파일, 기록, 평가)는 지우지 않습니다. 다시 켜면 그대로 있습니다. 데이터를 지우는 것은 reset 스크립트입니다.

$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"

# 1. Docker가 켜져 있는지 확인합니다.
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker가 켜져 있지 않습니다. 꺼져 있으면 묶음도 이미 꺼진 상태입니다." -ForegroundColor Red
    exit 1
}

# 2. 컨테이너를 멈춥니다. stop은 컨테이너를 지우지 않아서 로그(docker logs)도 남습니다.
docker compose -f $composeFile stop
if ($LASTEXITCODE -ne 0) {
    Write-Host "끄지 못했습니다. docker ps로 footprint-bundle- 로 시작하는 컨테이너를 확인하세요." -ForegroundColor Red
    exit 1
}
Write-Host "껐습니다. 데이터는 남아 있습니다. 다시 켜려면 start.bat" -ForegroundColor Green
