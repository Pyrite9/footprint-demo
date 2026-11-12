#!/usr/bin/env bash
# backup.sh(또는 backup.ps1)로 만든 묶음 하나를 빈 PostgreSQL·MinIO에 되살립니다. Windows PowerShell은 restore.ps1을 씁니다.
# 사용법(저장소 맨 위에서): ./infra/backup/restore.sh infra/backup/bundles/<날짜-시각>
#   다른 컨테이너에 되살리려면: ... --postgres-container 이름 --minio-container 이름
# 대상이 비어 있지 않으면(테이블이나 버킷이 이미 있으면) 아무것도 바꾸지 않고 멈춥니다. 덮어쓰는 옵션은 없습니다.
# 백엔드는 복구가 끝난 뒤에 켭니다. 먼저 켜면 빈 데이터베이스에 시작 데이터가 들어가서 대상이 비어 있지 않게 됩니다.
set -euo pipefail
# Git Bash가 /tmp 같은 컨테이너 안 경로를 Windows 경로로 바꾸지 않게 합니다. macOS·Linux에서는 아무 일도 하지 않습니다.
export MSYS_NO_PATHCONV=1

script_dir="$(cd "$(dirname "$0")" && pwd)"
# 그 대신 Git Bash에서는 이 컴퓨터 쪽 경로를 docker가 알아듣는 Windows 모양(E:/...)으로 바꿉니다. cygpath는 Git Bash에만 있습니다.
if command -v cygpath > /dev/null; then script_dir="$(cygpath -m "$script_dir")"; fi
postgres_container="footprint-demo-postgres"
minio_container="footprint-demo-minio"
bundle_dir=""

# 명령줄에서 묶음 폴더와 컨테이너 이름을 받습니다.
while [ $# -gt 0 ]; do
  case "$1" in
    --postgres-container) postgres_container="$2"; shift 2 ;;
    --minio-container) minio_container="$2"; shift 2 ;;
    *) bundle_dir="$1"; shift ;;
  esac
done

# 1. 묶음 폴더에 필요한 것이 다 있는지 확인합니다.
if [ -z "$bundle_dir" ] || [ ! -f "$bundle_dir/db.dump" ] || [ ! -f "$bundle_dir/manifest.txt" ] || [ ! -d "$bundle_dir/files" ]; then
  echo "묶음 폴더를 지정하세요. 그 안에 db.dump, files/, manifest.txt가 있어야 합니다." >&2
  exit 1
fi
if command -v cygpath > /dev/null; then bundle_dir="$(cygpath -m "$bundle_dir")"; fi
bucket="$(grep '^bucket=' "$bundle_dir/manifest.txt" | cut -d= -f2- | tr -d '\r')"

# 2. 두 컨테이너가 켜져 있는지 확인합니다.
for container in "$postgres_container" "$minio_container"; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" != "true" ]; then
    echo "컨테이너 $container 가 켜져 있지 않습니다." >&2
    exit 1
  fi
done
db_user="$(docker exec "$postgres_container" printenv POSTGRES_USER)"
db_name="$(docker exec "$postgres_container" printenv POSTGRES_DB)"

# 3. 대상이 비어 있는지 확인합니다. 둘 다 확인한 뒤에야 쓰기 시작해서, 한쪽만 되살아나는 일을 막습니다.
table_count="$(docker exec "$postgres_container" psql -U "$db_user" -d "$db_name" -tA \
  -c "select count(*) from information_schema.tables where table_schema = 'public'")"
if [ "$table_count" != "0" ]; then
  echo "멈춤: 대상 데이터베이스($postgres_container)의 public 스키마에 테이블이 $table_count 개 있습니다. 빈 환경(새 volume)에만 복구합니다." >&2
  exit 1
fi
docker exec "$minio_container" sh -c 'mc alias set footprint-restore http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD > /dev/null'
if docker exec "$minio_container" mc stat "footprint-restore/$bucket" > /dev/null 2>&1; then
  echo "멈춤: 대상 MinIO($minio_container)에 버킷 $bucket 이 이미 있습니다. 빈 환경(새 volume)에만 복구합니다." >&2
  exit 1
fi

# 4. 데이터베이스를 되살립니다. Flyway 이력 테이블(flyway_schema_history)도 함께 돌아와서 서버가 migration을 다시 돌리지 않습니다.
#    -n public: public 스키마 안의 테이블·데이터·인덱스만 되살리고 "CREATE SCHEMA public"은 건너뜁니다. 새 데이터베이스에는 public이 이미 있어서 그 줄이 실패하기 때문입니다.
#    --no-owner: 대상의 사용자 이름이 원본과 달라도 지금 사용자가 주인이 됩니다. --exit-on-error: 오류가 나면 바로 멈춥니다.
docker cp -q "$bundle_dir/db.dump" "$postgres_container:/tmp/footprint-restore.dump"
docker exec "$postgres_container" pg_restore -U "$db_user" -d "$db_name" -n public --no-owner --no-privileges --exit-on-error /tmp/footprint-restore.dump
docker exec "$postgres_container" rm -f /tmp/footprint-restore.dump

# 5. 버킷을 만들고 파일을 되살립니다. 파일 이름이 곧 산출물의 저장 위치(key)라서 데이터베이스 행과 다시 짝이 맞습니다.
docker exec "$minio_container" rm -rf /tmp/footprint-restore-files
# 컨테이너 안에 footprint-restore-files가 없어서, docker cp가 그 폴더를 만들고 files 안의 파일을 넣습니다.
docker cp -q "$bundle_dir/files" "$minio_container:/tmp/footprint-restore-files"
docker exec "$minio_container" mc mb --quiet "footprint-restore/$bucket" > /dev/null
docker exec "$minio_container" mc mirror --quiet /tmp/footprint-restore-files "footprint-restore/$bucket" > /dev/null
docker exec "$minio_container" rm -rf /tmp/footprint-restore-files

# 6. 되살린 결과를 묶음의 manifest.txt와 비교합니다. 테이블마다의 행 수와 파일 개수입니다.
docker cp -q "$script_dir/count-rows.sql" "$postgres_container:/tmp/footprint-count-rows.sql"
restored_rows="$(docker exec "$postgres_container" psql -U "$db_user" -d "$db_name" -tA -f /tmp/footprint-count-rows.sql)"
docker exec "$postgres_container" rm -f /tmp/footprint-count-rows.sql
expected_rows="$(grep '^rows\.' "$bundle_dir/manifest.txt" | tr -d '\r')"
restored_files="$(docker exec "$minio_container" mc ls --recursive "footprint-restore/$bucket" | wc -l | tr -d ' ')"
# 컨테이너 안에 남은 접속 이름(alias)을 지웁니다.
docker exec "$minio_container" mc alias remove footprint-restore > /dev/null
expected_files="$(grep '^file_count=' "$bundle_dir/manifest.txt" | cut -d= -f2- | tr -d '\r')"

if [ "$restored_rows" = "$expected_rows" ] && [ "$restored_files" = "$expected_files" ]; then
  echo "복구를 마쳤습니다. 테이블별 행 수와 파일 개수($restored_files 개)가 manifest.txt와 같습니다."
  echo "이제 백엔드를 켜고 GET /actuator/health 가 UP인지, 산출물 다운로드가 되는지 확인하세요."
else
  echo "복구는 끝났지만 manifest.txt와 다릅니다. 아래를 비교하세요." >&2
  echo "--- manifest.txt (파일 $expected_files 개)" >&2
  echo "$expected_rows" >&2
  echo "--- 되살린 결과 (파일 $restored_files 개)" >&2
  echo "$restored_rows" >&2
  exit 1
fi
