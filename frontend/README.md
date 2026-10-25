# frontend — 발자국 시연본 화면

Node.js 24, React 19, Vite 8. `../footprint-study/example/frontend`를 복사해서 시작했습니다.

## 처음 한 번

```powershell
cd frontend
npm install
```

## A. 가짜 API로 실행 (Docker, Java 없이)

터미널을 2개 엽니다. 둘 다 `frontend` 폴더에서 실행합니다.

1. 첫 번째 터미널에서 가짜 API를 켭니다. 켜 둔 채로 둡니다.

   ```powershell
   npm run mock
   ```

2. 두 번째 터미널에서 화면이 가짜 API(3001)를 보게 설정하고 실행합니다.

   ```powershell
   Set-Content -Path .env.local -Value "VITE_API_URL=http://localhost:3001" -Encoding ascii
   npm run dev
   ```

   macOS는 첫 줄이 `echo "VITE_API_URL=http://localhost:3001" > .env.local`입니다. 실제 백엔드로 돌아가려면 `.env.local`을 지우고 `npm run dev`를 다시 켭니다.

가짜 API 확인: 로그인하면 `{"accessToken": "mock-token-2"}`가 옵니다.

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:3001/auth/login -ContentType "application/json" -Body '{"email":"student01@example.com","password":"pass1234"}'
Invoke-RestMethod -Uri http://localhost:3001/members/me -Headers @{ Authorization = "Bearer mock-token-2" }
```

## B. 실제 백엔드로 실행

`backend/README.md`대로 서버를 켠 뒤 `npm run dev`만 실행합니다. `.env`의 기본값이 실제 백엔드(8080)입니다.

## 화면 확인

http://localhost:5173 을 열면 로그인 화면이 나옵니다. 시작 계정(예: `student01@example.com` / `pass1234`)으로 로그인하면 "김민준 (학생)님, 환영합니다."가 보입니다.

- 로그인 token은 sessionStorage에 저장합니다. 탭마다 따로라서, 한 탭은 교수, 다른 탭은 학생으로 동시에 로그인할 수 있습니다. 탭을 닫으면 다시 로그인해야 합니다.
- token이 만료되거나 틀리면 로그인 화면으로 돌아갑니다.

## 가짜 API를 쓸 때 알아 둘 것

- 가짜 API는 `db.json`, `mock/middleware.cjs`로 만듭니다. 응답 모양은 실제 API(`docs/api-draft.md`)와 같습니다.
- 주소마다 `mock/middleware.cjs`의 `handle...` 함수 하나가 실제 API 하나를 흉내 냅니다. 실제 API에 없는 주소는 404입니다. json-server가 기본으로 만드는 주소(`/members` 등)도 막습니다(비밀번호가 그대로 보이기 때문입니다).
- `db.json`의 시작 데이터(과제 1개, 조 2개, 조원 10명)는 실제 백엔드의 시작 데이터와 같습니다. 목록 이름은 `assignments`, `teams`, `teamMembers`이고 연결은 `assignmentId`, `teamId`, `memberId` 같은 번호로 합니다.
- 계정은 실제 백엔드의 시작 계정과 같습니다(`backend/README.md`). 비밀번호는 모두 `pass1234`입니다.
- 가짜 token은 `mock-token-<회원 id>`입니다. 서명이 없으므로 화면 코드는 token을 풀어 읽지 않고 그대로 보내기만 합니다.
- `mock/middleware.cjs`만 `require`, `module.exports` 문법입니다. json-server가 이 파일을 `require`로 읽기 때문입니다.
- 데이터를 바꾸는 요청을 보내면 `db.json` 파일이 바뀝니다. 처음 상태로 되돌리려면 `git restore db.json`을 실행합니다.
