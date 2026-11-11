# backend — 발자국 데모 서버

Java 21, Spring Boot 3.5.16. `../footprint-study/example/backend`를 복사해서 시작함.

## 실행

- 개발하지 않고 데모만 할 때는 저장소 맨 위 `README.md`의 "한 번에 실행"(`docker compose up -d --build`)을 쓸 것
- 아래는 개발할 때의 실행 방법

1. 데이터베이스와 MinIO 켜기. `infra` 폴더에서 실행.

   ```powershell
   cd infra
   Copy-Item .env.example .env
   docker compose up -d
   ```

   - macOS는 둘째 줄이 `cp .env.example .env`. `.env`는 처음 한 번만 만들면 됨
   - 둘째 줄은 건너뛰어도 됨. `.env`가 없으면 `.env.example`과 같은 기본값으로 켜짐. 교수 이메일(`PROFESSOR_EMAILS`)을 넣을 때만 필요함
   - 학습 저장소의 `infra`와 포트(5432, 9000, 9001)가 같음. 학습용이 켜져 있으면 그 폴더에서 `docker compose down`을 먼저 실행할 것

2. 서버 켜기. IntelliJ에서 `backend` 폴더를 열고 `BackendApplication`의 `main`을 실행. 터미널에서는 1번에 이어 `backend` 폴더로 옮겨 실행.

   ```powershell
   cd ..\backend
   .\gradlew.bat bootRun
   ```

   - macOS는 `cd ../backend`, `./gradlew bootRun`
   - bootRun은 서버가 꺼질 때까지 터미널을 차지함. 3번은 새 터미널을 `backend` 폴더에서 열어 실행할 것
   - 서버가 켜질 때 Flyway가 테이블을 만들고, 빈 데이터베이스면 시작 데이터를 넣음. 다시 켜도 기록은 남음(아래 "데이터베이스 스키마")
   - `Found non-empty schema(s) "public" but no schema history table`로 켜지지 않으면 아래 "2026.11.12 전에 실행해 본 컴퓨터의 한 번 전환"을 할 것

3. 확인. 로그인 API를 부르면 `{"accessToken": "..."}`가 옴. 주소와 응답 모양은 `docs/api-draft.md`.

   - 주소는 `localhost`가 아니라 `127.0.0.1`로 씀. Windows에서는 `localhost`가 IPv6(`::1`)로 연결되다가 멈추는 일이 있었음(맨 위 `docker-compose.yml`의 backend 주석)

   ```powershell
   Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/auth/login -ContentType "application/json" -Body '{"email":"student01@example.com","password":"pass1234"}'
   ```

   macOS는 `curl -X POST 127.0.0.1:8080/auth/login -H "Content-Type: application/json" -d '{"email":"student01@example.com","password":"pass1234"}'`.

   받은 token으로 내 정보를 부르면 이름과 회원 유형이 옴. token 없이 부르면 401.

   ```powershell
   $token = (Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/auth/login -ContentType "application/json" -Body '{"email":"student01@example.com","password":"pass1234"}').accessToken
   Invoke-RestMethod -Uri http://127.0.0.1:8080/members/me -Headers @{ Authorization = "Bearer $token" }
   ```

   교수 token으로 과제를 만들면 `{"id": 2, "title": ...}`가 옴. 학생 token이면 403. 같은 창에서 이어서 실행할 것.

   ```powershell
   $token = (Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/auth/login -ContentType "application/json" -Body '{"email":"professor@example.com","password":"pass1234"}').accessToken
   Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/assignments -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body '{"title":"Test assignment","startDate":"2026-11-02","endDate":"2026-12-11","leaderEmails":["student02@example.com","student07@example.com"]}'
   ```

   예시 제목을 영어로 둔 이유: Windows PowerShell 5.1은 문자열 body를 UTF-8로 보내지 않아서 한글 제목이 깨지거나 400이 될 수 있음. 한글 제목은 화면이나 Postman으로 확인할 것.

   산출물 업로드와 다운로드는 Windows에 들어 있는 `curl.exe`로 확인. 시작 데이터의 1조 이서연(student02, 역할 백엔드)으로 올림.

   ```powershell
   $token = (Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/auth/login -ContentType "application/json" -Body '{"email":"student02@example.com","password":"pass1234"}').accessToken
   curl.exe -X POST http://127.0.0.1:8080/teams/1/deliverables -H "Authorization: Bearer $token" -F "file=@README.md"
   curl.exe http://127.0.0.1:8080/teams/1/deliverables -H "Authorization: Bearer $token"
   curl.exe -o download.txt http://127.0.0.1:8080/deliverables/1/download -H "Authorization: Bearer $token"
   ```

   - `-o download.txt`는 받은 내용을 그 이름으로 저장함. curl은 한글 파일 이름(RFC 5987 형식)을 풀지 못해서 이름을 직접 정함
   - 한글 이름이 깨지지 않는 것은 브라우저에서 직접 내려받아 확인할 것
   - macOS는 `curl.exe` 대신 `curl`

4. 파일 저장소 확인. MinIO 콘솔 http://localhost:9001 에 `infra/.env`(없으면 `infra/.env.example`의 기본값)의 `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`로 로그인. 서버가 시작할 때 만든 `footprint` 버킷이 보이면 성공.

## 빌드와 테스트

- `.\gradlew.bat build`(macOS는 `./gradlew build`)는 테스트도 함께 실행함
- 테스트가 실제 PostgreSQL과 MinIO에 접속하므로 **빌드 전에 `infra`의 `docker compose up -d`가 켜져 있어야 함.** 꺼져 있으면 테스트가 실패함
- 테스트는 `src/test/resources/application-test.yml`의 설정으로 돌아감. 데이터베이스는 `footprint_test` 스키마, 파일은 `footprint-test` 버킷을 씀
- 테스트의 Spring context가 뜰 때마다 `footprint_test` 스키마를 비우고 migration을 처음부터 다시 돌림(`TestFlywayConfig`). 그래서 migration이 Entity와 어긋나면 빌드가 깨짐
- **빌드해도 개발 데이터(`public` 스키마, `footprint` 버킷)는 지워지지 않음.** 빌드할 때마다 시험 버킷에 작은 시험 파일과 시작 데이터 파일이 쌓임
- 규칙 테스트는 시작 데이터로 실제 요청을 보냄: `SchemaRuleTests`(스키마와 부분 unique 인덱스, 시작 데이터 한 번만), `AccessRuleTests`(접근 범위), `TeamRuleTests`(과제·초대·합류·역할 지정), `RoleChangeRuleTests`(역할 변경·이력), `LeaderChangeRuleTests`(조장 교체·이력), `DeliverableRuleTests`(업로드), `DeliverableDeleteRuleTests`(삭제), `DeliverableOrphanFileTests`(주인 없는 파일 정리), `ContributionRuleTests`(기여 항목), `ConfirmationRuleTests`(완료확인), `EvaluationRuleTests`(동료평가), `EvaluationDraftRuleTests`(평가 임시 저장), `EvidenceRuleTests`(근거 패널), `RecordRuleTests`(기록 열람), `EvaluationResultRuleTests`(평가 결과), `PhaseRuleTests`(과제 단계), `NotificationRuleTests`(알림), `JoinPreviewRuleTests`(합류 전 미리보기)
- 테스트가 끝나면 데이터베이스 변경을 되돌림(`@Transactional`). 그래서 어떤 순서로 돌아도 시작 데이터가 그대로임
- 실행 로그의 `Using generated security password: ...`는 Spring Security가 기본으로 만드는 계정. 이 프로젝트는 쓰지 않으므로 무시할 것

## 데이터베이스 스키마 (Flyway)

테이블은 Hibernate가 아니라 `src/main/resources/db/migration`의 SQL 파일이 만듦(O-1, 2026.11.12). Hibernate는 `ddl-auto: validate`로 Entity와 테이블이 맞는지 확인만 함

- 서버가 켜질 때 Flyway가 아직 적용하지 않은 파일만 번호 순서로 실행하고, 실행한 파일을 `flyway_schema_history` 테이블에 적음. 그래서 **서버를 다시 켜도 기록이 지워지지 않음**
- 지금 파일: `V1__create_tables.sql`(엔티티 12개의 테이블), `V2__add_partial_unique_indexes.sql`(유효한 완료확인 하나, 조장 한 명)
- Entity와 테이블이 맞지 않으면 서버가 켜지지 않음. 예: `Schema-validation: missing column [privacy_consented_at] in table [member]`

### Entity에 칸을 더하거나 바꿀 때

1. Entity를 고침
2. 같은 변경을 SQL로 새 파일에 씀. 이름은 `V<다음 번호>__<영어 소문자 설명>.sql`(밑줄 두 개). 예: `V3__add_member_student_number.sql`에 `alter table member add column student_number varchar(255);`
   - 열 이름은 Java 필드 이름을 snake_case로 바꾼 것(`studentNumber` → `student_number`). 타입은 `V1__create_tables.sql`의 같은 Java 타입 줄을 따라 씀(`String` → `varchar(255)`, `LocalDateTime` → `timestamp(6)`, `Long` → `bigint`)
   - enum에 값을 더하면 `check (... in (...))` 제약도 새 파일에서 바꿔야 함. 안 바꾸면 새 값을 저장할 때 오류가 남
3. `.\gradlew.bat build`(macOS는 `./gradlew build`)로 확인. 테스트가 migration으로 스키마를 만들고 validate 하므로, 파일이 Entity와 어긋나면 여기서 실패함
4. **이미 commit 된 migration 파일은 고치지 않음.** 다른 컴퓨터는 그 파일을 이미 실행해서, 고치면 Flyway가 "checksum mismatch"로 서버를 켜지 않음. 틀렸으면 다음 번호의 새 파일로 고침
5. 두 사람이 같은 번호를 만들면 나중에 합치는 쪽이 번호를 올림. commit 전에 `db/migration`의 마지막 번호를 다시 봄

### 개발 데이터베이스를 처음부터 다시 만들기

시작 데이터 상태로 돌아가고 싶을 때나, commit 전 내 migration을 고쳐 다시 돌리고 싶을 때 씀. 개발 데이터(`public` 스키마)만 지우고, volume과 테스트 스키마는 그대로 둠

1. 서버를 끔
2. 저장소 어디서든 실행. `infra`로 켰든 맨 위 폴더로 켰든 컨테이너 이름이 같음

   ```powershell
   docker exec footprint-demo-postgres psql -U footprint -d footprint -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
   ```

   - macOS·Linux도 같은 명령임
   - `infra/.env`에서 `POSTGRES_USER`, `POSTGRES_DB`를 바꿨으면 `-U`, `-d` 뒤의 값을 그 값으로 바꿈
3. 서버를 켜면 Flyway가 테이블을 만들고 시작 데이터가 들어감
- MinIO의 `footprint` 버킷은 비우지 않음. 지운 데이터베이스가 가리키던 파일(시작 파일 3개, 올린 파일)은 주인 없이 남지만 화면에는 보이지 않음
- `docker compose down -v`는 쓰지 않음. MinIO까지 함께 지워짐

### 2026.11.12 전에 실행해 본 컴퓨터의 한 번 전환

O-1 전에는 서버가 켜질 때마다 Hibernate가 테이블을 새로 만들었음(`ddl-auto: create`). 그 테이블은 Flyway 이력이 없어서, 새 코드를 켜면 Flyway가 아무것도 바꾸지 않고 멈춤

```
Found non-empty schema(s) "public" but no schema history table. Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

- 바로 위 "개발 데이터베이스를 처음부터 다시 만들기"를 **한 번** 하면 됨. 그 전 데이터는 서버를 켤 때마다 지워지던 데이터라 지켜야 할 것이 없음
- 오류 문장이 권하는 `baselineOnMigrate`는 쓰지 않음. 이유는 `docs/worklog.md`의 2026.11.12 O-1 항목
- 테스트 스키마(`footprint_test`)는 빌드할 때마다 비우고 다시 만들므로 할 일이 없음
- 처음 clone 해서 `infra`를 처음 켜는 컴퓨터도 할 일이 없음

## 시작 계정

- 서버가 시작할 때 `config/DataInitializer`가 넣음. **회원이 한 명도 없는 빈 데이터베이스에서만** 넣음. 다시 켜도 다시 넣지 않음
- 시작 데이터 상태로 돌아가려면 위 "개발 데이터베이스를 처음부터 다시 만들기"
- 비밀번호는 모두 `pass1234`. 데이터베이스에는 BCrypt로 바꾼 값이 저장됨
- 12.11 데모의 기본 로그인은 구글(학교 계정)이고 시작 계정은 개발·백업용. 화면에서는 로그인 화면 아래 "시작 계정으로 로그인 (개발·백업용)"을 펼쳐서 씀
- 개인정보 고지(X-2): 임지아(student10)만 동의 전이고 나머지는 동의한 상태로 들어감. 임지아로 로그인하면 고지 화면이 먼저 나옴. 한 번 동의하면 서버를 다시 켜도 동의한 상태로 남음. 다시 시험하려면 개발 데이터베이스를 처음부터 다시 만들 것

| 이메일 | 이름 | 회원 유형 |
|---|---|---|
| professor@example.com | 이교수 | PROFESSOR |
| student01@example.com | 김민준 | STUDENT |
| student02@example.com | 이서연 | STUDENT |
| student03@example.com | 박도윤 | STUDENT |
| student04@example.com | 최서윤 | STUDENT |
| student05@example.com | 정하준 | STUDENT |
| student06@example.com | 강지우 | STUDENT |
| student07@example.com | 조은우 | STUDENT |
| student08@example.com | 윤하은 | STUDENT |
| student09@example.com | 장시우 | STUDENT |
| student10@example.com | 임지아 | STUDENT |

## 구글 로그인

화면의 구글 버튼 → `POST /auth/google` → 서버가 ID token을 검증하고 우리 token을 줌(`docs/api-draft.md`). 처음 로그인한 구글 계정은 회원으로 저장됨.

- 설정값(`application.yml`의 `google`):
  - `GOOGLE_CLIENT_ID` — Google Cloud의 OAuth Client ID. 기본값이 들어 있어 따로 넣지 않아도 됨. `frontend/.env`의 `VITE_GOOGLE_CLIENT_ID`와 같아야 함
  - `GOOGLE_ALLOWED_DOMAINS` — 받는 학교 도메인. 기본 `g.yju.ac.kr`
  - `PROFESSOR_EMAILS` — 교수로 저장하거나 교수로 올릴 이메일(쉼표로 구분, 대소문자 무시). 기본값은 비어 있어서 모두 학생으로 저장됨. 실제 이메일이라 저장소에 적지 않음
- 교수 이메일 넣는 법:
  - 내 컴퓨터에서 bootRun — 같은 PowerShell 창에서 먼저 `$env:PROFESSOR_EMAILS="교수이메일@g.yju.ac.kr"` 후 `.\gradlew.bat bootRun`. macOS는 `PROFESSOR_EMAILS=... ./gradlew bootRun`
  - docker compose — `infra/.env`에 `PROFESSOR_EMAILS=...` 한 줄을 넣을 것(`infra/.env.example` 참고)
- 회원 유형은 구글 로그인할 때마다 확인함(X-1)
  - 학생으로 먼저 로그인한 뒤 목록에 넣으면, 서버를 다시 켠 뒤(목록을 다시 읽음) 다음 구글 로그인 때 교수로 바뀜. 그 로그인에서 받은 token부터 교수임
  - 조원·조장 기록이 하나라도 있는 학생은 목록에 있어도 학생으로 남음. 오류는 나지 않음. 교수로 쓰려면 조에 들어가지 않은 계정을 써야 함
  - 목록에서 빼도 교수를 학생으로 내리지 않음. 이메일·비밀번호 로그인은 유형을 바꾸지 않음
- 로그인할 수 있는 구글 계정: Google Cloud의 동의 화면이 "테스트 중"이면 그곳의 "테스트 사용자"에 넣은 계정만 됨. 데모·개발에 쓸 학교 계정을 먼저 넣어 둘 것
- 서버는 구글 공개키를 받으려고 인터넷에 접속함. 인터넷이 없으면 구글 로그인은 500이 나고 시작 계정 로그인은 됨

## 시작 과제와 조

| 과제 | 기간 | 만든 교수 | 단계 |
|---|---|---|---|
| 발자국 데모 과제 | 2026-11-02 ~ 2026-12-11 | 이교수 | 기록 단계 |
| 발자국 평가 단계 과제 | 2026-09-01 ~ 2026-10-30 | 이교수 | 평가 단계 |

**발자국 데모 과제**

| 조 | 이름 (이메일) | 조장 | 역할명 |
|---|---|---|---|
| 1조 | 김민준 (student01) | 조장 | 기획 |
| 1조 | 이서연 (student02) | | 백엔드 |
| 1조 | 박도윤 (student03) | | 프론트엔드 |
| 1조 | 최서윤 (student04) | | 디자인 |
| 1조 | 정하준 (student05) | | 발표 |
| 2조 | 강지우 (student06) | 조장 | 기획 |
| 2조 | 조은우 (student07) | | 백엔드 |
| 2조 | 윤하은 (student08) | | 백엔드 |
| 2조 | 장시우 (student09) | | 프론트엔드 |
| 2조 | 임지아 (student10) | | 발표 |

**발자국 평가 단계 과제**

| 조 | 이름 (이메일) | 조장 | 역할명 |
|---|---|---|---|
| 1조 | 김민준 (student01) | 조장 | 기획 |
| 1조 | 이서연 (student02) | | 백엔드 |
| 1조 | 박도윤 (student03) | | 발표 |

- 동료평가는 평가 단계에서만 입력됨. 준비 없이 평가 화면을 보려면 평가 단계 과제로 들어갈 것
- 2조는 "같은 역할을 여러 조원이 맡을 수 있다"는 규칙을 보이려고 백엔드가 2명
- 시작 산출물(파일 본문도 MinIO에 있음): 데모 과제 1조 `기획서.txt`(김민준, 기획), `API 명세.txt`(이서연, 백엔드). 평가 단계 과제 1조 `최종 보고서.txt`(이서연, 백엔드)
- 시작 파일은 시작 데이터와 함께 빈 데이터베이스에서만 MinIO에 올림. 서버를 다시 켜도 더 쌓이지 않음. 개발 데이터베이스를 처음부터 다시 만들면 이전 시작 파일 3개가 주인 없이 남음
- 초대 코드: 데모 과제 1조 `demo-team-1`, 2조 `demo-team-2`, 평가 단계 과제 1조 `demo-evaluation-1`. 초대 링크는 `http://localhost:5173/join/demo-team-1` 모양. 조장이 다시 발급하면 무작위 값으로 바뀌고 이전 코드는 404가 됨
- 시작 학생 10명은 모두 이미 시작 과제의 조에 있음. 합류를 시험하려면 교수로 새 과제를 만들고, 그 과제의 조장이 발급한 링크로 다른 학생이 합류할 것
- 시작 기여 항목: 데모 과제 1조 `회의록 정리`(박도윤, 보고 전), 평가 단계 과제 1조 `자료 취합`(이서연, 보고됨)
- 시작 완료확인: 평가 단계 과제 1조의 `최종 보고서.txt`(김민준이 확인), `자료 취합`(박도윤이 확인). 평가 단계에서는 새로 확인할 수 없어서 넣어 둠
- 시작 알림: 위 기록이 실제 요청으로 생겼을 때와 같은 알림을 넣어 둠. 박도윤은 `회의록 정리` 담당 지정, 김민준과 박도윤은 `자료 취합` 완료 보고, 평가 단계 과제 1조 3명은 평가 시작을 받음. 왼쪽 아래 종 모양 버튼에 점이 찍혀 있으면 안 읽은 알림이 있다는 뜻임
- 동료평가는 시작 데이터에 없음. 아무 학생으로 로그인해 `http://localhost:5173/teams/1/evaluation`(2조는 `/teams/2/evaluation`)에서 제출해 볼 것. 한 학생은 한 번만 제출할 수 있고, 서버를 다시 켜도 제출한 평가는 남음. 다시 제출해 보려면 개발 데이터베이스를 처음부터 다시 만들 것
