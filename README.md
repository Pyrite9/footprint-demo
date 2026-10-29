# 발자국 데모

- 조별과제 과정기록 & 평가지원 서비스 "발자국"의 12.11 데모
- 흐름: 과제 생성 → 초대 링크로 합류 → 역할 배정 → 산출물 업로드·다운로드 → 동료평가 제출
- 서비스 원칙: **판단은 사람, 시스템은 근거만**
  - 점수, 평균, 순위, 진척률을 계산하지 않음. 기록을 있는 그대로 보여 줌
  - 평가 화면에 "업로드 횟수는 기여도가 아닙니다", "점수는 시스템이 계산하지 않습니다"를 고정

## 지금 상태 (2026.10.29)

- 데모 흐름 전체가 동작함
- 새로 clone 한 폴더에서 `docker compose up -d --build` 한 번으로 처음부터 끝까지 실행하는 것까지 확인

1단계로 만든 기능 8개.

| 기능 | 상태 |
|---|---|
| M-1 회원 등록 | 처음 구글 로그인할 때 회원을 저장하는 것으로 대신함 |
| M-2 로그인·로그아웃 | 학교 구글 계정(g.yju.ac.kr) 로그인. 이메일·비밀번호 로그인은 개발·백업용 |
| A-1 과제 생성 (조장 지정) | 됨 |
| A-2 과제 목록·상세 조회 | 됨 |
| T-1 조원 초대 (초대 링크 발급, 링크로 합류) | 됨 |
| T-2 역할 배정 | 지정까지. 변경은 2단계 |
| R-1 산출물 업로드·열람 | 업로드·목록·다운로드. 삭제는 2단계 |
| E-2 동료평가 입력 | 입력·제출. 임시 저장은 2단계 |

**일부러 만들지 않은 것** (12.12 이후로 미룸)

- 교수의 평가 결과 열람 화면, 기여 항목, 완료확인, 근거 패널, 진척 시각화
- 과제 단계 전환, 역할 변경, 산출물 삭제, 평가 임시 저장, 조장 교체
- 메일 발송, LLM 기능
- 앞으로 만들 것과 미룬 이유는 `TODO.md`

**데모라 매번 초기화함**

- 서버가 켜질 때마다 데이터베이스를 새로 만들고 시작 데이터를 넣음(`ddl-auto: create`)
- 실행해 보는 사람이 준비 작업 없이 바로 흐름을 볼 수 있게 한 선택

## 기술 스택

| 쪽 | 쓴 것 |
|---|---|
| 백엔드 | Java 21, Spring Boot 3.5.16, Spring Data JPA, Spring Security(JWT, jjwt 0.13.0), Gradle 8.14.3 |
| 데이터베이스 | PostgreSQL 17 |
| 파일 저장 | MinIO(S3 호환). 코드는 AWS SDK v2의 S3 API로만 접근 |
| 로그인 | Google ID token 검증(google-api-client), 도메인 제한 |
| 프론트엔드 | Node.js 24, React 19, Vite 8, React Router 7, JavaScript(TypeScript 아님) |
| 가짜 API | json-server 0.17.4. 백엔드 없이 화면만 돌릴 때 씀 |
| 한 번에 실행 | Docker Compose(백엔드, nginx, PostgreSQL, MinIO) |

**영리한 코드보다 따라 읽을 수 있는 코드**

- 팀원 6명 전원이 Spring Boot·React 초심자라서 둔 기준
- 안 쓰는 것: MapStruct, QueryDSL, 상태 관리 라이브러리, 공통 부모 클래스, 공통 응답 감싸기 클래스, AOP 권한 검사
- 권한 검사는 `TeamAccessChecker`를 Service에서 직접 불러 눈에 보이게 함

## 폴더 구조

```
backend/    Spring Boot. 패키지 com.footprint.backend (Java 파일 58개)
  controller/  요청을 받고 Entity를 응답 DTO로 바꿈 (XxxResponse.from)
  service/     업무 규칙과 권한 검사. Entity를 돌려줌
  repository/  JPA
  entity/      회원, 과제, 조, 조 참여, 산출물, 평가 (6개)
  dto/         record. 요청 하나에 DTO 하나
  exception/   GlobalExceptionHandler 한 곳에서 처리. 응답은 ApiErrorResponse { code, message }
  security/    JWT 발급·검증 필터, 구글 ID token 검증
  config/      SecurityFilterChain, CORS, S3, 시작 데이터(DataInitializer)
frontend/   React (파일 36개)
  src/pages/       데이터를 불러오고 state를 가짐
  src/components/  props로 받아 그림
  src/api/         fetch는 여기에만. 공통 함수 client.js를 거침
  src/constants/   동료평가 6개 항목 문장, 회원 유형
  mock/            가짜 API 경로와 middleware. 응답 모양을 실제 API와 똑같이 맞춤
infra/      docker-compose.yml(PostgreSQL, MinIO), .env.example
docs/       구조 그림, API 명세, 작업 기록
```

## 한 번에 실행 (Docker만 있으면 됨)

Java, Node.js 없이 Docker Desktop만으로 백엔드, 화면, PostgreSQL, MinIO, 시작 데이터를 한 번에 켜는 방법.

```powershell
docker compose up -d --build
```

- 처음에는 이미지를 만드느라 몇 분 걸림. 다음부터는 `docker compose up -d`만 쓰면 됨(코드를 바꿨으면 `--build`를 붙일 것)
- 화면: http://localhost:5173 — 주소는 `127.0.0.1`이 아니라 `localhost`로 열 것. 백엔드가 이 주소만 허용함
- 백엔드: http://127.0.0.1:8080
- MinIO 콘솔: http://localhost:9001 — 계정은 `infra/.env.example`
- 시작 계정과 시작 데이터: `backend/README.md`의 "시작 계정"부터. 비밀번호는 모두 `pass1234`
- 올린 산출물과 제출한 평가는 `docker compose restart backend`로 지워짐

끄기.

```powershell
docker compose down
```

### 켜기 전에 확인할 것

- `docker compose version`이 v2.24 이상일 것. 맨 위 `docker-compose.yml`이 쓰는 `include`와 `env_file`의 `required`가 그 버전부터 됨
- 학습 저장소(`footprint-study`)의 infra가 켜져 있으면 먼저 끌 것. 포트(5432, 9000, 9001)가 같음
- 개발 중에 켠 백엔드(8080)와 화면 개발 서버(5173)도 끌 것. 같은 포트를 씀
- `infra/.env`는 없어도 됨. 없으면 `infra/.env.example`과 같은 기본값을 씀

### 비밀값

- 저장소에 들어 있는 비밀값은 모두 개발용 기본값(`change-me` 등)
- 접속 가능한 주소에 올릴 때는 `JWT_SECRET`, `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`를 환경 변수로 반드시 바꿀 것

## 개발할 때

DB와 MinIO만 Docker로 켜고, 백엔드와 화면은 내 컴퓨터에서 실행함.

- DB·MinIO: `infra` 폴더에서 `docker compose up -d`
- 백엔드: `backend/README.md`
- 화면: `frontend/README.md`

두 방법은 같은 이름(`footprint-demo`)이라 같은 DB·MinIO 컨테이너를 씀. 한 번에 실행한 뒤 개발로 돌아갈 때는 맨 위 폴더에서 `docker compose down`을 하고 `infra`에서 다시 켤 것.

## 문서

- `TODO.md` — 앞으로 만들 것. 미룬 단위 업무, 2단계 기능, 아직 정하지 않은 것, 알려진 한계
- `docs/architecture.md` — 구조 그림 네 장. 전체 구조, JWT 흐름, 프론트엔드 구조, 백엔드 구조와 데이터 구조. **코드를 처음 읽는다면 여기부터**
- `docs/api-draft.md` — API 명세. 코드보다 이 문서를 먼저 씀
- `docs/postman/` — API를 데모 흐름 순서로 모은 Postman 모음과 쓰는 방법
- `docs/worklog.md` — 작업 기록. 단위 업무마다 결정과 이유, 초심자가 막힐 곳, 채운 가정
- `docs/laptop-setup.md` — 다른 컴퓨터(Arch Linux 노트북)에 이 저장소를 올려 작업하는 설정 지시서
- `CLAUDE.md` — 이 저장소의 코드 생성 규칙. 스택, 계층, 명명, 금지 목록을 AI 도구와 사람이 같이 지키는 기준

## 라이선스

MIT. `LICENSE` 참고.
