#!/usr/bin/env bash
# 발자국 실행 묶음을 켭니다. macOS·Linux용입니다. Windows는 start.bat(start.ps1)을 씁니다.
# 사용법: 터미널에서 이 폴더로 가서 bash start.sh
# 하는 일: Docker 확인 → 이미지가 없으면 load → .env가 없으면 만듦 → 켜기 → 백엔드가 준비될 때까지 기다림 → 열 주소 안내
set -euo pipefail

# 이 스크립트가 있는 폴더(묶음 폴더)를 기준으로 파일을 찾습니다. 어느 폴더에서 실행해도 같게 동작합니다.
bundle_dir="$(cd "$(dirname "$0")" && pwd)"
compose_file="$bundle_dir/docker-compose.yml"
env_file="$bundle_dir/.env"
env_template="$bundle_dir/env.template"
image_list="$bundle_dir/images/images.txt"

# 문장을 보여 주고 스크립트를 멈춥니다(종료 코드 1).
stop_with_message() {
  echo "$1" >&2
  exit 1
}

# 무작위 값을 16진수 글자로 만듭니다. /dev/urandom(운영체제의 암호용 난수)에서 바이트를 읽습니다. 바이트 하나가 글자 두 개가 됩니다.
random_hex() {
  od -An -tx1 -N"$1" /dev/urandom | tr -d ' \n'
}

# .env에서 "이름=값" 줄의 값을 읽습니다. 없으면 빈 글자입니다. Windows에서 만든 .env의 줄 끝 \r도 지웁니다.
env_value() {
  grep "^$1=" "$env_file" | tail -n 1 | cut -d= -f2- | tr -d '\r' | xargs || true
}

# 1. Docker가 켜져 있는지 확인합니다. docker info는 Docker Desktop이 꺼져 있으면 실패합니다.
if ! docker info > /dev/null 2>&1; then
  stop_with_message "Docker가 켜져 있지 않습니다. Docker Desktop을 켜고 'Engine running'이 된 뒤 다시 실행하세요."
fi

# 2. 필요한 이미지가 이 컴퓨터에 없으면 images 폴더의 tar 파일에서 넣습니다(docker load). 인터넷을 쓰지 않습니다.
#    images.txt의 한 줄은 "tar 파일 이름|이미지 이름"입니다. 이미 있는 이미지는 건너뜁니다.
while IFS='|' read -r tar_name image || [ -n "$tar_name" ]; do
  tar_name="$(echo "$tar_name" | tr -d '\r' | xargs)"
  image="$(echo "${image:-}" | tr -d '\r' | xargs)"
  if [ -z "$tar_name" ] || [ "${tar_name:0:1}" = "#" ]; then continue; fi
  if ! docker image inspect "$image" > /dev/null 2>&1; then
    echo "이미지를 넣는 중: $image (처음 한 번만, 1~3분 걸립니다)"
    docker load -i "$bundle_dir/images/$tar_name" || stop_with_message "이미지를 넣지 못했습니다: $tar_name"
  fi
done < "$image_list"

# 3. 설정 파일 .env가 없으면 env.template을 복사해 만들고, 비어 있는 비밀값 세 줄을 무작위 값으로 채웁니다.
#    값은 화면에 찍지 않습니다. 데이터(volume)가 이미 있는데 .env만 없으면, 새 비밀번호로는 데이터베이스가 열리지 않으므로 멈춥니다.
if [ ! -f "$env_file" ]; then
  if docker volume inspect footprint-bundle_postgres-data > /dev/null 2>&1; then
    stop_with_message "데이터는 남아 있는데 설정 파일 .env가 없습니다. 처음 켤 때 만든 .env를 이 폴더에 되살리세요.
데이터를 버리고 새로 시작하려면 reset 스크립트로 지운 뒤 다시 켜세요."
  fi
  # umask 077: 새로 만드는 .env를 이 컴퓨터의 다른 사용자가 읽지 못하게 합니다(macOS·Linux 파일 권한).
  umask 077
  while IFS= read -r line || [ -n "$line" ]; do
    line="$(printf '%s' "$line" | tr -d '\r')"
    case "$line" in
      "JWT_SECRET=") echo "JWT_SECRET=$(random_hex 32)" ;;
      "POSTGRES_PASSWORD=") echo "POSTGRES_PASSWORD=$(random_hex 24)" ;;
      "MINIO_ROOT_PASSWORD=") echo "MINIO_ROOT_PASSWORD=$(random_hex 24)" ;;
      *) echo "$line" ;;
    esac
  done < "$env_template" > "$env_file"
  echo "설정 파일 .env를 만들었습니다. 비밀값은 이 파일에만 있습니다. 다른 사람에게 보내지 마세요."
fi

# 4. 실행 방식을 확인합니다. prod는 교수 이메일이 없으면 백엔드가 켜지지 않으므로 미리 멈춥니다.
mode="$(env_value FOOTPRINT_MODE)"
mode="${mode:-demo}"
if [ "$mode" != "demo" ] && [ "$mode" != "prod" ]; then
  stop_with_message ".env의 FOOTPRINT_MODE는 demo 또는 prod여야 합니다. 지금 값: $mode"
fi
if [ "$mode" = "prod" ] && [ -z "$(env_value PROFESSOR_EMAILS)" ]; then
  stop_with_message "FOOTPRINT_MODE=prod에서는 .env의 PROFESSOR_EMAILS에 교수 학교 이메일을 적어야 합니다."
fi
echo "실행 방식: $mode"

# 5. 켭니다. --wait: 모든 컨테이너가 켜지고 healthcheck(백엔드는 /actuator/health)가 통과할 때까지 기다립니다. 처음에는 1~2분 걸립니다.
echo "켜는 중입니다. 백엔드가 준비될 때까지 기다립니다..."
if ! docker compose -f "$compose_file" up -d --wait --wait-timeout 300; then
  stop_with_message "켜지 못했습니다. 자주 막히는 곳은 README.md의 '자주 막히는 곳'에 있습니다.
- 포트 5173이나 8080을 다른 프로그램이 쓰고 있지 않은지
- 백엔드 로그: docker logs footprint-bundle-backend --tail 100"
fi

# 6. 열 주소를 알려 줍니다. 화면은 127.0.0.1이 아니라 localhost로 열어야 로그인이 됩니다(백엔드가 이 주소만 허용합니다).
echo ""
echo "켜졌습니다."
echo "  화면: http://localhost:5173"
echo "  상태 확인: http://127.0.0.1:8080/actuator/health"
if [ "$mode" = "demo" ]; then echo "  시작 계정: professor@example.com / pass1234 (다른 계정은 README.md)"; fi
echo "  끄기: bash stop.sh (데이터는 남습니다)"
