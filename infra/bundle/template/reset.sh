#!/usr/bin/env bash
# 발자국 실행 묶음의 데이터를 모두 지웁니다. macOS·Linux용입니다. Windows는 reset.bat(reset.ps1)을 씁니다.
# 지우는 것: 묶음의 컨테이너 4개와 volume 2개(데이터베이스, 올린 파일). 되돌릴 수 없습니다.
# 남기는 것: 이 폴더의 .env(설정과 비밀값), backup 폴더의 백업 묶음, 이미지. 다음 start는 빈 데이터로 켜집니다(demo면 시작 데이터가 다시 들어갑니다).
# 개발 환경(footprint-demo-*)의 컨테이너와 volume은 이름이 달라서 건드리지 않습니다.
set -euo pipefail

bundle_dir="$(cd "$(dirname "$0")" && pwd)"
compose_file="$bundle_dir/docker-compose.yml"

# 1. Docker가 켜져 있는지 확인합니다.
if ! docker info > /dev/null 2>&1; then
  echo "Docker가 켜져 있지 않습니다. Docker Desktop을 켠 뒤 다시 실행하세요." >&2
  exit 1
fi

# 2. 무엇을 지우는지 보여 주고 한 번 더 확인을 받습니다. delete를 정확히 입력해야 진행합니다.
echo "묶음의 데이터를 모두 지웁니다. 되돌릴 수 없습니다."
echo "  지움: 컨테이너 footprint-bundle-*, volume footprint-bundle_postgres-data, footprint-bundle_minio-data"
echo "  남김: .env, backup 폴더, 이미지"
echo "  지우기 전에 남길 데이터가 있으면 먼저 백업하세요(README.md '백업')."
read -r -p "지우려면 delete 를 입력하세요: " answer
if [ "$answer" != "delete" ]; then
  echo "지우지 않았습니다."
  exit 0
fi

# 3. 컨테이너와 이 묶음의 volume을 지웁니다. -v: compose 파일에 적힌 volume(postgres-data, minio-data)도 지웁니다.
#    .env가 없으면 compose 파일의 비밀값 자리(${...:?})를 채우지 못해 멈추므로, 그때만 자리를 채우는 값(unused)을 넘깁니다.
#    지우는 데에는 비밀값이 필요 없습니다. 이 값은 이 스크립트가 끝나면 사라집니다.
if [ ! -f "$bundle_dir/.env" ]; then
  export JWT_SECRET=unused POSTGRES_PASSWORD=unused MINIO_ROOT_PASSWORD=unused
fi
if ! docker compose -f "$compose_file" down -v; then
  echo "지우지 못했습니다. docker ps -a 와 docker volume ls 로 footprint-bundle 이름을 확인하세요." >&2
  exit 1
fi
echo "지웠습니다. 다음 start는 빈 데이터로 켜집니다."
