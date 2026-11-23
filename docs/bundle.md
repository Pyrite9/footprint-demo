# 제출용 실행 묶음 (O-3)

- 12.11 학기 발표와 12.15 PBL 결과물 제출에 쓰는 **로컬 실행 묶음**을 만들고 USB에 담는 방법
- 제출 형태는 "접속 가능한 주소"가 아니라 "로컬 실행 묶음", 묶음은 Docker compose 기반(2026.11.12 관리자 결정). 서버, 도메인, Google Cloud 설정은 이 단위에 없음
- 받는 사람(담당 교수, 강단 PC)이 읽는 안내는 묶음 맨 위의 `README.md`. 원본은 `infra/bundle/template/README.md`

## 묶음 하나에 든 것

- 묶음 = 폴더 하나(`footprint-demo/`). 받는 PC에서 인터넷과 빌드 없이 켜짐

| 경로 | 무엇 | 원본 |
|---|---|---|
| `README.md` | 받는 사람용 안내 | `infra/bundle/template/README.md` |
| `docker-compose.yml` | 컨테이너 5개(https proxy 포함). `build` 대신 `image`, `pull_policy: never` | `infra/bundle/template/docker-compose.yml`(`__VERSION__`을 바꿔 넣음) |
| `proxy/nginx.conf` | https proxy의 nginx 설정. compose가 읽기 전용으로 붙임 | `infra/bundle/template/proxy/nginx.conf` |
| `tools/mkcert-*` | 받는 PC에서 https 인증서를 만드는 mkcert v1.4.4 실행 파일 4개(Windows amd64, macOS amd64·arm64, Linux amd64) | `make-bundle.ps1`이 받아서 넣음(`infra/bundle/cache/mkcert/`에 보관, `.gitignore`) |
| `env.template` | `.env`의 원본. 비밀값 세 줄이 비어 있음 | `infra/bundle/template/env.template` |
| `start`·`stop`·`reset` `.bat`/`.ps1`/`.sh` | 켜기, 끄기(데이터 남김), 초기화(데이터 지움, `delete` 입력 필요) | `infra/bundle/template/` |
| `backup/` | O-4 백업·복구 스크립트 4개와 `count-rows.sql` | `infra/backup/` |
| `images/*.tar`, `images/images.txt` | `docker save` 한 이미지 6개와 목록 | `make-bundle.ps1`이 만듦 |
| `VERSION.txt` | 묶음 버전, 만든 시각, commit, 이미지 이름 | `make-bundle.ps1`이 만듦 |
| `SHA256SUMS.txt` | 파일마다의 sha256. `sha256sum -c` 모양 | `make-bundle.ps1`이 만듦 |

- 이미지 6개(모두 `linux/amd64`)
  - `footprint-backend:<버전>`
  - `footprint-frontend:<버전>-demo`(이메일·비밀번호 로그인 폼 있음), `footprint-frontend:<버전>-prod`(폼 없음)
  - `postgres:17`, `pgsty/minio:RELEASE.2026-08-04T00-00-00Z`(개발 환경과 같은 이미지)
  - `nginx:1.28-alpine`(https proxy. 화면 이미지의 base와 같은 이미지)
- 인증서(`certs/`)는 묶음에 없음. 받는 PC의 start가 처음 켤 때 만듦(아래 "https 구조")
- `<버전>`은 만든 날짜(`yyyy.MM.dd`). `-Version`으로 바꿀 수 있음

## 개발 환경과 다른 이름

- 같은 PC에서 개발 환경을 켠 채 묶음을 켤 수 있게 이름을 모두 다르게 둠

| | 개발 환경 | 묶음 |
|---|---|---|
| compose 이름 | `footprint-demo` | `footprint-bundle` |
| 컨테이너 | `footprint-demo-postgres` 등 | `footprint-bundle-postgres`, `-minio`, `-backend`, `-frontend` |
| volume | `footprint-demo_postgres-data` 등 | `footprint-bundle_postgres-data`, `footprint-bundle_minio-data` |
| 호스트 포트 | 5432, 9000, 9001(DB·MinIO), 8080, 5173 | 443, 80, 8080만 |
| 화면 주소 | `http://localhost:5173` | `https://localhost` |

- 묶음의 PostgreSQL·MinIO는 호스트 포트를 열지 않아서 개발 DB·MinIO와 부딪히지 않음
- 묶음의 화면 컨테이너도 호스트 포트를 열지 않음. 브라우저는 proxy(443)를 거쳐서만 화면에 닿음. 그래서 5173은 개발 서버와 부딪히지 않음
- 8080은 같음. **개발용 백엔드(8080)는 끈 채로 묶음을 켬**
- 백엔드의 허용 주소(`CORS_ALLOWED_ORIGINS`)는 묶음 compose가 `https://localhost`로 넣음. 구글 로그인도 이 주소를 기준으로 함
- 화면 이미지에는 백엔드 주소가 아니라 `/api`가 들어 있음. 화면 컨테이너의 nginx가 `/api`를 떼고 compose 안의 `backend` 서비스로 넘김(`frontend/nginx.conf`). 묶음의 8080은 상태 확인과 Postman용임

## https 구조

- 멘토 확인 기준: 묶음을 켠 PC에서 `https://localhost`가 경고 없이 열리고, 브라우저 개발자 도구에서 refresh token cookie에 `Secure`가 붙어 있음. 다른 PC에서 접속하는 것은 범위 밖
- 요청이 지나는 길

```
브라우저 ──https──▶ proxy(nginx, 127.0.0.1:443, TLS를 여기서 풂)
                     └─http─▶ frontend(nginx:80) ─ 화면 파일
                                └─ /api/ ─http─▶ backend(8080)
```

- proxy는 묶음 compose의 서비스 `proxy`. 설정은 묶음 폴더의 `proxy/nginx.conf`(원본 `infra/bundle/template/proxy/nginx.conf`)를 읽기 전용으로 붙임. 이미지는 화면 이미지의 base와 같은 `nginx:1.28-alpine`이라 새 도구를 들이지 않음
- 화면 컨테이너의 nginx(`frontend/nginx.conf`)는 바꾸지 않음. `/api`를 backend로 넘기는 구조, 20MB 상한과 413→400 JSON 처리가 그대로임
  - proxy는 업로드 상한을 두지 않고(`client_max_body_size 0`), body를 쌓지 않고 바로 넘김(`proxy_request_buffering off`). 상한을 proxy에 두면 그보다 큰 업로드에 proxy의 413 HTML이 먼저 나가서 화면이 오류 문장을 읽지 못함
  - `Host`는 브라우저가 보낸 값(`localhost`)을 그대로 넘김
- 백엔드 설정은 compose 환경 변수 두 줄만 바꿈. Java 코드는 그대로임
  - `REFRESH_COOKIE_SECURE: "true"` — demo와 prod 모두
  - `CORS_ALLOWED_ORIGINS: https://localhost` — refresh token 요청의 `Origin` 검사(`AuthService.checkOrigin`)와 CORS가 이 값을 씀
  - 백엔드는 요청을 http로 받음. Spring은 `Origin`(https://localhost)과 요청 주소(http://localhost)가 달라 화면의 POST·PUT·DELETE를 CORS 요청으로 보지만, `Origin`이 허용 목록에 있어서 통과함
- **80은 302로 https로 보냄** — 301은 브라우저가 영구히 기억해서, 묶음을 끈 뒤에도 이 PC의 다른 `http://localhost` 프로그램이 https로 바뀜. 302는 기억하지 않음. 보내는 주소는 `https://localhost`로 고정(`http://127.0.0.1`로 열어도 로그인이 되는 주소로 감)
- **HSTS header는 넣지 않음** — 브라우저가 `localhost`를 https로만 열게 되어 개발 서버 `http://localhost:5173`까지 열리지 않게 됨
- **인증서는 받는 PC에서 만듦**
  - 이유: 인증서의 개인 key를 묶음(USB)에 싣지 않으려는 것. 묶음에서 key가 새면 그 인증서를 믿는 PC에서 다른 사람이 `localhost`인 척할 수 있음. 받는 PC에서 만들면 key와 로컬 인증 기관이 그 PC 밖으로 나가지 않음
  - 묶음에는 mkcert 실행 파일만 넣음(`tools/`). `make-bundle.ps1`이 GitHub release에서 받아 스크립트 안의 고정 SHA-256과 비교하고, 다르면 묶음을 만들지 않음
  - start가 `certs/footprint.pem`이 없을 때만 (1) `mkcert -install`로 이 PC에 로컬 인증 기관을 만들어 신뢰 목록에 넣고(Windows 확인 창, macOS 비밀번호) (2) 인증서를 만듦. (1)이 거절되거나 실패해도 멈추지 않고 인증서를 만들어 켬. 그때는 브라우저 경고 화면에서 "고급 → 계속"으로 넘어감
  - 인증서의 이름: `localhost`, `127.0.0.1`, `::1`, `main.footprint.localhost`, `dev.footprint.localhost`. 묶음의 proxy는 `localhost`만 받고 뒤 둘은 쓰지 않음. branch 사이트(`docs/branch-sites.md`)는 인증서를 `infra/sites/certs`에 따로 만듦
  - reset은 인증서와 로컬 인증 기관을 지우지 않음. 빼는 법은 받는 사람용 `README.md` "https 인증서"(`mkcert -uninstall`)
  - Firefox는 Windows의 신뢰 목록을 기본으로 쓰지 않아 경고가 뜰 수 있음. Chrome·Edge로 엶
- **cookie의 `Secure` 확인 방법**
  1. `https://localhost`에서 시작 계정으로 로그인
  2. F12 → Application(Edge "애플리케이션") → Storage의 Cookies → `https://localhost`
  3. `refreshToken` 줄의 `HttpOnly`, `Secure`에 체크 표시, `SameSite`가 `Strict`
  - Network 탭에서 `/api/auth/login` 응답의 `Set-Cookie` header에 `Secure`가 있는지로도 봄
- **구글 로그인** — Google Cloud의 OAuth Client "승인된 JavaScript 원본"에 `https://localhost`를 더해야 함(관리자 작업). 없으면 구글 로그인 창이 열리지 않음. 이메일·비밀번호 로그인(demo)은 상관없음

## 묶음 만들기

- 필요한 것: Docker Desktop, 인터넷(빌드할 때 Gradle·npm 의존성과 기반 이미지를 받음), 저장소 폴더
- 저장소 맨 위에서
  ```powershell
  .\infra\bundle\make-bundle.ps1
  ```
- 제출용은 Docker의 빌드 cache를 쓰지 않고 처음부터 만듦(`-NoCache`). 몇 분 더 걸리지만 저장소만으로 빌드되는지 함께 확인됨
  ```powershell
  .\infra\bundle\make-bundle.ps1 -NoCache
  ```
- 하는 일: mkcert 고정 SHA-256이 채워졌는지 확인 → 백엔드 이미지 1개와 화면 이미지 2개를 새로 빌드 → 없는 기반 이미지(PostgreSQL, MinIO, nginx)를 받음 → mkcert 4개를 받거나 cache에서 꺼내 SHA-256 확인 → `infra/bundle/out/footprint-demo/`를 새로 만듦(이전 결과는 지움) → 템플릿, `proxy/`, `tools/`, 백업 스크립트 복사 → 이미지 6개를 `docker save` → `VERSION.txt`, `SHA256SUMS.txt`(`proxy/`, `tools/`도 들어감)
- mkcert는 처음 한 번만 인터넷에서 받아 `infra/bundle/cache/mkcert/`(`.gitignore`)에 둠. SHA-256이 스크립트의 고정값과 다르면 그 파일을 지우고 멈춤. mkcert 판을 바꾸면 `$mkcertVersion`과 고정값 4개를 함께 바꾸고 cache를 지움
- 결과 폴더 `infra/bundle/out/`는 `.gitignore`에 있음. 이미지 tar가 들어 있어 저장소에 올리지 않음
- commit 하지 않은 변경이 있으면 `VERSION.txt`의 commit 줄에 "(commit 하지 않은 변경 포함)"이 붙음. 제출용 묶음은 commit 한 뒤에 만듦
- macOS·Linux용 만들기 스크립트(bash)는 두지 않음. 묶음은 관리자 Windows PC에서 한 번 만들고, 같은 일을 두 벌로 고치는 비용이 큼. PowerShell 7(`pwsh`)에서 돌 수 있게 썼으나 실제로 돌려 보지 않음

## USB에 담기

- 쓰기 전에 USB가 맞는지 확인함(이름, 파일시스템, 용량)
  ```powershell
  Get-Volume -DriveLetter F | Format-List FileSystemLabel, FileSystem, Size, SizeRemaining
  ```
- 복사(USB에는 `F:\footprint-demo\` 폴더 하나만 둠). 이전 묶음이 있으면 먼저 지움. 다른 버전의 파일이 섞이면 `SHA256SUMS.txt`와 맞지 않음
  ```powershell
  Remove-Item -Recurse -Force F:\footprint-demo
  Copy-Item -Recurse infra\bundle\out\footprint-demo F:\footprint-demo
  ```
- 복사한 파일이 원본과 같은지 확인(Git Bash). 모두 `OK`여야 함
  ```bash
  cd /f/footprint-demo && sha256sum -c SHA256SUMS.txt
  ```
- Windows PowerShell에서 확인하려면 받는 사람용 `README.md` "USB에서 PC로 복사한 뒤 실행"의 명령을 씀
- USB에서 시험으로 켰다면, 넘기기 전에 USB의 `.env`와 `backup/bundles/`를 지움. 데이터(volume)는 USB가 아니라 이 PC의 Docker에 있음(시험이 끝나면 묶음 폴더에서 `docker compose down -v`로 묶음 volume만 지움)

## 이 PC에서 시험할 때

- 개발 환경(`footprint-demo-postgres`, `footprint-demo-minio`)과 화면 개발 서버(5173)는 켠 채로 둬도 됨. 개발용 백엔드(8080)만 끔
- 관리자 PC에서 처음 start를 돌리면 관리자 PC에도 로컬 인증 기관이 들어감. 받는 PC와 같은 흐름을 보려면 묶음의 `certs/`를 지우고 다시 start
- 이미지 load를 시험하려면 묶음 tag 이미지만 지우고 start를 돌림. `postgres:17`, `pgsty/minio:...`는 개발 컨테이너가 쓰고 있어서 지우지 못함
  ```powershell
  docker rmi footprint-backend:<버전> footprint-frontend:<버전>-demo footprint-frontend:<버전>-prod
  ```
- load한 이미지의 ID는 빌드한 이미지의 ID와 다르게 나왔음(2026.11.12 확인. `docker save --platform`이 한 플랫폼만 담는 탓으로 봄). start는 이름(tag)만 보므로 동작에는 차이가 없음

## 4절 "배포할 때 반드시 할 것"을 묶음에서 처리한 방법

- **비밀값** — 첫 start가 `JWT_SECRET`(32바이트), `POSTGRES_PASSWORD`·`MINIO_ROOT_PASSWORD`(24바이트)를 암호용 난수로 만들어 16진수로 묶음의 `.env`에 넣음. 저장소의 공개 기본값을 쓰지 않음. 값은 화면에 찍지 않음
  - PostgreSQL 비밀번호는 volume을 처음 만들 때 고정됨. `.env`를 지우고 새로 만들면 백엔드가 `password authentication failed`로 켜지지 않음
  - 그래서 start는 "volume은 있는데 `.env`가 없음"이면 새 `.env`를 만들지 않고 멈춤. `.env`를 되살리거나 reset 뒤 켜라고 안내함
- **포트** — proxy `127.0.0.1:443`, `127.0.0.1:80`(https로 보내기만 함), 백엔드 `127.0.0.1:8080`만 엶. 화면은 8080을 쓰지 않고 proxy와 화면 컨테이너를 거쳐 백엔드를 부름. PostgreSQL·MinIO는 `ports`가 없어 호스트에서 접속되지 않음. 같은 와이파이의 다른 PC에서도 접속되지 않음
- **시작 데이터와 로그인 방식** — `.env`의 `FOOTPRINT_MODE`
  - `demo`(기본): Spring profile `demo`(설정 파일 없음 → 개발 설정과 같음). 시작 데이터, 이메일·비밀번호 로그인. `ProductionSettingsChecker`는 `prod`만 보므로 끼어들지 않음. 화면은 `-demo` 이미지
  - `prod`: Spring profile `prod`. 시작 데이터 없음, `POST /auth/login` 404, 구글 로그인만. `PROFESSOR_EMAILS`가 비면 start가 미리 멈춤. 화면은 `-prod` 이미지(폼 없음)
  - 방식을 바꿀 때는 reset으로 데이터를 지운 뒤 켬(demo의 시작 계정이 prod 데이터베이스에 남지 않게)
- **스키마** — 그대로 Flyway migration과 `ddl-auto: validate`(O-1). 묶음을 다시 켜도 데이터가 남음
- **CORS** — 묶음 compose가 `CORS_ALLOWED_ORIGINS=https://localhost`로 넣음. 묶음은 이 주소로만 엶. 이 값은 refresh token의 `Origin` 검사에도 쓰임(위 "https 구조")
- **https** — 묶음 안의 proxy가 `https://localhost`를 맡고, refresh token cookie에 `Secure`를 붙임(`REFRESH_COOKIE_SECURE=true`). 인증서는 받는 PC에서 만듦(위 "https 구조")
- **백업** — O-4 스크립트를 묶음의 `backup/`에 넣음. 컨테이너 이름 옵션으로 묶음 컨테이너를 가리킴(받는 사람용 `README.md` "백업과 복구")
  - 하는 것: 묶음 안에서 수동 백업·복구, 개인정보와 보관 기간 안내
  - 하지 않는 것: 자동 예약(강단 PC는 시연 동안만 켜고, 받는 PC의 작업 스케줄러를 바꾸는 일이라 받는 사람이 정함), 다른 곳 보관과 암호화(받는 사람의 저장 장치 선택. 안내만 함), 기간 지난 묶음 자동 삭제(보관 기간이 X-2 초안이라 정해지지 않음. 수동 삭제 안내)
- **구글 로그인 동의 화면** — Google Cloud 콘솔 작업이라 하지 않음. 동의 화면이 "테스트 중"이면 테스트 사용자만 로그인됨. 받는 사람 안내에 "인터넷 필요, 테스트 사용자만"을 적음. 관리자가 할 일로 `TODO.md` 4절에 남김

## 알려진 한계

- 이미지는 amd64만 넣음. Apple Silicon Mac에서는 Docker Desktop의 변환(Rosetta·QEMU)으로 돌아 느림. arm64 이미지를 함께 넣으면 묶음이 두 배가 되고 빌드에 변환이 필요해서 넣지 않음
- 포트 443, 80, 8080을 바꿀 수 없음(위 "개발 환경과 다른 이름"). 443이나 80을 다른 웹 서버(IIS 등)가 쓰는 PC에서는 그것을 끈 뒤 켬
- https는 묶음을 켠 PC 안에서만 됨. 인증서는 그 PC의 로컬 인증 기관이 서명해서 다른 PC는 믿지 않음. 묶음은 127.0.0.1에만 열어서 다른 PC에서 접속할 수도 없음
- Linux의 rootless Docker는 1024 아래 포트(443, 80)를 열지 못함. 확인하지 않음
- 받는 PC의 운영체제·CPU가 Windows amd64, macOS amd64·arm64, Linux amd64가 아니면 인증서를 만들 mkcert가 없어서 start가 멈춤
- 받는 PC에 같은 이름의 `postgres:17`이 이미 있으면 그 이미지를 씀(start는 없는 이미지만 load). 같은 17 판이라 동작은 같음
