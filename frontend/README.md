# frontend — 발자국 데모 화면

Node.js 24, React 19, Vite 8. `../footprint-study/example/frontend`를 복사해서 시작함.

개발하지 않고 데모만 할 때는 저장소 맨 위 `README.md`의 "한 번에 실행"을 쓸 것. 그때 화면은 개발 서버가 아니라 nginx 컨테이너(`Dockerfile`, `nginx.conf`)가 내려줌.

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

`backend/README.md`대로 서버를 켠 뒤 `npm run dev`만 실행. `.env`의 기본값이 실제 백엔드(8080)임.

## 화면 확인

http://localhost:5173 을 열면 로그인 화면이 나옴. 시작 계정(예: `student01@example.com` / `pass1234`)으로 로그인하면 "김민준 (학생)님, 환영합니다."가 보임.

- 로그인 token은 sessionStorage에 저장함. 탭마다 따로라서, 한 탭은 교수, 다른 탭은 학생으로 동시에 로그인할 수 있음. 탭을 닫으면 다시 로그인해야 함
- token이 만료되거나 틀리면 로그인 화면으로 돌아감

## 가짜 API를 쓸 때 알아 둘 것

- 가짜 API는 `db.json`, `mock/middleware.cjs`로 만듦. 응답 모양은 실제 API(`docs/api-draft.md`)와 같음
- 주소마다 `mock/middleware.cjs`의 `handle...` 함수 하나가 실제 API 하나를 흉내 냄. 실제 API에 없는 주소는 404. 처리하다 오류가 나면 `handleError`가 실제처럼 500 `SERVER_ERROR`(JSON이 아닌 body는 400)로 답함. json-server가 기본으로 만드는 주소(`/members` 등)도 막음(비밀번호가 그대로 보이기 때문)
- `db.json`의 시작 데이터(과제 1개, 조 2개, 조원 10명)는 실제 백엔드의 시작 데이터와 같음. 목록 이름은 `assignments`, `teams`, `teamMembers`이고 연결은 `assignmentId`, `teamId`, `memberId` 같은 번호로 함
- 계정은 실제 백엔드의 시작 계정과 같음(`backend/README.md`). 비밀번호는 모두 `pass1234`
- 가짜 token은 `mock-token-<회원 id>`. 서명이 없으므로 화면 코드는 token을 풀어 읽지 않고 그대로 보내기만 함
- `mock/middleware.cjs`만 `require`, `module.exports` 문법. json-server가 이 파일을 `require`로 읽기 때문
- 데이터를 바꾸는 요청을 보내면 `db.json` 파일이 바뀜. 처음 상태로 되돌리려면 `git restore db.json`을 실행할 것
- 가짜 API에 올린 산출물 파일은 `mock/files/`에 저장됨(실제 백엔드의 MinIO 역할). 시작 파일 `seed-1.txt`, `seed-2.txt`만 저장소에 올라가고 새로 올린 파일은 git이 무시함
- 동료평가는 `db.json`의 `evaluations`에 `evaluatorId`, `targetId`(둘 다 조 참여 번호)로 저장됨. 시작 상태는 빈 목록. 화면과 API는 점수를 돌려주지 않고 제출 여부만 돌려줌
- 가짜 API의 다운로드 header는 실제와 모양이 조금 다름(`res.attachment`가 만듦). 한글 이름은 둘 다 `filename*=UTF-8''...`로 옴. 화면은 이 header를 읽지 않고 목록의 `fileName`을 씀
