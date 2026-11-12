# 발자국 실행 묶음의 데이터를 모두 지웁니다. Windows PowerShell용입니다. macOS·Linux는 reset.sh를 씁니다.
# 지우는 것: 묶음의 컨테이너 4개와 volume 2개(데이터베이스, 올린 파일). 되돌릴 수 없습니다.
# 남기는 것: 이 폴더의 .env(설정과 비밀값), backup 폴더의 백업 묶음, 이미지. 다음 start는 빈 데이터로 켜집니다(demo면 시작 데이터가 다시 들어갑니다).
# 개발 환경(footprint-demo-*)의 컨테이너와 volume은 이름이 달라서 건드리지 않습니다.

$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"

# 1. Docker가 켜져 있는지 확인합니다.
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker가 켜져 있지 않습니다. Docker Desktop을 켠 뒤 다시 실행하세요." -ForegroundColor Red
    exit 1
}

# 2. 무엇을 지우는지 보여 주고 한 번 더 확인을 받습니다. delete를 정확히 입력해야 진행합니다.
Write-Host "묶음의 데이터를 모두 지웁니다. 되돌릴 수 없습니다." -ForegroundColor Yellow
Write-Host "  지움: 컨테이너 footprint-bundle-*, volume footprint-bundle_postgres-data, footprint-bundle_minio-data"
Write-Host "  남김: .env, backup 폴더, 이미지"
Write-Host "  지우기 전에 남길 데이터가 있으면 먼저 백업하세요(README.md '백업')."
$answer = Read-Host "지우려면 delete 를 입력하세요"
if ($answer -ne "delete") {
    Write-Host "지우지 않았습니다."
    exit 0
}

# 3. 컨테이너와 이 묶음의 volume을 지웁니다. -v: compose 파일에 적힌 volume(postgres-data, minio-data)도 지웁니다.
#    .env가 없으면 compose 파일의 비밀값 자리(${...:?})를 채우지 못해 멈추므로, 그때만 자리를 채우는 값(unused)을 넘깁니다.
#    지우는 데에는 비밀값이 필요 없습니다. 이 값은 이 스크립트가 끝나면 사라집니다.
if (-not (Test-Path (Join-Path $PSScriptRoot ".env"))) {
    $env:JWT_SECRET = "unused"; $env:POSTGRES_PASSWORD = "unused"; $env:MINIO_ROOT_PASSWORD = "unused"
}
docker compose -f $composeFile down -v
if ($LASTEXITCODE -ne 0) {
    Write-Host "지우지 못했습니다. docker ps -a 와 docker volume ls 로 footprint-bundle 이름을 확인하세요." -ForegroundColor Red
    exit 1
}
Write-Host "지웠습니다. 다음 start는 빈 데이터로 켜집니다." -ForegroundColor Green
