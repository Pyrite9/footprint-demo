#!/usr/bin/env bash
# main·dev branch 사이트와 proxy를 끕니다(배포-3). macOS·Linux용입니다. Windows는 stop.ps1을 씁니다.
# 사용법: 저장소 맨 위에서 bash infra/sites/stop.sh
# 컨테이너를 지우지만 데이터(volume)는 남습니다. 다시 켜면 그대로 있습니다.
# 한 사이트의 데이터까지 지우려면(되돌릴 수 없음) 이 폴더에서:
#   SITE=main docker compose -p footprint-main -f site.yml down -v
set -euo pipefail

sites_dir="$(cd "$(dirname "$0")" && pwd)"
site_file="$sites_dir/site.yml"
proxy_file="$sites_dir/proxy.yml"
sites="main dev"

# 1. Docker가 켜져 있는지 확인합니다.
if ! docker info > /dev/null 2>&1; then
  echo "Docker가 켜져 있지 않습니다. 꺼져 있으면 사이트도 이미 꺼진 상태입니다." >&2
  exit 1
fi

# 2. proxy를 먼저 끕니다. proxy가 두 사이트의 network에 붙어 있어서, 먼저 떼어야 사이트를 끌 때 network를 지울 수 있습니다.
if ! docker compose -f "$proxy_file" down; then
  echo "proxy를 끄지 못했습니다. docker ps로 footprint-sites-proxy 컨테이너를 확인하세요." >&2
  exit 1
fi

# 3. 두 사이트를 끕니다. site.yml이 ${SITE}를 읽으므로 SITE 환경 변수를 넣어야 compose 파일이 읽힙니다.
for site in $sites; do
  if ! SITE="$site" docker compose -p "footprint-$site" -f "$site_file" down; then
    echo "$site 사이트를 끄지 못했습니다. docker ps로 footprint-$site- 로 시작하는 컨테이너를 확인하세요." >&2
    exit 1
  fi
done
echo "껐습니다. 데이터는 남아 있습니다. 다시 켜려면 bash infra/sites/start.sh"
