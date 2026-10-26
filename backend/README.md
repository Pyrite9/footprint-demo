# backend — 발자국 시연본 서버

Java 21, Spring Boot 3.5.16. `../footprint-study/example/backend`를 복사해서 시작했습니다.

## 실행

개발하지 않고 시연만 할 때는 저장소 맨 위 `README.md`의 "한 번에 실행"(`docker compose up -d --build`)을 씁니다. 아래는 개발할 때의 실행 방법입니다.

1. 데이터베이스와 MinIO를 켭니다. `infra` 폴더에서 실행합니다.

   ```powershell
   cd infra
   Copy-Item .env.example .env
   docker compose up -d
   ```

   macOS는 둘째 줄이 `cp .env.example .env`입니다. `.env`는 처음 한 번만 만들면 됩니다.
   학습 저장소의 `infra`와 포트(5432, 9000, 9001)가 같습니다. 학습용이 켜져 있으면 그 폴더에서 `docker compose down`을 먼저 실행합니다.

2. IntelliJ에서 `backend` 폴더를 열고 `BackendApplication`의 `main`을 실행합니다. 터미널에서는 1번에 이어 `backend` 폴더로 옮겨 실행합니다.

   ```powershell
   cd ..\backend
   .\gradlew.bat bootRun
   ```

   macOS는 `cd ../backend`, `./gradlew bootRun`입니다. bootRun은 서버가 꺼질 때까지 터미널을 차지합니다. 3번은 새 터미널을 `backend` 폴더에서 열어 실행합니다.

3. 확인: 로그인 API를 부르면 `{"accessToken": "..."}`가 옵니다. 주소와 응답 모양은 `docs/api-draft.md`에 있습니다.

   ```powershell
   Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType "application/json" -Body '{"email":"student01@example.com","password":"pass1234"}'
   ```

   macOS는 `curl -X POST localhost:8080/auth/login -H "Content-Type: application/json" -d '{"email":"student01@example.com","password":"pass1234"}'`입니다.

   받은 token으로 내 정보를 부르면 이름과 회원 유형이 옵니다. token 없이 부르면 401입니다.

   ```powershell
   $token = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType "application/json" -Body '{"email":"student01@example.com","password":"pass1234"}').accessToken
   Invoke-RestMethod -Uri http://localhost:8080/members/me -Headers @{ Authorization = "Bearer $token" }
   ```

   교수 token으로 과제를 만들면 `{"id": 2, "title": ...}`가 옵니다. 학생 token이면 403입니다. 같은 창에서 이어서 실행합니다.

   ```powershell
   $token = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType "application/json" -Body '{"email":"professor@example.com","password":"pass1234"}').accessToken
   Invoke-RestMethod -Method Post -Uri http://localhost:8080/assignments -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body '{"title":"Test assignment","startDate":"2026-11-02","endDate":"2026-12-11","leaderEmails":["student02@example.com","student07@example.com"]}'
   ```

   예시 제목을 영어로 둔 이유: Windows PowerShell 5.1은 문자열 body를 UTF-8로 보내지 않아서 한글 제목이 깨지거나 400이 될 수 있습니다. 한글 제목은 화면이나 Postman으로 확인합니다.

   산출물 업로드와 다운로드는 Windows에 들어 있는 `curl.exe`로 확인합니다. 시작 데이터의 1조 이서연(student02, 역할 백엔드)으로 올립니다.

   ```powershell
   $token = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType "application/json" -Body '{"email":"student02@example.com","password":"pass1234"}').accessToken
   curl.exe -X POST http://localhost:8080/teams/1/deliverables -H "Authorization: Bearer $token" -F "file=@README.md"
   curl.exe http://localhost:8080/teams/1/deliverables -H "Authorization: Bearer $token"
   curl.exe -o download.txt http://localhost:8080/deliverables/1/download -H "Authorization: Bearer $token"
   ```

   `-o download.txt`는 받은 내용을 그 이름으로 저장합니다. curl은 한글 파일 이름(RFC 5987 형식)을 풀지 못해서 이름을 직접 정합니다. 한글 이름이 깨지지 않는 것은 브라우저로 확인합니다(`docs/demo-script.md` 장면 6). macOS는 `curl.exe` 대신 `curl`입니다.

4. 파일 저장소 확인: MinIO 콘솔 http://localhost:9001 에 `infra/.env`의 `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`로 로그인합니다. 서버가 시작할 때 만든 `footprint` 버킷이 보이면 성공입니다.

## 빌드와 테스트

`.\gradlew.bat build`(macOS는 `./gradlew build`)는 테스트도 함께 실행합니다. 테스트가 실제 PostgreSQL과 MinIO에 접속하므로 **빌드 전에 `infra`의 `docker compose up -d`가 켜져 있어야 합니다.** 꺼져 있으면 테스트가 실패합니다.
테스트는 시연용 버킷이 아니라 `footprint-test` 버킷을 씁니다. 빌드할 때마다 이 버킷에 작은 시험 파일과 시작 데이터 파일이 쌓입니다.
**빌드(테스트)도 서버를 켤 때처럼 데이터베이스를 지우고 시작 데이터를 다시 넣습니다.** 화면에서 만든 과제·산출물·평가가 사라지므로 시연·리허설 중에는 빌드하지 않습니다.

실행 로그에 `Using generated security password: ...`가 나옵니다. Spring Security가 기본으로 만드는 계정입니다. 이 프로젝트는 이 계정을 쓰지 않으므로 무시합니다.

## 시작 계정

서버가 시작할 때 `config/DataInitializer`가 넣습니다. 비밀번호는 모두 `pass1234`이고 데이터베이스에는 BCrypt로 바꾼 값이 저장됩니다.
로그인 방식(구글 계정 로그인 여부)이 정해지면 바뀔 수 있습니다.

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

## 시작 과제와 조

| 과제 | 기간 | 만든 교수 |
|---|---|---|
| 발자국 시연 과제 | 2026-11-02 ~ 2026-12-11 | 이교수 |

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

2조는 "같은 역할을 여러 조원이 맡을 수 있다"는 규칙을 보이려고 백엔드가 2명입니다.

시작 산출물(1조, 파일 본문도 MinIO에 있음): `기획서.txt`(김민준, 기획), `API 명세.txt`(이서연, 백엔드).
MinIO는 서버를 다시 켜도 비워지지 않아서, 서버를 켤 때마다 같은 내용의 시작 파일이 새 key로 하나씩 더 쌓입니다. 데이터베이스는 매번 새로 만들어지므로 목록에는 2건만 보입니다.

초대 코드: 1조 `demo-team-1`, 2조 `demo-team-2`. 초대 링크는 `http://localhost:5173/join/demo-team-1` 모양입니다. 조장이 다시 발급하면 무작위 값으로 바뀌고 이전 코드는 404가 됩니다.
시작 학생 10명은 모두 이미 시작 과제의 조에 있습니다. 합류를 시험하려면 교수로 새 과제를 만들고, 그 과제의 조장이 발급한 링크로 다른 학생이 합류합니다.

동료평가는 시작 데이터에 없습니다. 아무 학생으로 로그인해 `http://localhost:5173/teams/1/evaluation`(2조는 `/teams/2/evaluation`)에서 제출해 봅니다. 한 학생은 한 번만 제출할 수 있고, 서버를 다시 켜면 데이터베이스가 새로 만들어져 다시 제출할 수 있습니다.
