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
- **빌드해도 개발 데이터(`public` 스키마, `footprint` 버킷)는 지워지지 않음.** 빌드할 때마다 시험 버킷에 작은 시험 파일과 시작 데이터 파일이 쌓임
- 규칙 테스트는 시작 데이터로 실제 요청을 보냄: `AccessRuleTests`(접근 범위), `TeamRuleTests`(과제·초대·합류·역할 지정), `RoleChangeRuleTests`(역할 변경·이력), `LeaderChangeRuleTests`(조장 교체·이력), `DeliverableRuleTests`(업로드), `DeliverableDeleteRuleTests`(삭제), `DeliverableOrphanFileTests`(주인 없는 파일 정리), `ContributionRuleTests`(기여 항목), `ConfirmationRuleTests`(완료확인), `EvaluationRuleTests`(동료평가), `EvaluationDraftRuleTests`(평가 임시 저장), `EvidenceRuleTests`(근거 패널), `RecordRuleTests`(기록 열람), `EvaluationResultRuleTests`(평가 결과), `PhaseRuleTests`(과제 단계), `NotificationRuleTests`(알림)
- 테스트가 끝나면 데이터베이스 변경을 되돌림(`@Transactional`). 그래서 어떤 순서로 돌아도 시작 데이터가 그대로임
- 실행 로그의 `Using generated security password: ...`는 Spring Security가 기본으로 만드는 계정. 이 프로젝트는 쓰지 않으므로 무시할 것

## 시작 계정

- 서버가 시작할 때 `config/DataInitializer`가 넣음
- 비밀번호는 모두 `pass1234`. 데이터베이스에는 BCrypt로 바꾼 값이 저장됨
- 12.11 데모의 기본 로그인은 구글(학교 계정)이고 시작 계정은 개발·백업용. 화면에서는 로그인 화면 아래 "시작 계정으로 로그인 (개발·백업용)"을 펼쳐서 씀

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
  - `PROFESSOR_EMAILS` — 교수로 저장할 이메일(쉼표로 구분). 기본값은 비어 있어서 모두 학생으로 저장됨. 실제 이메일이라 저장소에 적지 않음
- 교수 이메일 넣는 법:
  - 내 컴퓨터에서 bootRun — 같은 PowerShell 창에서 먼저 `$env:PROFESSOR_EMAILS="교수이메일@g.yju.ac.kr"` 후 `.\gradlew.bat bootRun`. macOS는 `PROFESSOR_EMAILS=... ./gradlew bootRun`
  - docker compose — `infra/.env`에 `PROFESSOR_EMAILS=...` 한 줄을 넣을 것(`infra/.env.example` 참고)
- 회원 유형은 처음 로그인할 때 한 번만 정해짐. 학생으로 먼저 로그인한 뒤 목록에 넣어도 바뀌지 않음. 서버를 다시 켜면(데이터베이스가 새로 만들어지면) 다시 정해짐
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
- MinIO는 서버를 다시 켜도 비워지지 않아서, 서버를 켤 때마다 같은 내용의 시작 파일이 새 key로 하나씩 더 쌓임. 데이터베이스는 매번 새로 만들어지므로 목록에는 3건만 보임
- 초대 코드: 데모 과제 1조 `demo-team-1`, 2조 `demo-team-2`, 평가 단계 과제 1조 `demo-evaluation-1`. 초대 링크는 `http://localhost:5173/join/demo-team-1` 모양. 조장이 다시 발급하면 무작위 값으로 바뀌고 이전 코드는 404가 됨
- 시작 학생 10명은 모두 이미 시작 과제의 조에 있음. 합류를 시험하려면 교수로 새 과제를 만들고, 그 과제의 조장이 발급한 링크로 다른 학생이 합류할 것
- 시작 기여 항목: 데모 과제 1조 `회의록 정리`(박도윤, 보고 전), 평가 단계 과제 1조 `자료 취합`(이서연, 보고됨)
- 시작 완료확인: 평가 단계 과제 1조의 `최종 보고서.txt`(김민준이 확인), `자료 취합`(박도윤이 확인). 평가 단계에서는 새로 확인할 수 없어서 넣어 둠
- 시작 알림: 위 기록이 실제 요청으로 생겼을 때와 같은 알림을 넣어 둠. 박도윤은 `회의록 정리` 담당 지정, 김민준과 박도윤은 `자료 취합` 완료 보고, 평가 단계 과제 1조 3명은 평가 시작을 받음. 왼쪽 아래 종 모양 버튼에 점이 찍혀 있으면 안 읽은 알림이 있다는 뜻임
- 동료평가는 시작 데이터에 없음. 아무 학생으로 로그인해 `http://localhost:5173/teams/1/evaluation`(2조는 `/teams/2/evaluation`)에서 제출해 볼 것. 한 학생은 한 번만 제출할 수 있고, 서버를 다시 켜면 데이터베이스가 새로 만들어져 다시 제출할 수 있음
