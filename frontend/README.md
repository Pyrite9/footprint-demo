# frontend — 발자국 데모 화면

Node.js 24, React 19, Vite 8. `../footprint-study/example/frontend`를 복사해서 시작함.

개발하지 않고 데모만 할 때는 `docs/getting-started.md`의 "한 번에 실행"을 쓸 것. 그때 화면은 개발 서버가 아니라 nginx 컨테이너(`Dockerfile`, `nginx.conf`)가 내려줌.

## 처음 한 번

```powershell
cd frontend
npm install
```

## A. 가짜 API로 실행 (Docker, Java 없이)

터미널을 2개 열 것. 둘 다 `frontend` 폴더에서 실행.

1. 첫 번째 터미널에서 가짜 API 켜기. 켜 둔 채로 둘 것.

   ```powershell
   npm run mock
   ```

2. 두 번째 터미널에서 화면이 가짜 API(3001)를 보게 설정하고 실행.

   ```powershell
   Set-Content -Path .env.local -Value "VITE_API_URL=http://localhost:3001" -Encoding ascii
   npm run dev
   ```

   - macOS는 첫 줄이 `echo "VITE_API_URL=http://localhost:3001" > .env.local`
   - 실제 백엔드로 돌아가려면 `.env.local`을 지우고 `npm run dev`를 다시 켤 것

가짜 API 확인: 로그인하면 `{"accessToken": "mock-token-2"}`가 옴.

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:3001/auth/login -ContentType "application/json" -Body '{"email":"student01@example.com","password":"pass1234"}'
Invoke-RestMethod -Uri http://localhost:3001/members/me -Headers @{ Authorization = "Bearer mock-token-2" }
```

## B. 실제 백엔드로 실행

`backend/README.md`대로 서버를 켠 뒤 `npm run dev`만 실행. `.env`의 기본값(`VITE_API_URL=/api`)이 실제 백엔드임.

- 화면은 백엔드를 화면과 같은 주소의 `/api`로 부름. 개발 서버가 `/api`를 떼고 `http://127.0.0.1:8080`으로 넘김(`vite.config.js`의 `server.proxy`). 예: `http://localhost:5173/api/auth/login` → `http://127.0.0.1:8080/auth/login`
- 이유: 화면(`localhost:5173`)과 백엔드(`127.0.0.1:8080`)는 브라우저가 다른 site로 봐서 로그인 cookie를 보내지 않음. 같은 주소로 부르면 cookie가 실림
- 데모의 nginx 컨테이너도 같은 일을 함(`nginx.conf`의 `location /api/`)
- 브라우저 개발자 도구의 Network 탭에는 `/api/...`로 보임. 백엔드 로그와 `docs/api-draft.md`의 주소는 `/api`를 뗀 주소임

## 화면 확인

http://localhost:5173 을 열면 로그인 화면이 나옴. 아래 "시작 계정으로 로그인 (개발·백업용)"을 펼쳐 시작 계정(예: `student01@example.com` / `pass1234`)으로 로그인하면 왼쪽 메뉴 아래에 "김민준", 본문에 "내 과제"가 보임.

- access token은 `src/api/token.js`의 변수(메모리)에만 둠. sessionStorage·localStorage에 넣지 않음. 유효 시간은 10분
- refresh token은 서버가 `refreshToken` cookie(`HttpOnly`, `SameSite=Strict`)로 내려줌. 화면의 JS는 읽지 못하고 브라우저가 요청에 붙임. 유효 시간은 로그인부터 24시간
- 새로고침하거나 새 탭을 열면 `RequireLogin`이 `POST /auth/refresh`로 access token을 다시 받음. 그동안 진행 막대가 보임
- 요청이 access token 만료로 401이면 `client.js`가 재발급하고 같은 요청을 한 번만 다시 보냄. 재발급도 401이면 로그인 화면으로 돌아가고 "로그인이 만료되었습니다" 안내가 나옴
- 같은 브라우저의 탭은 cookie를 함께 써서 탭마다 다른 계정으로 로그인할 수 없음. 교수와 학생을 동시에 보려면 브라우저를 둘 쓰거나 한쪽을 시크릿 창으로 엶. 다른 탭에서 다른 계정으로 로그인하면 먼저 연 탭도 다음 재발급부터 그 계정이 됨
- 만료 뒤 재발급을 손으로 보려면 백엔드를 `JWT_EXPIRATION_MINUTES=1`로 켬(`backend/README.md`)

## 가짜 API를 쓸 때 알아 둘 것

- 가짜 API는 `db.json`, `mock/middleware.cjs`로 만듦. 응답 모양은 실제 API(`docs/api-draft.md`)와 같음
- 주소마다 `mock/middleware.cjs`의 `handle...` 함수 하나가 실제 API 하나를 흉내 냄. 실제 API에 없는 주소는 404. 처리하다 오류가 나면 `handleError`가 실제처럼 500 `SERVER_ERROR`(JSON이 아닌 body는 400)로 답함. json-server가 기본으로 만드는 주소(`/members` 등)도 막음(비밀번호가 그대로 보이기 때문)
- `db.json`의 시작 데이터(과제 2개, 조 3개, 조원 13명)는 실제 백엔드의 시작 데이터와 같음. 목록 이름은 `assignments`, `teams`, `teamMembers`이고 연결은 `assignmentId`, `teamId`, `memberId` 같은 번호로 함
- 계정은 실제 백엔드의 시작 계정과 같음(`backend/README.md`). 비밀번호는 모두 `pass1234`
- 개인정보 고지는 임지아(student10, `id` 11)만 동의 전(`privacyConsentedAt: null`)임. 동의하면 `db.json`에 시각이 남으므로 다시 시험하려면 `git restore db.json`
- 가짜 token은 `mock-token-<회원 id>`. 서명이 없으므로 화면 코드는 token을 풀어 읽지 않고 그대로 보내기만 함
- 가짜 refresh token은 `mock-refresh-<회원 id>`이고 실제와 같은 `refreshToken` cookie로 옴(`Secure` 없음). 만료, 교체, `Origin` 검사는 없음. 그래서 가짜 API로는 access token 만료와 재발급 흐름을 볼 수 없음(가짜 access token은 만료되지 않음)
- 화면(`localhost:5173`)과 가짜 API(`localhost:3001`)는 주소가 달라도 같은 site라서 cookie가 실림. json-server가 CORS 응답에 `Access-Control-Allow-Credentials: true`와 요청의 Origin을 그대로 돌려줌
- `mock/middleware.cjs`만 `require`, `module.exports` 문법. json-server가 이 파일을 `require`로 읽기 때문
- 데이터를 바꾸는 요청을 보내면 `db.json` 파일이 바뀜. 처음 상태로 되돌리려면 `git restore db.json`을 실행할 것
- 가짜 API에 올린 산출물 파일은 `mock/files/`에 저장됨(실제 백엔드의 MinIO 역할). 시작 파일 `seed-1.txt`, `seed-2.txt`, `seed-3.txt`만 저장소에 올라가고 새로 올린 파일은 git이 무시함
- 가짜 API에서 시작 산출물을 삭제하면 저장소의 시작 파일도 지워짐. `git restore db.json mock/files`로 되돌릴 것
- 동료평가는 `db.json`의 `evaluations`에 `evaluatorId`, `targetId`(둘 다 조 참여 번호)로 저장됨. 시작 상태는 빈 목록. 화면과 API는 점수를 돌려주지 않고 제출 여부만 돌려줌
- 평가 임시 저장은 `db.json`의 `evaluationDrafts`에 저장됨. 시작 상태는 빈 목록. 저장한 본인에게만 돌려줌
- 근거 패널은 따로 저장하지 않음. `deliverables`, `contributions`, `confirmations`에서 그 조원의 기록을 모아 돌려줌
- 기록 열람도 따로 저장하지 않음. 조의 기록을 모아 한 줄씩 펼쳐 돌려줌. 시작 데이터의 시각은 실제 백엔드와 달라서 시작 줄의 순서는 다를 수 있음
- 평가 결과는 `evaluations`를 읽어 입력된 그대로 돌려줌. CSV도 실제 백엔드와 같은 글자로 만듦
- 가짜 API의 다운로드 header는 실제와 모양이 조금 다름(`res.attachment`가 만듦). 한글 이름은 둘 다 `filename*=UTF-8''...`로 옴. 화면은 이 header를 읽지 않고 목록의 `fileName`을 씀
