# Tailscale로 개발 서버 열기

- 관리자 PC에서 켠 개발 서버(화면 5173, 백엔드 8080)를 같은 Tailscale 네트워크(tailnet)의 다른 기기에서 여는 방법
- 인터넷 전체에 여는 것이 아님. tailnet에 들어온 기기만 열 수 있음
- 주소는 `https://<PC 이름>.<tailnet 이름>.ts.net`. 아래에서는 `<주소>`로 적음. 실제 이름은 저장소에 올리지 않음

## 구조

```
다른 기기 ──https──▶ Tailscale(이 PC, 인증서를 Tailscale이 받아 줌)
                      └─http─▶ localhost:5173 (Vite 개발 서버) ─ /api ─▶ 127.0.0.1:8080 (백엔드)
```

- 개발 서버와 백엔드는 `localhost`에만 묶인 채로 둠. 방화벽을 열지 않음

## 맞출 곳 세 가지

| 곳 | 무엇 | 안 하면 |
|---|---|---|
| tailnet | 관리 콘솔에서 Serve(HTTPS 인증서)를 켬. tailnet에서 한 번만 | `tailscale serve`가 "Serve is not enabled on your tailnet"으로 멈춤 |
| 화면 | `frontend/.env.local`에 `DEV_ALLOWED_HOSTS=<PC 이름>.<tailnet 이름>.ts.net` | 개발 서버가 모르는 이름이라 403 |
| 백엔드 | 환경 변수 `CORS_ALLOWED_ORIGINS`에 `https://<주소>`를 더함 | 로그인은 되지만 `/auth/refresh`, `/auth/logout`이 403(Origin 검사). 10분 뒤 로그인이 풀림 |

## 켜는 법

1. 이 PC의 주소 확인
   ```powershell
   tailscale status
   ```
   - PC 이름은 첫 줄에 있음. tailnet 이름은 관리 콘솔의 DNS 화면에 있음
2. `frontend/.env.local`에 한 줄을 더함(없으면 만듦). `.gitignore`에 있어서 저장소에 올라가지 않음
   ```
   DEV_ALLOWED_HOSTS=<PC 이름>.<tailnet 이름>.ts.net
   ```
3. 백엔드를 허용 주소를 더해 켬(`backend` 폴더)
   ```powershell
   $env:CORS_ALLOWED_ORIGINS = "http://localhost:5173,https://<주소>"; .\gradlew.bat bootRun
   ```
4. 화면을 켬(`frontend` 폴더). `.env.local`을 고쳤으면 다시 켜야 읽음
   ```powershell
   npm run dev
   ```
5. Tailscale이 5173으로 넘기게 함
   ```powershell
   tailscale serve --bg http://localhost:5173
   ```
   - `5173`만 적으면 `127.0.0.1:5173`으로 넘김. Windows에서 Vite 개발 서버는 `localhost`를 IPv6(`::1`)로만 여는 경우가 있어서 그때 502가 남. `http://localhost:5173`으로 적으면 됨
   - 처음이면 Serve를 켜는 주소를 보여 줌. 그 주소를 브라우저로 열어 켠 뒤 다시 실행함
6. 다른 기기에서 `https://<주소>`를 엶

## 끄는 법

```powershell
tailscale serve reset
```

- 상태 확인은 `tailscale serve status`. PC를 다시 켜도 설정이 남으므로 다 쓰면 끔

## 제약

- **시작 계정의 비밀번호가 저장소에 공개돼 있음**. 주소를 열 수 있는 사람은 누구나 교수 계정으로 들어옴. 믿는 사람만 있는 tailnet에서만 엶
- `tailscale funnel`은 인터넷 전체에 여는 명령임. 쓰지 않음
- 구글 로그인은 Google Cloud의 OAuth Client "승인된 JavaScript 원본"에 `https://<주소>`를 더해야 함. 이메일·비밀번호 로그인은 상관없음
- 다른 사람이 열려면 그 사람이 tailnet에 들어와 있거나, 이 PC를 그 사람에게 공유해야 함(관리 콘솔의 Share)
- branch 사이트(`docs/branch-sites.md`)와 실행 묶음은 이 방법으로 열지 않음. proxy가 `.localhost` 이름만 받음

## 자주 막히는 곳

- **브라우저에 "사이트에 연결할 수 없음", `DNS_PROBE_FINISHED_NXDOMAIN`** — 여는 기기의 브라우저가 `ts.net` 이름을 못 찾은 것임. 브라우저의 "보안 DNS 사용"이 켜져 있으면 Tailscale의 DNS(MagicDNS)가 아니라 외부 DNS에 물어서 생김. Chrome 계열은 `chrome://settings/security`(웨일은 `whale://settings/security`)에서 끔. Firefox는 설정 → 개인 정보 및 보안 → DNS over HTTPS
  - 같은 기기의 터미널에서 `curl -sI https://<주소>`가 200이면 Tailscale은 정상이고 브라우저 쪽 문제임
- **502** — 이 PC의 개발 서버가 꺼졌거나, `tailscale serve`의 대상이 `127.0.0.1:5173`으로 잡힌 것임(위 5번)
- **로그인은 되는데 새로고침하면 로그인이 풀림** — 백엔드의 `CORS_ALLOWED_ORIGINS`에 `https://<주소>`가 없는 것임
- **빈 화면** — 광고 차단 확장이 개발 서버의 스크립트를 막은 것일 수 있음. 끄고 다시 엶
