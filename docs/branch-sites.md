# branch 사이트 — 배포 환경 분리 (배포-3)

- 한 PC에서 `main` branch의 코드로 뜬 사이트와 `dev` branch의 코드로 뜬 사이트를 함께 띄우는 방법
- 멘토링 과제 "배포 환경 분리"용. 저장소가 있는 PC(관리자 PC)에서 켬. 제출용 실행 묶음(`docs/bundle.md`)에는 넣지 않음
- 파일은 모두 `infra/sites/`에 있음

## 주소

| 주소 | 코드 | 데이터 |
|---|---|---|
| `https://main.footprint.localhost` | GitHub의 `main`(`origin/main`) | main 사이트 전용 PostgreSQL·MinIO |
| `https://dev.footprint.localhost` | GitHub의 `dev`(`origin/dev`) | dev 사이트 전용 PostgreSQL·MinIO |

- 사이트마다 화면, 백엔드, PostgreSQL, MinIO가 따로 있음. 한 사이트에서 만든 기록은 다른 사이트에 없음
- 두 사이트 모두 개발 설정(시작 데이터, 이메일·비밀번호 로그인)으로 켜짐. 시작 계정은 `professor@example.com / pass1234`

## 구조

```
브라우저 ──https──▶ proxy(footprint-sites-proxy, 127.0.0.1:443, TLS를 여기서 풂)
                     │  주소의 hostname으로 사이트를 고름
                     ├─ main.footprint.localhost ─http─▶ footprint-main-frontend ─ /api ─▶ footprint-main-backend ─▶ footprint-main-postgres, footprint-main-minio
                     └─ dev.footprint.localhost  ─http─▶ footprint-dev-frontend  ─ /api ─▶ footprint-dev-backend  ─▶ footprint-dev-postgres,  footprint-dev-minio
```

| 파일 | 무엇 |
|---|---|
| `infra/sites/site.yml` | 사이트 하나(컨테이너 4개)의 compose 파일. 두 번 켬 |
| `infra/sites/proxy.yml` | proxy 컨테이너 하나의 compose 파일 |
| `infra/sites/proxy/nginx.conf` | proxy의 nginx 설정. hostname마다 `server` 블록 하나 |
| `infra/sites/start.ps1`, `start.sh` | 켜기 |
| `infra/sites/stop.ps1`, `stop.sh` | 끄기(데이터 남김) |
| `infra/sites/src/` | start가 두 branch에서 꺼낸 코드. `.gitignore` |
| `infra/sites/certs/` | start가 만든 인증서와 개인 key. `.gitignore` |

- 이름 정리

| | main 사이트 | dev 사이트 |
|---|---|---|
| compose project | `footprint-main` | `footprint-dev` |
| 컨테이너 | `footprint-main-postgres`, `-minio`, `-backend`, `-frontend` | `footprint-dev-postgres`, `-minio`, `-backend`, `-frontend` |
| volume | `footprint-main_postgres-data`, `footprint-main_minio-data` | `footprint-dev_postgres-data`, `footprint-dev_minio-data` |
| network | `footprint-main_default` | `footprint-dev_default` |

- proxy는 compose 이름 `footprint-sites-proxy`, 컨테이너 `footprint-sites-proxy`. 두 사이트의 network에 모두 붙음

## 켜는 법

- 필요한 것: Docker Desktop, git, 인터넷(GitHub에서 받기, 처음 빌드할 때 의존성과 기반 이미지), mkcert(아래)
- 저장소 맨 위에서
  ```powershell
  .\infra\sites\start.ps1
  ```
- macOS·Linux: `bash infra/sites/start.sh`
- 하는 일(번호는 스크립트의 단계와 같음)
  1. Docker와 git 확인
  2. `git fetch origin main dev`. 실패하면(인터넷 없음) 노란 글자로 알리고 이미 받아 둔 `origin/main`, `origin/dev`로 계속함
  3. `origin/main`, `origin/dev`의 `backend`, `frontend` 폴더를 `git archive`로 `infra/sites/src/main`, `src/dev`에 꺼냄. 지금 checkout 한 branch와 작업 중인 파일은 바꾸지 않음. 사이트마다 commit 한 줄을 보여 줌
  4. `infra/sites/certs/`에 인증서가 없으면 mkcert로 만듦(`main.footprint.localhost`, `dev.footprint.localhost` 두 이름)
  5. main 사이트, dev 사이트를 차례로 켬(`docker compose -p footprint-<branch> -f site.yml up -d --build --wait`). 백엔드 healthcheck가 통과할 때까지 기다림
  6. proxy를 새로 만들어 켬
  7. 두 주소, 사이트마다의 commit, 시작 계정을 보여 줌
- 처음 켤 때는 사이트마다 이미지 빌드 때문에 몇 분 걸림. 두 번째부터는 Docker의 빌드 cache로 바뀐 부분만 다시 만듦
- mkcert는 두 곳에서 찾음
  - Windows: 실행 묶음을 만들 때 받아 둔 `infra\bundle\cache\mkcert\mkcert-windows-amd64.exe` → PATH의 `mkcert`
  - macOS·Linux: PATH의 `mkcert` → `infra/bundle/cache/mkcert/mkcert-<운영체제>-<CPU>`
  - 둘 다 없으면 멈춤. `infra\bundle\make-bundle.ps1`을 한 번 실행하거나 mkcert를 설치함
  - 실행 묶음을 이 PC에서 켠 적이 있으면 로컬 인증 기관이 이미 들어 있어서 확인 창이 다시 뜨지 않음
- **새 코드를 반영하려면 start를 다시 실행함**. GitHub에서 merge 된 PR이 `main`, `dev`에 들어오면 다시 받아서 다시 빌드함. 켜 둔 채로 저절로 바뀌지 않음

## 끄는 법

- 저장소 맨 위에서
  ```powershell
  .\infra\sites\stop.ps1
  ```
- macOS·Linux: `bash infra/sites/stop.sh`
- proxy를 먼저 끄고 두 사이트를 끔(`down`). 컨테이너는 지우고 데이터(volume)는 남김. 다시 켜면 기록이 그대로 있음
- 한 사이트의 데이터까지 지우려면(되돌릴 수 없음) `infra/sites` 폴더에서
  ```powershell
  $env:SITE = "dev"; docker compose -p footprint-dev -f site.yml down -v
  ```
  - `site.yml`이 `SITE` 환경 변수를 읽어서, 끌 때도 넣어야 파일이 읽힘

## 확인하는 법

- 두 주소가 열리고 경고 화면이 뜨지 않음(Chrome 또는 Edge)
- 어느 사이트가 답했는지: F12 → Network → 아무 요청 → Response Headers의 `X-Footprint-Site`가 `main` 또는 `dev`
  - 터미널에서는 `curl.exe -sI https://dev.footprint.localhost`(macOS·Linux는 `curl -sI ...`)
- 데이터가 따로인지: dev 사이트에서 과제를 하나 만들고 main 사이트에 없는지 봄
- cookie가 주소마다 따로인지: F12 → Application → Cookies에서 `https://main.footprint.localhost`와 `https://dev.footprint.localhost`의 `refreshToken`이 따로 있음. 한 사이트에서 로그아웃해도 다른 사이트는 로그인이 남음
- 코드가 다른지: start 7단계가 보여 준 두 commit이 GitHub의 `main`, `dev` 최신 commit과 같음

## 왜 이렇게 했는지

- **같은 compose 파일을 project 이름만 바꿔 두 번 켬** — 사이트 정의가 한 곳이라 두 사이트가 어긋나지 않음. compose는 project 이름을 volume, network 앞에 붙여서 데이터가 저절로 따로 생김. 컨테이너 이름은 `SITE` 환경 변수로 나눔
- **`.localhost` 주소** — 브라우저(Chrome, Edge)가 `*.localhost`를 자기 PC(127.0.0.1)로 풀어 줘서 hosts 파일을 고치지 않음. 주소가 둘이라 cookie와 화면의 저장소(브라우저 기준 출처)도 사이트마다 따로임
- **포트 하나(443)에 주소 둘** — proxy가 요청의 hostname(`Host`)을 보고 사이트를 고름. 사이트마다 다른 포트를 쓰면 주소에 포트가 붙고 백엔드의 허용 주소(`CORS_ALLOWED_ORIGINS`)도 그만큼 늘어남
- **코드는 작업 폴더가 아니라 `origin/main`, `origin/dev`에서 꺼냄** — GitHub에 merge 된 것이 기준임. 작업 branch에서 고치던 파일, commit 하지 않은 변경이 사이트에 섞이지 않음. 지금 checkout 한 branch도 바꾸지 않음
- **사이트는 포트를 열지 않음** — 두 사이트가 같은 PC에서 뜨므로 열면 부딪힘. 그래서 비밀번호를 저장소의 개발용 기본값(`change-me`, `change-me-please`) 그대로 둠. 이 PC의 다른 프로그램도 사이트의 DB·MinIO에 접속하지 못함
- **proxy를 켤 때마다 새로 만듦** — nginx는 켜질 때 `footprint-main-frontend` 같은 이름의 주소를 한 번만 찾음. 사이트 컨테이너를 다시 만들면 주소가 바뀌어서, proxy를 그대로 두면 502가 남

## 제약

- 실행 묶음(`footprint-bundle`)과 같은 포트(443, 80)를 써서 **동시에 못 켬**. 한쪽을 끈 뒤 켬
- 개발용 `infra` compose(5432, 9000, 9001)와 개발용 백엔드(8080), 화면 개발 서버(5173)와는 함께 켜도 됨. 사이트는 호스트 포트를 열지 않음
- 백엔드의 8080을 열지 않음. Postman은 `https://dev.footprint.localhost/api/...`로 부름(화면 컨테이너의 nginx가 `/api`를 떼고 백엔드로 넘김)
- 구글 로그인은 Google Cloud의 OAuth Client "승인된 JavaScript 원본"에 두 주소를 더해야 함(관리자 작업). `.localhost` 하위 주소를 받아 주는지 확인하지 못함. 이메일·비밀번호 로그인은 상관없음
- `https://localhost`, `https://127.0.0.1`로 열면 어느 사이트로도 보내지 않고 404임(인증서에 없는 이름이라 경고 화면이 먼저 뜸)
- 두 사이트 모두 개발 설정이라 운영 설정(`prod`)은 확인하지 않음
- Firefox는 Windows의 신뢰 목록을 기본으로 쓰지 않아 경고가 뜰 수 있음. Chrome·Edge로 엶

## 자주 막히는 곳

- **proxy가 켜지지 않음, 포트 오류** — 443이나 80을 다른 프로그램이 씀. 실행 묶음(`docker ps`에 `footprint-bundle-proxy`)이나 IIS를 끈 뒤 start를 다시 실행함
- **proxy가 켜졌다가 바로 꺼짐, 로그에 `host not found in upstream`** — 사이트 하나가 켜지지 않은 것임. `docker ps`로 `footprint-main-frontend`, `footprint-dev-frontend`가 있는지 보고 start를 다시 실행함. 로그는 `docker logs footprint-sites-proxy --tail 100`
- **브라우저에 "연결이 비공개로 설정되어 있지 않습니다"** — 로컬 인증 기관이 신뢰 목록에 들어가지 않음. `infra/sites/certs/`를 지우고 start를 다시 실행해서 확인 창에서 "예"를 누름
- **502 Bad Gateway** — 사이트를 따로 다시 켜서 proxy가 이전 주소를 들고 있음. start를 다시 실행함(proxy를 새로 만듦)
- **사이트가 켜지지 않음(`--wait` 실패)** — `docker logs footprint-<branch>-backend --tail 100`. 그 branch의 코드가 빌드되지 않거나 migration이 실패한 것일 수 있음
- **"origin/dev 가 이 PC에 없습니다"** — 인터넷 없이 처음 켠 것임. 인터넷에 연결한 뒤 다시 실행함
