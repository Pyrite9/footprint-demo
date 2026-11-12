# 실행 안내

- 발자국을 켜는 방법 세 가지와 켜기 전에 확인할 것, 비밀값을 모은 문서
- 맨 위 `README.md`의 "빠른 시작"은 이 문서의 "한 번에 실행"을 줄인 것임

## 실행 방법 세 가지

| | 누가 | 무엇으로 | 안내 |
|---|---|---|---|
| 제출용 실행 묶음 | 담당 교수, 강단 PC, 시연 | USB의 묶음 폴더와 Docker Desktop. 저장소·인터넷 필요 없음 | 아래 "제출용 실행 묶음" |
| 한 번에 실행 | 조원이 저장소 전체를 돌려 볼 때 | 저장소와 Docker Desktop. 이미지를 소스로 빌드 | 아래 "한 번에 실행" |
| 개발할 때 | 코드를 고칠 때 | DB·MinIO만 Docker, 백엔드·화면은 내 컴퓨터 | 아래 "개발할 때" |

## 제출용 실행 묶음

- 12.11 발표와 12.15 PBL 제출에 쓰는 폴더 하나짜리 묶음(O-3). 이미지가 tar로 들어 있어 받는 PC에서 빌드와 인터넷 없이 켜짐
- 받는 사람: 묶음 폴더를 PC에 복사 → `start.bat` 더블클릭(macOS·Linux `bash start.sh`) → http://localhost:5173. 안내는 묶음 맨 위 `README.md`(원본 `infra/bundle/template/README.md`)
- 만드는 사람(관리자): 저장소 맨 위에서 `.\infra\bundle\make-bundle.ps1` → `infra/bundle/out/footprint-demo/`를 USB에 복사 → `SHA256SUMS.txt`로 확인. 자세히는 `docs/bundle.md`
- 개발 환경과 이름이 달라서(`footprint-bundle-*`) 개발 데이터를 건드리지 않음. 포트 5173, 8080은 같아서 개발용 백엔드·화면 서버는 끈 채로 켬
- 기본은 시연용(demo, 시작 계정 로그인). 묶음 폴더의 `.env`에서 `FOOTPRINT_MODE=prod`로 바꾸면 운영 설정(구글 로그인만)

## 한 번에 실행 (저장소와 Docker만 있으면 됨)

- Java, Node.js 없이 Docker Desktop만으로 백엔드, 화면, PostgreSQL, MinIO, 시작 데이터를 한 번에 켜는 방법. 이미지를 저장소의 소스로 빌드함

```powershell
docker compose up -d --build
```

- 처음에는 이미지를 만드느라 몇 분 걸림. 다음부터는 `docker compose up -d`만 쓰면 됨(코드를 바꿨으면 `--build`를 붙일 것)
- 화면: http://localhost:5173 — 주소는 `127.0.0.1`이 아니라 `localhost`로 열 것. 백엔드와 구글 로그인이 기본으로 이 주소만 허용함(바꾸려면 `infra/.env`의 `CORS_ALLOWED_ORIGINS`)
- 화면은 백엔드를 같은 주소의 `/api`로 부름. 화면 컨테이너의 nginx가 `/api`를 떼고 백엔드 컨테이너로 넘김(`frontend/nginx.conf`)
- 백엔드: http://127.0.0.1:8080 — Postman과 상태 확인용. 화면은 이 주소를 직접 부르지 않음
- MinIO 콘솔: http://localhost:9001 — 계정은 `infra/.env`(없으면 `infra/.env.example`의 기본값)
- 시작 계정과 시작 데이터: `backend/README.md`의 "시작 계정"부터. 비밀번호는 모두 `pass1234`
- 올린 산출물과 제출한 평가는 백엔드를 다시 켜거나(`docker compose restart backend`) 꺼도 남음. `gradlew build`로도 지워지지 않음. 시작 데이터 상태로 돌아가려면 `backend/README.md`의 "개발 데이터베이스를 처음부터 다시 만들기"
- 2026.11.12 전에 이 방법으로 켜 본 컴퓨터는 백엔드가 `Found non-empty schema(s) "public"`로 켜지지 않음. 같은 곳의 "한 번 전환"을 먼저 할 것
- 끄기

```powershell
docker compose down
```

### 켜기 전에 확인할 것

- `docker compose version`이 v2.24 이상일 것. 맨 위 `docker-compose.yml`이 쓰는 `include`와 `env_file`의 `required`가 그 버전부터 됨
- 학습 저장소(`footprint-study`)의 infra가 켜져 있으면 먼저 끌 것. 포트(5432, 9000, 9001)가 같음
- 개발 중에 켠 백엔드(8080)와 화면 개발 서버(5173)도 끌 것. 같은 포트를 씀
- `infra/.env`는 없어도 됨. 없으면 `infra/.env.example`과 같은 기본값을 씀

### 비밀값

- 저장소에 들어 있는 비밀값은 모두 개발용 기본값(`change-me` 등). 제출용 실행 묶음은 처음 켤 때 무작위 값을 만들어 쓰므로 이 값을 쓰지 않음
- 접속 가능한 주소에 올릴 때는 `JWT_SECRET`, `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`를 환경 변수로 반드시 바꿀 것
- 운영 설정(`SPRING_PROFILES_ACTIVE=prod`)으로 켜면 셋 중 하나라도 공개된 기본값이거나 비었을 때, 또는 `PROFESSOR_EMAILS`가 비었을 때 백엔드가 켜지지 않음. 로그에는 바꿔야 할 이름만 나오고 값은 나오지 않음. 켜는 법과 환경 변수 표는 `backend/README.md`의 "운영 설정으로 켜기"
- 환경 변수 없이 켜면 개발 설정이라 검사하지 않음. 조원은 할 일이 없음

## 개발할 때

- DB와 MinIO만 Docker로 켜고, 백엔드와 화면은 내 컴퓨터에서 실행함
  - DB·MinIO: `infra` 폴더에서 `docker compose up -d`
  - 백엔드: `backend/README.md`
  - 화면: `frontend/README.md`. 백엔드 없이 가짜 API로 화면만 돌리는 방법도 여기 있음
- 두 방법은 같은 이름(`footprint-demo`)이라 같은 DB·MinIO 컨테이너를 씀
  - 한 번에 실행한 뒤 개발로 돌아갈 때는 맨 위 폴더에서 `docker compose down`을 하고 `infra`에서 다시 켤 것
  - 반대로 개발 중에 한 번에 실행으로 바꿀 때는 `infra`에서 `docker compose down`을 하고 맨 위 폴더에서 켤 것

## 서버를 다시 켜도 기록이 남음 (O-1, 2026.11.12)

- 테이블은 Flyway가 `backend/src/main/resources/db/migration`의 SQL 파일로 만들고, Hibernate는 Entity와 맞는지 확인만 함(`ddl-auto: validate`)
- 시작 데이터는 빈 데이터베이스에서 한 번만 들어감. 실행해 보는 사람은 준비 작업 없이 바로 흐름을 볼 수 있음
- 시작 데이터 상태로 돌아가는 방법과, 2026.11.12 전에 실행해 본 컴퓨터의 한 번 전환은 `backend/README.md`의 "데이터베이스 스키마"
