# 제출용 실행 묶음 (O-3)

- 12.11 학기 발표와 12.15 PBL 결과물 제출에 쓰는 **로컬 실행 묶음**을 만들고 USB에 담는 방법
- 제출 형태는 "접속 가능한 주소"가 아니라 "로컬 실행 묶음", 묶음은 Docker compose 기반(2026.11.12 관리자 결정). 서버, 도메인, Google Cloud 설정은 이 단위에 없음
- 받는 사람(담당 교수, 강단 PC)이 읽는 안내는 묶음 맨 위의 `README.md`. 원본은 `infra/bundle/template/README.md`

## 묶음 하나에 든 것

- 묶음 = 폴더 하나(`footprint-demo/`). 받는 PC에서 인터넷과 빌드 없이 켜짐

| 경로 | 무엇 | 원본 |
|---|---|---|
| `README.md` | 받는 사람용 안내 | `infra/bundle/template/README.md` |
| `docker-compose.yml` | 컨테이너 4개. `build` 대신 `image`, `pull_policy: never` | `infra/bundle/template/docker-compose.yml`(`__VERSION__`을 바꿔 넣음) |
| `env.template` | `.env`의 원본. 비밀값 세 줄이 비어 있음 | `infra/bundle/template/env.template` |
| `start`·`stop`·`reset` `.bat`/`.ps1`/`.sh` | 켜기, 끄기(데이터 남김), 초기화(데이터 지움, `delete` 입력 필요) | `infra/bundle/template/` |
| `backup/` | O-4 백업·복구 스크립트 4개와 `count-rows.sql` | `infra/backup/` |
| `images/*.tar`, `images/images.txt` | `docker save` 한 이미지 5개와 목록 | `make-bundle.ps1`이 만듦 |
| `VERSION.txt` | 묶음 버전, 만든 시각, commit, 이미지 이름 | `make-bundle.ps1`이 만듦 |
| `SHA256SUMS.txt` | 파일마다의 sha256. `sha256sum -c` 모양 | `make-bundle.ps1`이 만듦 |

- 이미지 5개(모두 `linux/amd64`)
  - `footprint-backend:<버전>`
  - `footprint-frontend:<버전>-demo`(이메일·비밀번호 로그인 폼 있음), `footprint-frontend:<버전>-prod`(폼 없음)
  - `postgres:17`, `pgsty/minio:RELEASE.2026-08-04T00-00-00Z`(개발 환경과 같은 이미지)
- `<버전>`은 만든 날짜(`yyyy.MM.dd`). `-Version`으로 바꿀 수 있음

## 개발 환경과 다른 이름

- 같은 PC에서 개발 환경을 켠 채 묶음을 켤 수 있게 이름을 모두 다르게 둠

| | 개발 환경 | 묶음 |
|---|---|---|
| compose 이름 | `footprint-demo` | `footprint-bundle` |
| 컨테이너 | `footprint-demo-postgres` 등 | `footprint-bundle-postgres`, `-minio`, `-backend`, `-frontend` |
| volume | `footprint-demo_postgres-data` 등 | `footprint-bundle_postgres-data`, `footprint-bundle_minio-data` |
| 호스트 포트 | 5432, 9000, 9001(DB·MinIO), 8080, 5173 | 8080, 5173만 |

- 묶음의 PostgreSQL·MinIO는 호스트 포트를 열지 않아서 개발 DB·MinIO와 부딪히지 않음
- 8080과 5173은 같음. 백엔드의 허용 주소(`cors.allowed-origins`)와 구글 로그인이 `http://localhost:5173`을 기준으로 함. **개발용 백엔드(8080)와 화면(5173)은 끈 채로 묶음을 켬**
- 화면 이미지에는 백엔드 주소가 아니라 `/api`가 들어 있음. 화면 컨테이너의 nginx가 `/api`를 떼고 compose 안의 `backend` 서비스로 넘김(`frontend/nginx.conf`). 묶음의 8080은 상태 확인과 Postman용임

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
- 하는 일: 백엔드 이미지 1개와 화면 이미지 2개를 새로 빌드 → `infra/bundle/out/footprint-demo/`를 새로 만듦(이전 결과는 지움) → 템플릿과 백업 스크립트 복사 → 이미지 5개를 `docker save` → `VERSION.txt`, `SHA256SUMS.txt`
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

- 개발 환경(`footprint-demo-postgres`, `footprint-demo-minio`)은 켠 채로 둬도 됨. 개발용 백엔드(8080)와 화면(5173)만 끔
- 이미지 load를 시험하려면 묶음 tag 이미지만 지우고 start를 돌림. `postgres:17`, `pgsty/minio:...`는 개발 컨테이너가 쓰고 있어서 지우지 못함
  ```powershell
  docker rmi footprint-backend:<버전> footprint-frontend:<버전>-demo footprint-frontend:<버전>-prod
  ```
- load한 이미지의 ID는 빌드한 이미지의 ID와 다르게 나왔음(2026.11.12 확인. `docker save --platform`이 한 플랫폼만 담는 탓으로 봄). start는 이름(tag)만 보므로 동작에는 차이가 없음

## 4절 "배포할 때 반드시 할 것"을 묶음에서 처리한 방법

- **비밀값** — 첫 start가 `JWT_SECRET`(32바이트), `POSTGRES_PASSWORD`·`MINIO_ROOT_PASSWORD`(24바이트)를 암호용 난수로 만들어 16진수로 묶음의 `.env`에 넣음. 저장소의 공개 기본값을 쓰지 않음. 값은 화면에 찍지 않음
  - PostgreSQL 비밀번호는 volume을 처음 만들 때 고정됨. `.env`를 지우고 새로 만들면 백엔드가 `password authentication failed`로 켜지지 않음
  - 그래서 start는 "volume은 있는데 `.env`가 없음"이면 새 `.env`를 만들지 않고 멈춤. `.env`를 되살리거나 reset 뒤 켜라고 안내함
- **포트** — 화면 `127.0.0.1:5173`, 백엔드 `127.0.0.1:8080`만 엶. 화면은 8080을 쓰지 않고 화면 컨테이너를 거쳐 백엔드를 부름. PostgreSQL·MinIO는 `ports`가 없어 호스트에서 접속되지 않음. 같은 와이파이의 다른 PC에서도 접속되지 않음
- **시작 데이터와 로그인 방식** — `.env`의 `FOOTPRINT_MODE`
  - `demo`(기본): Spring profile `demo`(설정 파일 없음 → 개발 설정과 같음). 시작 데이터, 이메일·비밀번호 로그인. `ProductionSettingsChecker`는 `prod`만 보므로 끼어들지 않음. 화면은 `-demo` 이미지
  - `prod`: Spring profile `prod`. 시작 데이터 없음, `POST /auth/login` 404, 구글 로그인만. `PROFESSOR_EMAILS`가 비면 start가 미리 멈춤. 화면은 `-prod` 이미지(폼 없음)
  - 방식을 바꿀 때는 reset으로 데이터를 지운 뒤 켬(demo의 시작 계정이 prod 데이터베이스에 남지 않게)
- **스키마** — 그대로 Flyway migration과 `ddl-auto: validate`(O-1). 묶음을 다시 켜도 데이터가 남음
- **CORS** — 기본값(`http://localhost:5173`) 그대로. 묶음은 이 주소로만 열어서 바꿀 일이 없음. 화면의 요청은 같은 주소의 `/api`라 CORS를 거치지 않지만, 이 값은 refresh token의 `Origin` 검사에도 쓰임
- **백업** — O-4 스크립트를 묶음의 `backup/`에 넣음. 컨테이너 이름 옵션으로 묶음 컨테이너를 가리킴(받는 사람용 `README.md` "백업과 복구")
  - 하는 것: 묶음 안에서 수동 백업·복구, 개인정보와 보관 기간 안내
  - 하지 않는 것: 자동 예약(강단 PC는 시연 동안만 켜고, 받는 PC의 작업 스케줄러를 바꾸는 일이라 받는 사람이 정함), 다른 곳 보관과 암호화(받는 사람의 저장 장치 선택. 안내만 함), 기간 지난 묶음 자동 삭제(보관 기간이 X-2 초안이라 정해지지 않음. 수동 삭제 안내)
- **구글 로그인 동의 화면** — Google Cloud 콘솔 작업이라 하지 않음. 동의 화면이 "테스트 중"이면 테스트 사용자만 로그인됨. 받는 사람 안내에 "인터넷 필요, 테스트 사용자만"을 적음. 관리자가 할 일로 `TODO.md` 4절에 남김

## 알려진 한계

- 이미지는 amd64만 넣음. Apple Silicon Mac에서는 Docker Desktop의 변환(Rosetta·QEMU)으로 돌아 느림. arm64 이미지를 함께 넣으면 묶음이 두 배가 되고 빌드에 변환이 필요해서 넣지 않음
- 포트 5173, 8080을 바꿀 수 없음(위 "개발 환경과 다른 이름")
- 받는 PC에 같은 이름의 `postgres:17`이 이미 있으면 그 이미지를 씀(start는 없는 이미지만 load). 같은 17 판이라 동작은 같음
