#!/usr/bin/env bash
# PostgreSQL과 MinIO를 한 묶음으로 백업합니다. macOS·Linux(그리고 Windows의 Git Bash)용입니다. Windows PowerShell은 backup.ps1을 씁니다.
# 사용법(저장소 맨 위에서): ./infra/backup/backup.sh
#   다른 컨테이너를 백업하려면: ./infra/backup/backup.sh --postgres-container 이름 --minio-container 이름
# 결과: infra/backup/bundles/<날짜-시각>/ 폴더에 db.dump, files/, manifest.txt
# 비밀번호는 이 스크립트가 다루지 않습니다. 명령을 컨테이너 안에서 실행해 컨테이너가 가진 접속값을 그대로 씁니다.
set -euo pipefail
# Git Bash가 /tmp 같은 컨테이너 안 경로를 Windows 경로로 바꾸지 않게 합니다. macOS·Linux에서는 아무 일도 하지 않습니다.
export MSYS_NO_PATHCONV=1

script_dir="$(cd "$(dirname "$0")" && pwd)"
# 그 대신 Git Bash에서는 이 컴퓨터 쪽 경로를 docker가 알아듣는 Windows 모양(E:/...)으로 바꿉니다. cygpath는 Git Bash에만 있습니다.
if command -v cygpath > /dev/null; then script_dir="$(cygpath -m "$script_dir")"; fi
postgres_container="footprint-demo-postgres"
minio_container="footprint-demo-minio"

# 명령줄에서 컨테이너 이름을 바꿀 수 있습니다.
while [ $# -gt 0 ]; do
  case "$1" in
    --postgres-container) postgres_container="$2"; shift 2 ;;
    --minio-container) minio_container="$2"; shift 2 ;;
    *) echo "모르는 옵션입니다: $1" >&2; exit 1 ;;
  esac
done

# 버킷 이름은 infra/.env의 STORAGE_BUCKET, 없으면 백엔드의 기본값 footprint입니다.
bucket="footprint"
env_file="$script_dir/../.env"
if [ -f "$env_file" ] && grep -q '^STORAGE_BUCKET=' "$env_file"; then
  bucket="$(grep '^STORAGE_BUCKET=' "$env_file" | tail -n 1 | cut -d= -f2- | tr -d '\r')"
fi

# 1. 두 컨테이너가 켜져 있는지 확인합니다.
for container in "$postgres_container" "$minio_container"; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" != "true" ]; then
    echo "컨테이너 $container 가 켜져 있지 않습니다. infra 폴더에서 docker compose up -d 를 먼저 실행하세요." >&2
    exit 1
  fi
done

# 2. 데이터베이스 이름과 사용자 이름은 컨테이너의 환경 변수에서 읽습니다(비밀번호는 읽지 않습니다).
db_user="$(docker exec "$postgres_container" printenv POSTGRES_USER)"
db_name="$(docker exec "$postgres_container" printenv POSTGRES_DB)"

bundle_dir="$script_dir/bundles/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$bundle_dir"
echo "백업 폴더: $bundle_dir"
echo "서버(백엔드)를 멈춘 뒤에 백업하는 것을 권장합니다. 켜 둔 채면 백업 도중의 업로드가 묶음에 빠질 수 있습니다."

# 3. 데이터베이스를 먼저 백업합니다. -n public: 서비스 데이터가 있는 public 스키마만 담고, 빌드할 때 테스트가 쓰는 스키마(footprint_test 등)는 뺍니다.
#    -Fc: pg_restore로 되살리는 압축 형식입니다. 파일은 컨테이너 안에 만들고 docker cp로 꺼냅니다.
docker exec "$postgres_container" pg_dump -U "$db_user" -d "$db_name" -n public -Fc -f /tmp/footprint-backup.dump
docker cp -q "$postgres_container:/tmp/footprint-backup.dump" "$bundle_dir/db.dump"
docker exec "$postgres_container" rm -f /tmp/footprint-backup.dump

# 4. 확인용 사실을 데이터베이스에서 읽습니다. Flyway가 마지막으로 적용한 migration 번호와 테이블마다의 행 수입니다.
flyway_version="$(docker exec "$postgres_container" psql -U "$db_user" -d "$db_name" -tA \
  -c "select version from flyway_schema_history where success order by installed_rank desc limit 1")"
docker cp -q "$script_dir/count-rows.sql" "$postgres_container:/tmp/footprint-count-rows.sql"
row_counts="$(docker exec "$postgres_container" psql -U "$db_user" -d "$db_name" -tA -f /tmp/footprint-count-rows.sql)"
docker exec "$postgres_container" rm -f /tmp/footprint-count-rows.sql
pg_dump_version="$(docker exec "$postgres_container" pg_dump --version)"

# 5. 그다음 버킷 파일을 백업합니다. mc(MinIO 클라이언트)는 MinIO 컨테이너 안에 들어 있습니다.
#    작은따옴표 안의 $MINIO_ROOT_USER 등은 컨테이너 안에서 풀립니다. 그래서 비밀번호가 이 컴퓨터의 화면이나 명령에 나오지 않습니다.
docker exec "$minio_container" sh -c 'mc alias set footprint-backup http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD > /dev/null'
docker exec "$minio_container" rm -rf /tmp/footprint-backup-files
docker exec "$minio_container" mkdir -p /tmp/footprint-backup-files
docker exec "$minio_container" mc mirror --quiet "footprint-backup/$bucket" /tmp/footprint-backup-files > /dev/null
# 대상 폴더(files)가 아직 없어서, docker cp가 files 폴더를 만들고 그 안에 파일을 넣습니다.
docker cp -q "$minio_container:/tmp/footprint-backup-files" "$bundle_dir/files"
docker exec "$minio_container" rm -rf /tmp/footprint-backup-files
# 컨테이너 안에 남은 접속 이름(alias)을 지웁니다.
docker exec "$minio_container" mc alias remove footprint-backup > /dev/null
file_count="$(find "$bundle_dir/files" -type f | wc -l | tr -d ' ')"

# 6. 묶음 설명 파일을 씁니다. 비밀값은 넣지 않습니다. 복구 스크립트가 이 파일의 rows., file_count 줄로 결과를 비교합니다.
{
  echo "# 발자국 백업 묶음. 개인정보(이름, 이메일, 평가 점수·의견, 올린 파일)가 들어 있음. 저장소·공유 폴더에 올리지 않음"
  echo "created_at=$(date '+%Y-%m-%d %H:%M:%S')"
  echo "database=$db_name"
  echo "bucket=$bucket"
  echo "flyway_version=$flyway_version"
  echo "pg_dump_version=$pg_dump_version"
  echo "file_count=$file_count"
  echo "$row_counts"
} > "$bundle_dir/manifest.txt"

echo "백업을 마쳤습니다. 파일 $file_count 개, Flyway 버전 $flyway_version"
echo "manifest: $bundle_dir/manifest.txt"
