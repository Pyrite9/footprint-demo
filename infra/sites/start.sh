#!/usr/bin/env bash
# main branch와 dev branch의 사이트를 함께 켭니다(배포-3). macOS·Linux용입니다. Windows는 start.ps1을 씁니다.
# 사용법: 저장소 어디서든 터미널에서 bash infra/sites/start.sh (저장소 맨 위에서 실행하는 경우)
# 하는 일: Docker·git 확인 → GitHub에서 main·dev 받기 → 두 branch의 코드 꺼내기 → 인증서가 없으면 만듦 → 두 사이트 켜기 → proxy 켜기 → 열 주소 안내
set -euo pipefail

# 이 스크립트가 있는 폴더(infra/sites)를 기준으로 파일을 찾습니다. 어느 폴더에서 실행해도 같게 동작합니다.
sites_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$sites_dir/../.." && pwd)"
site_file="$sites_dir/site.yml"
proxy_file="$sites_dir/proxy.yml"
src_dir="$sites_dir/src"
cert_dir="$sites_dir/certs"
cert_file="$cert_dir/footprint.pem"
key_file="$cert_dir/footprint-key.pem"
# 켤 사이트입니다. 사이트 이름이 branch 이름이고, 주소(main.footprint.localhost)와 컨테이너 이름(footprint-main-*)에도 쓰입니다.
sites="main dev"

# 문장을 보여 주고 스크립트를 멈춥니다(종료 코드 1).
stop_with_message() {
  echo "$1" >&2
  exit 1
}

# 1. Docker가 켜져 있는지, git이 있는지 확인합니다. docker info는 Docker Desktop이 꺼져 있으면 실패합니다.
if ! docker info > /dev/null 2>&1; then
  stop_with_message "Docker가 켜져 있지 않습니다. Docker Desktop을 켜고 'Engine running'이 된 뒤 다시 실행하세요."
fi
if ! command -v git > /dev/null 2>&1; then
  stop_with_message "git이 없습니다. git을 설치한 뒤 다시 실행하세요."
fi

# 2. GitHub(origin)에서 main과 dev의 최신 commit을 받습니다. 받기만 하고, 지금 작업 중인 branch와 폴더는 바꾸지 않습니다.
#    git -C: 저장소 맨 위 폴더에서 실행한 것처럼 git을 부릅니다.
echo "GitHub에서 main, dev를 받는 중입니다..."
if ! git -C "$repo_root" fetch origin main dev; then
  # 33m: 노란 글자입니다.
  printf '\033[33m%s\033[0m\n' "GitHub에서 받지 못했습니다(인터넷 확인). 이 PC에 이미 받아 둔 origin/main, origin/dev로 계속합니다."
fi

# 3. 두 branch에 commit 된 코드(backend, frontend 폴더)를 src/<branch> 폴더에 꺼냅니다. site.yml이 이 폴더의 코드로 이미지를 만듭니다.
#    git archive는 그 branch에 commit 된 파일만 꺼냅니다. 지금 작업 중인 폴더(checkout)와 commit 하지 않은 변경은 쓰지 않고 바꾸지도 않습니다.
for site in $sites; do
  ref="origin/$site"
  if ! git -C "$repo_root" rev-parse --verify --quiet "$ref" > /dev/null; then
    stop_with_message "$ref 가 이 PC에 없습니다. 인터넷에 연결한 뒤 다시 실행하세요. GitHub에 $site branch가 있는지도 확인하세요."
  fi

  # 이전에 꺼낸 코드를 지우고 새로 꺼냅니다. 지우지 않으면 branch에서 지운 파일이 남습니다.
  site_src="$src_dir/$site"
  tar_file="$src_dir/$site.tar"
  rm -rf "$site_src"
  mkdir -p "$site_src"

  # tar 파일로 받은 뒤 풉니다(start.ps1과 같은 순서).
  git -C "$repo_root" archive --format=tar -o "$tar_file" "$ref" backend frontend \
    || stop_with_message "$ref 의 코드를 꺼내지 못했습니다."
  tar -xf "$tar_file" -C "$site_src" || stop_with_message "$ref 의 코드를 풀지 못했습니다: $tar_file"
  rm -f "$tar_file"

  echo "  $site 사이트의 코드: $(git -C "$repo_root" log -1 --format="%h %s" "$ref")"
done

# 4. https 인증서가 없으면 이 PC에서 mkcert로 만듭니다. 이미 있으면 건너뜁니다.
#    인증서의 개인 key는 이 PC 밖으로 나가지 않게 저장소에 올리지 않습니다(.gitignore). 만든 인증서는 이 PC에서만 믿을 수 있습니다.
if [ ! -f "$cert_file" ] || [ ! -f "$key_file" ]; then
  # mkcert를 찾습니다. 이 PC에 설치한 mkcert → 실행 묶음을 만들 때 받아 둔 파일(make-bundle.ps1) 순서입니다.
  if command -v mkcert > /dev/null 2>&1; then
    mkcert="$(command -v mkcert)"
  else
    # 운영체제(uname -s)와 CPU(uname -m)에 맞는 mkcert 파일을 고릅니다.
    case "$(uname -s)-$(uname -m)" in
      Darwin-arm64) mkcert="$repo_root/infra/bundle/cache/mkcert/mkcert-darwin-arm64" ;;
      Darwin-x86_64) mkcert="$repo_root/infra/bundle/cache/mkcert/mkcert-darwin-amd64" ;;
      Linux-x86_64) mkcert="$repo_root/infra/bundle/cache/mkcert/mkcert-linux-amd64" ;;
      *) mkcert="" ;;
    esac
    if [ -z "$mkcert" ] || [ ! -f "$mkcert" ]; then
      stop_with_message "인증서를 만드는 프로그램(mkcert)이 없습니다. infra/bundle/make-bundle.ps1을 한 번 실행하거나 mkcert를 설치하세요."
    fi
    # Windows에서 받은 파일에는 실행 권한이 없을 수 있어서 붙입니다.
    chmod +x "$mkcert"
  fi
  # (a) mkcert가 이 PC에 로컬 인증 기관(CA)을 만들고 운영체제의 신뢰 목록에 넣습니다. 이미 넣었으면 그대로 둡니다.
  echo "https 인증서를 처음 만듭니다. 이 PC에 로컬 인증 기관(mkcert)을 신뢰 목록에 넣습니다."
  echo "  이 PC의 로그인 비밀번호를 물으면 입력하세요(macOS는 확인 창, Linux는 sudo). 거절해도 켜지지만, 브라우저에 경고 화면이 뜹니다."
  ca_installed=true
  "$mkcert" -install || ca_installed=false
  # (b) 두 사이트의 주소가 든 인증서 하나를 만듭니다. 앞 단계가 실패해도 만들 수 있습니다(CA는 신뢰 목록에 없는 채로 만들어져 있음).
  mkdir -p "$cert_dir"
  "$mkcert" -cert-file "$cert_file" -key-file "$key_file" main.footprint.localhost dev.footprint.localhost \
    || stop_with_message "https 인증서를 만들지 못했습니다. 위의 mkcert 메시지를 확인하세요."
  if [ "$ca_installed" = false ]; then
    echo "로컬 인증 기관을 신뢰 목록에 넣지 못했습니다. 그대로 켭니다."
    echo "  브라우저에 '연결이 비공개로 설정되어 있지 않습니다' 경고가 뜨면 '고급' → '(안전하지 않음)으로 이동'을 누르세요."
    echo "  다시 넣으려면 infra/sites/certs 폴더를 지우고 start를 다시 실행하세요."
  fi
fi

# 5. 두 사이트를 차례로 켭니다. 같은 site.yml을 project 이름(-p)만 바꿔 켭니다. SITE 환경 변수가 site.yml의 ${SITE}에 들어갑니다.
#    --build: 꺼낸 코드로 이미지를 새로 만듭니다. 처음에는 사이트마다 이미지 빌드에 몇 분 걸립니다.
#    --wait: 컨테이너가 켜지고 healthcheck(백엔드는 /actuator/health)가 통과할 때까지 기다립니다.
for site in $sites; do
  echo "$site 사이트를 켜는 중입니다. 백엔드가 준비될 때까지 기다립니다..."
  if ! SITE="$site" docker compose -p "footprint-$site" -f "$site_file" up -d --build --wait --wait-timeout 600; then
    stop_with_message "$site 사이트를 켜지 못했습니다.
- 백엔드 로그: docker logs footprint-$site-backend --tail 100"
  fi
done

# 6. proxy를 켭니다. --force-recreate: 사이트 컨테이너가 다시 만들어지면 주소가 바뀌어서, proxy를 매번 새로 만들어 주소를 다시 찾게 합니다.
echo "proxy를 켜는 중입니다..."
if ! docker compose -f "$proxy_file" up -d --force-recreate; then
  stop_with_message "proxy를 켜지 못했습니다.
- 포트 443, 80을 다른 프로그램(실행 묶음 등)이 쓰고 있지 않은지
- proxy 로그: docker logs footprint-sites-proxy --tail 100"
fi

# 7. 열 주소를 알려 줍니다.
echo ""
echo "켜졌습니다."
for site in $sites; do
  echo "  https://$site.footprint.localhost  ($site 사이트, $(git -C "$repo_root" log -1 --format="%h %s" "origin/$site"))"
done
echo "  시작 계정: professor@example.com / pass1234 (두 사이트 모두)"
echo "  끄기: bash infra/sites/stop.sh (데이터는 남습니다)"
