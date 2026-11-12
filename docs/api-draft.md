# API 초안

`../footprint-study/docs/api.md`가 생기기 전까지 쓰는 데모의 API 초안임. Controller를 만들기 전에 여기에 먼저 적고, 확인을 기다리지 않고 바로 만듦(2026.10.25 관리자 결정). 정한 이유는 `docs/worklog.md`의 그 단위 항목 "결정" 줄에 남김. 제목 끝의 날짜는 그 API를 정한 날임. "확인 2026.10.25"는 이 결정 전에 관리자 확인을 받은 API, 날짜만 있으면 확인 없이 추천안으로 만든 API라는 표시임

공통
- 오류 응답은 모두 `{ "code": "...", "message": "..." }`. `code`는 `NOT_FOUND` 404, `INVALID_INPUT` 400, `UNAUTHORIZED` 401, `FORBIDDEN` 403, `DUPLICATE` 409, `SERVER_ERROR` 500
- `/auth/**`와 `GET /actuator/health`(상태 확인) 말고는 모두 header에 `Authorization: Bearer <accessToken>`이 필요함. 없거나 틀리면 401 `UNAUTHORIZED`
- 화면은 백엔드를 화면과 같은 origin의 `/api`로 부름(2026.11.12 관리자 결정). 예: 화면의 `POST /api/auth/login` → 백엔드의 `POST /auth/login`
  - 개발은 Vite 개발 서버가, 제출 묶음은 화면 컨테이너의 nginx가 `/api`를 떼고 백엔드로 넘김. 이 문서의 주소는 모두 `/api`를 뗀 백엔드의 주소임
  - 이유: 화면(`localhost:5173`)과 백엔드(`127.0.0.1:8080`)가 다른 site이면 브라우저가 refresh token cookie를 보내지 않음. 같은 origin이면 cookie가 항상 실리고 화면 요청에는 CORS가 필요 없음
  - 백엔드를 직접 부르는 주소(`http://127.0.0.1:8080`)는 그대로 열려 있음. Postman, 상태 확인, 테스트가 씀
  - 가짜 API는 지금처럼 화면이 `http://localhost:3001`을 직접 부름. `localhost`끼리는 포트가 달라도 같은 site라서 cookie가 실림
- 없는 주소, 또는 주소는 있지만 그 method가 없는 요청(예: `DELETE /assignments/1`)은 404 `NOT_FOUND` "없는 주소입니다"(2026.10.25 결정). token이 없으면 그보다 먼저 401임
- 서버 내부 오류는 500 `SERVER_ERROR` "서버에서 오류가 났습니다"(2026.10.26 관리자 결정). 원인(예외 내용)은 응답에 담지 않고 서버 로그에만 남김
- 위 code에 들어가지 않는 요청 오류(예: 지원하지 않는 Content-Type)는 400 `INVALID_INPUT` "요청의 형식이 올바르지 않습니다"
- 요청 body의 값은 타입이 정확해야 함(2026.10.30 결정). 숫자 필드(점수, id)에 소수(`4.5`, `5.0`)나 문자열(`"5"`)을 보내면 400 `INVALID_INPUT` "요청 body의 형식이 올바르지 않습니다. teamMemberId: 정수여야 합니다"(아래 "400의 틀린 칸"). 서버가 비슷한 값으로 바꿔 받지 않음. 날짜 필드의 빈 글자 `""`도 형식 오류임. 반대로 글자 필드에 숫자나 `true`를 보내면 글자(`"5"`, `"true"`)로 받음(Jackson 기본 동작). 가짜 API는 `5.0`을 `5`로 받음(JavaScript가 JSON을 읽으면 둘이 같은 값이 됨). 화면은 정수만 보내므로 차이가 드러나지 않음

400의 틀린 칸 (X-7, 2026.11.12)
- 응답 모양은 그대로 `{ code, message }`임. 칸을 더하지 않고 `message` 끝에 틀린 칸을 `칸 이름: 이유`로 붙임. 앞 문장과는 `. `, 칸끼리는 `, `로 이음
- 칸 이름은 요청 JSON의 필드 이름이고 목록 안은 `evaluations[0].goalSharing`처럼 적음. 주소 자리는 Controller의 이름(`teamId` 등), 업로드는 `file`
- 입력값 검사(`@Valid`)에 걸리면 걸린 칸을 **모두** 적고 `칸 이름: 이유` 글자를 문자 코드 순으로 정렬함. 요청마다 순서가 같음. 예: `POST /assignments`에 `{}` → "입력값이 올바르지 않습니다. endDate: 값이 없습니다, leaderEmails: 비어 있습니다, startDate: 값이 없습니다, title: 비어 있습니다"
  - 이유: `@NotBlank`·`@NotEmpty` "비어 있습니다", `@NotNull` "값이 없습니다", `@Size` "255자 이하여야 합니다", `@Min` "1 이상이어야 합니다", `@Max` "5 이하여야 합니다"
  - 한 칸이 두 검사에 걸리면 둘 다 적음. 예: 공백 256자 제목 → "title: 255자 이하여야 합니다, title: 비어 있습니다"
- 요청 body의 타입이 틀리면 JSON을 앞에서부터 읽다가 **처음 틀린 칸 하나**만 적음. 예: "요청 body의 형식이 올바르지 않습니다. evaluations[1].goalSharing: 정수여야 합니다"
  - 이유: "정수여야 합니다", "문자열이어야 합니다", "yyyy-MM-dd 형식의 날짜여야 합니다", "배열이어야 합니다", 그 밖(목록 안의 평가 하나가 객체가 아님 등) "형식이 올바르지 않습니다"
  - JSON 문법이 깨졌거나, body가 없거나, body가 객체가 아니면(`[]` 등) 칸을 알 수 없어서 "요청 body의 형식이 올바르지 않습니다"만 보냄
- 주소의 번호가 정수가 아니면 "주소의 값이 올바르지 않습니다. teamId: 정수여야 합니다". `-1`처럼 정수이면 형식 오류가 아니라 없는 대상(404)임
- 업로드에 `file` 칸이 없으면 "입력값이 올바르지 않습니다. file: 값이 없습니다"
- 서버 내부 정보(클래스 이름, Jackson 원문 메시지)는 넣지 않음
- Service가 업무 규칙으로 거절한 400(예: "종료일이 시작일보다 앞입니다")은 이미 사람이 읽는 문장이라 그대로임
- 가짜 API도 같은 문장을 냄

---

## 인증

### POST /auth/login — 로그인 (M-2-1) · 확인 2026.10.25

이메일과 비밀번호로 로그인하고 token을 받음. token 없이 부를 수 있음

요청
```json
{ "email": "student01@example.com", "password": "pass1234" }
```

200
```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `email`이나 `password`가 비었을 때 |
| 401 | `UNAUTHORIZED` | 이메일이 없거나 비밀번호가 틀렸을 때. 둘을 구분하지 않고 같은 메시지를 줌 |
| 404 | `NOT_FOUND` | 운영 설정(`prod`)으로 켠 서버일 때. 회원과 비밀번호가 맞아도 같음. 메시지 "이메일·비밀번호 로그인은 사용하지 않습니다. 구글 로그인을 사용합니다" |

운영 설정(O-2, 2026.11.12)
- 운영 설정(환경 변수 `SPRING_PROFILES_ACTIVE=prod`)에서는 이 로그인이 꺼짐. `POST /auth/google`은 그대로임
- 404로 정한 이유: 이 서버에 없는 기능이라 "없는 주소"와 같은 code를 씀. 401이면 비밀번호가 틀린 것으로 읽히고, 403이면 권한이 모자란 것으로 읽힘
- 본문 형식 검사(`@Valid`)가 먼저라서 `email`·`password`가 비었으면 운영 설정에서도 400 `INVALID_INPUT`임. 형식이 맞으면 회원을 찾기 전에 404임
- 개발 설정(환경 변수 없이 켠 서버)과 가짜 API는 지금과 같음(200, 400, 401)

access token (2026.11.12 관리자 결정으로 24시간 → 10분)
- 서명 HS256. 유효 시간 10분(`jwt.expiration-minutes`)
- payload: `sub` = 회원 id(문자열), `memberType` = `PROFESSOR` 또는 `STUDENT`
- 화면은 token을 풀어 읽지 않음. 회원 정보는 `GET /members/me`로 받음
- 화면은 access token을 JS 변수(메모리)에만 둠. `sessionStorage`·`localStorage`에 넣지 않음. 새로고침하면 사라지므로 화면이 시작할 때 `POST /auth/refresh`로 다시 받음
- 서버는 access token을 저장하지 않음. 로그아웃해도 이미 받은 access token은 만료(최대 10분)까지 유효함
- 가짜 API의 token은 JWT가 아니라 문자열 `mock-token-<회원 id>`이고 만료가 없음

refresh token (2026.11.12 관리자 결정)
- 로그인(`POST /auth/login`, `POST /auth/google`)에 성공하면 응답 body의 `accessToken`과 함께 `Set-Cookie`로 refresh token을 내려줌. body에는 담지 않음
- 값: 서버가 만든 난수 32바이트를 Base64 URL 글자로 바꾼 것. JWT가 아니고 뜻이 없음
- cookie: 이름 `refreshToken`, `HttpOnly`(JS가 읽지 못함), `SameSite=Strict`, `Path=/`, `Max-Age`는 만료까지 남은 초
  - `Secure`는 설정값 `footprint.refresh-cookie-secure`를 따름. 개발 설정은 false(http), 운영 설정은 true
  - `Path=/`인 이유: 화면은 `/api/auth/refresh`, 가짜 API와 Postman은 `/auth/refresh`로 불러서 한 경로로 좁힐 수 없음
- 유효 시간 24시간(`footprint.refresh-token-hours`). **로그인한 시각부터 세는 절대 만료**임. 재발급해도 늘어나지 않음
- 서버는 DB(`refresh_tokens`)에 값의 SHA-256 hash, 회원, 만료 시각, 만든 시각을 저장함. 값 자체는 저장하지 않음
- 한 회원이 여러 개를 가질 수 있음(브라우저마다 하나). 로그인할 때 그 회원의 만료된 행을 지움
- 같은 브라우저의 탭은 cookie를 함께 씀. 탭마다 다른 계정으로 로그인할 수 없음. 다른 탭에서 다른 계정으로 로그인하면 먼저 열린 탭도 다음 재발급부터 그 계정이 됨
- 가짜 API의 refresh token은 문자열 `mock-refresh-<회원 id>`이고 만료가 없음. cookie 이름과 속성은 같음(`Secure` 없음)
- 로그에 refresh token과 cookie 값을 찍지 않음
- 12.11 데모의 기본 로그인은 아래 `POST /auth/google`임. 이메일·비밀번호 로그인은 시작 계정용(개발·백업)으로 남김(2026.10.26 관리자 결정)

### POST /auth/google — 구글 로그인 (M-1-1, M-2-1) · 2026.10.26

화면의 구글 버튼이 받은 ID token(`credential`)을 보내고 우리 서버의 token을 받음. token 없이 부를 수 있음
처음 로그인한 구글 계정은 회원으로 저장됨(M-1-1 회원 등록). 응답은 `POST /auth/login`과 같음

요청
```json
{ "credential": "eyJhbGciOiJSUzI1NiIs..." }
```

200
```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `credential`이 비었을 때 |
| 401 | `UNAUTHORIZED` | token 모양이 깨졌거나, 구글의 서명이 틀렸거나, 만료됐거나, 다른 앱(Client ID)용이거나, 구글이 이메일을 확인하지 않은 계정일 때. 구분하지 않고 같은 메시지를 줌 |
| 403 | `FORBIDDEN` | 허용한 학교 도메인(`hd`, 기본 `g.yju.ac.kr`)의 계정이 아닐 때 |
| 500 | `SERVER_ERROR` | 서버가 구글 공개키를 받아 오지 못했을 때(서버의 인터넷 끊김) |

서버 쪽 규칙
- ID token 검증은 Google 라이브러리(`GoogleIdTokenVerifier`)가 함: 서명(구글 공개키), 만료, 발급자(`accounts.google.com`), 받는 앱(`aud` = `google.client-id`)
- 그다음 서버가 `email_verified`가 true인지, `hd`가 `google.allowed-domains`에 있는지 봄
- 같은 이메일의 회원이 있으면 그 회원으로 로그인함. 없으면 새로 만듦. 이름은 구글 이름(공개하지 않은 계정이면 이메일), 비밀번호는 없음(null)
- 회원 유형: 새로 만드는 회원은 이메일이 `google.professor-emails`(쉼표로 구분한 목록)에 있으면 `PROFESSOR`, 아니면 `STUDENT`. 대소문자는 가리지 않음
- 교수로 올리기(X-1, 2026.11.12): 이미 있는 회원도 구글 로그인할 **때마다** 확인함. 회원 유형이 `STUDENT`이고 이메일이 교수 목록에 있으면 `PROFESSOR`로 바꾸고, 그 로그인에서 발급하는 token부터 교수로 담음
  - 조원·조장 기록(조 참여 기록)이 하나라도 있는 학생은 올리지 않고 `STUDENT`로 로그인함. 오류를 내지 않음. "조원은 모두 학생"을 지키려는 것임
  - 내리지는 않음. 목록에서 빠져도 `PROFESSOR`로 남음
  - 바뀐 시각이나 이력은 남기지 않음. 관리 화면과 새 API는 없음
  - 이메일·비밀번호 로그인(`POST /auth/login`)은 유형을 바꾸지 않음
  - 권한 검사는 token의 `memberType`이 아니라 DB의 회원 유형으로 하므로, 올리기 전에 받은 token으로도 교수 권한이 바로 적용됨
- 비밀번호가 없는 회원(구글로 만든 회원)은 `POST /auth/login`으로 로그인할 수 없음(401)
- 가짜 API는 구글 서명을 확인하지 못함. payload만 풀어 같은 규칙(`email_verified`, `hd`, 교수 목록)을 적용함

### POST /auth/refresh — access token 재발급 · 2026.11.12

cookie의 refresh token으로 새 access token을 받음. 요청 body와 `Authorization` header는 없음. 화면이 시작할 때와 access token이 만료돼 401을 받았을 때 부름

200: 모양은 로그인과 같음. `Set-Cookie`로 **새 refresh token**도 함께 내려줌
```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

| 상태 | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | cookie가 없거나, DB에 없는 값이거나(이미 쓴 것, 로그아웃한 것), 만료됐을 때. 구분하지 않고 같은 메시지 "로그인이 만료되었습니다. 다시 로그인합니다"를 줌 |
| 403 | `FORBIDDEN` | `Origin` header가 있는데 허용한 화면 주소(`cors.allowed-origins`)가 아닐 때. 메시지 "허용하지 않은 주소에서 온 요청입니다" |

서버 쪽 규칙
- 검사 순서: `Origin` → cookie → DB의 행 → 만료 시각
- 교체(rotation): 성공하면 쓴 refresh token의 행을 지우고 새 값을 저장함. 만료 시각은 지운 행의 것을 그대로 물려줌(절대 만료). 한 번 쓴 값으로 다시 부르면 401임
- 같은 값으로 두 요청이 동시에 오면 행을 지운 쪽 하나만 성공하고 나머지는 401임(지운 행의 수로 판단)
- 401일 때는 `Set-Cookie`로 cookie를 지움(`Max-Age=0`)
- 새 access token의 `memberType`은 DB의 회원 유형으로 다시 담음. 교수로 올리기(X-1)는 하지 않음(구글 로그인 때만)
- `Origin` 검사는 CSRF를 막는 둘째 장치임(첫째는 `SameSite=Strict`). 이 API만 cookie로 인증하기 때문임. `Origin`이 없는 요청(Postman, 테스트)은 통과함. 브라우저는 POST에 `Origin`을 항상 붙임
- 재사용 감지(이미 쓴 값이 다시 오면 그 회원의 refresh token을 모두 지움)는 두지 않음. 여러 탭이 동시에 재발급하면 정상 사용도 걸리기 때문임
- 화면 쪽: 재발급은 한 번에 하나만 함. 한 탭 안에서는 진행 중인 재발급 하나를 여러 요청이 함께 기다리고, 탭끼리는 Web Locks API(`navigator.locks`)로 차례를 지킴. 재발급 뒤 원래 요청을 한 번만 다시 보냄. 재발급이 401이면 access token을 지우고 `/login?expired=1`로 감
- 가짜 API: cookie의 `mock-refresh-<회원 id>`에서 회원을 찾아 `mock-token-<회원 id>`를 줌. 교체와 만료, `Origin` 검사는 없음. cookie가 없거나 회원이 없으면 같은 401임

### POST /auth/logout — 로그아웃 · 2026.11.12

cookie의 refresh token을 서버에서 지움. 요청 body와 `Authorization` header는 없음

204: body 없음. `Set-Cookie`로 cookie를 지움(`Max-Age=0`)

| 상태 | code | 언제 |
|---|---|---|
| 403 | `FORBIDDEN` | `Origin` header가 있는데 허용한 화면 주소가 아닐 때. `POST /auth/refresh`와 같음 |

서버 쪽 규칙
- cookie가 없거나 DB에 없는 값이어도 204임. 여러 번 눌러도 결과가 같음
- 그 브라우저의 refresh token 하나만 지움. 같은 회원의 다른 브라우저는 로그인된 채로 남음
- 이미 받은 access token은 만료(최대 10분)까지 유효함. 화면은 로그아웃할 때 메모리의 access token도 지움
- 화면은 이 요청이 실패해도(서버 꺼짐 등) access token을 지우고 로그인 화면으로 감
- 가짜 API: cookie를 지우고 204

---

## 회원

### GET /members/me — 내 정보 (M-2-1) · 확인 2026.10.25

로그인한 회원 자신의 정보를 받음. token이 필요함

200
```json
{ "id": 2, "email": "student01@example.com", "name": "김민준", "memberType": "STUDENT", "privacyConsentedAt": "2026-11-12T09:00:00" }
```

| 상태 | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때. 셋을 구분하지 않고 같은 메시지를 줌 |
| 404 | `NOT_FOUND` | token의 회원 id가 DB에 없을 때(시작 데이터 순서를 바꾼 뒤 예전 token을 쓴 경우). 가짜 API는 이때 401임 |

서버 쪽 규칙
- Controller는 로그인한 회원을 `@AuthenticationPrincipal Long memberId`로 받음(2026.10.25 결정). 회원 유형이 필요하면 Service가 DB에서 읽음
- `privacyConsentedAt`(X-2, 2026.11.12): 개인정보 고지에 동의한 시각. 동의 전은 `null`. 화면은 이 값이 `null`이면 고지 화면으로 보냄

### POST /members/me/privacy-consent — 개인정보 고지 동의 (X-2) · 2026.11.12

로그인한 회원 본인이 개인정보 고지에 동의함. 요청 body는 없음

200: 동의 시각이 담긴 내 정보. 모양은 `GET /members/me`와 같음
```json
{ "id": 2, "email": "student01@example.com", "name": "김민준", "memberType": "STUDENT", "privacyConsentedAt": "2026-11-12T09:00:00" }
```

| 상태 | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 404 | `NOT_FOUND` | token의 회원 id가 DB에 없을 때. 가짜 API는 이때 401임 |

서버 쪽 규칙
- 동의 시각은 서버가 저장함. body에 시각을 보내도 읽지 않음
- 이미 동의한 회원이 다시 부르면 200이고, 처음 동의한 시각을 그대로 둠(알림 읽음 표시와 같은 방식)
- 동의하지 않는 API는 없음. 동의를 거두거나 회원을 지우는 기능은 없음(회원 탈퇴는 `TODO.md` 2절 "명세 규칙을 바꿔야 하는 것")
- 동의 전 회원의 다른 요청을 서버가 거부하지 않음. 동의 전에 다른 화면으로 가지 못하게 막는 것은 화면이 함(`docs/worklog.md` 2026.11.12 X-2 "결정")
- 고지 문구는 서버에 없고 화면의 `frontend/src/constants/privacyNotice.js`에 있음. 문구의 판(버전)은 저장하지 않음

---

## 과제

### POST /assignments — 과제 생성 (A-1-1, A-1-2) · 확인 2026.10.25

교수가 과제, 조, 조장을 한 번에 만듦. 등록한 과제는 고치거나 지울 수 없음(수정·삭제 API 없음)

요청
```json
{
  "title": "발자국 데모 과제",
  "startDate": "2026-11-02",
  "endDate": "2026-12-11",
  "leaderEmails": ["student01@example.com", "student06@example.com"]
}
```

200
```json
{ "id": 2, "title": "발자국 데모 과제", "startDate": "2026-11-02", "endDate": "2026-12-11", "phase": "RECORDING", "evaluationStartedAt": null }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `title`이 비었거나 255자를 넘거나, 날짜가 없거나 형식이 틀렸거나, `leaderEmails`가 없거나 빈 배열일 때 |
| 400 | `INVALID_INPUT` | 종료일이 시작일보다 앞일 때 |
| 400 | `INVALID_INPUT` | 같은 이메일이 두 번 있을 때(한 학생은 같은 과제의 두 조에 조장이 될 수 없음) |
| 400 | `INVALID_INPUT` | 가입하지 않은 이메일이거나 학생이 아닌 이메일일 때. 메시지에 그 이메일을 넣음 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때. 셋을 구분하지 않고 같은 메시지를 줌 |
| 403 | `FORBIDDEN` | 교수가 아닌 회원이 부를 때 |

서버 쪽 규칙
- 조 개수는 `leaderEmails`의 길이임. 조 이름은 순서대로 "1조", "2조"…로 붙임
- 조장은 그 조의 첫 번째 조원으로 함께 등록됨(`TeamMember.leader = true`). 역할명은 비어 있고 역할 지정(T-2-1)에서 넣음
- 검사를 모두 통과한 뒤에 저장함. 하나의 트랜잭션이라 저장 중에 실패해도 일부만 남지 않음
- 응답에 조 목록은 넣지 않음. 조 목록은 과제 상세(A-2-2)에서 받음
- 과제는 기록 단계(`RECORDING`)로 시작함. `phase`는 `RECORDING`, `EVALUATION` 둘이고, `evaluationStartedAt`은 평가를 시작한 시각임. 기록 단계에서는 `null`임

### GET /assignments — 과제 목록 (A-2-1) · 2026.10.25

내가 볼 수 있는 과제 목록을 받음. 교수는 자기가 만든 과제를 만든 순서의 최신순으로, 학생은 자기가 조원으로 참여한 과제를 참여한 순서의 최신순으로 받음

200
```json
[
  { "id": 1, "title": "발자국 데모 과제", "startDate": "2026-11-02", "endDate": "2026-12-11", "phase": "RECORDING", "evaluationStartedAt": null }
]
```

볼 과제가 없으면 빈 배열 `[]`임

| 상태 | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |

### GET /assignments/{assignmentId} — 과제 상세 (A-2-2) · 2026.10.25

과제 정보와 조 목록을 받음. 과제를 만든 교수는 조 전부, 학생은 자기 조만 받음

200
```json
{
  "id": 1, "title": "발자국 데모 과제", "startDate": "2026-11-02", "endDate": "2026-12-11",
  "phase": "RECORDING", "evaluationStartedAt": null,
  "professorName": "이교수",
  "teams": [
    { "id": 1, "name": "1조", "leaderName": "김민준" },
    { "id": 2, "name": "2조", "leaderName": "강지우" }
  ]
}
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `assignmentId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 과제를 만든 교수도 아니고 그 과제의 조원도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 과제일 때 |

서버 쪽 규칙
- 접근 검사는 `TeamAccessChecker` 한 곳에서 함(CLAUDE.md "업무 규칙"의 접근 범위)
- 조 목록은 조 id 순서임

### POST /assignments/{assignmentId}/start-evaluation — 평가 시작 (A-3-1) · 2026.11.04

과제를 만든 교수가 과제를 기록 단계에서 평가 단계로 바꿈. 되돌릴 수 없음. 요청 body는 없음. 시작 시각은 서버가 기록함

200: 모양은 `POST /assignments`의 응답과 같음
```json
{ "id": 1, "title": "발자국 데모 과제", "startDate": "2026-11-02", "endDate": "2026-12-11", "phase": "EVALUATION", "evaluationStartedAt": "2026-12-12T09:00:00" }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 이미 평가 단계일 때, `assignmentId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 과제를 만든 교수가 아닐 때(그 과제의 조장, 다른 교수 포함) |
| 404 | `NOT_FOUND` | 없는 과제일 때 |

서버 쪽 규칙

- 평가 단계에서는 조 운영(초대 코드 발급, 합류, 역할 지정)과 산출물 업로드가 400 `INVALID_INPUT`임. 열람은 모두 됨
- 동료평가 제출은 평가 단계에서만 됨. 기록 단계이면 400 `INVALID_INPUT`임
- 검사 순서는 권한 → 단계임. 권한이 없으면 단계와 상관없이 403임
- 단계 검사는 `service/AssignmentPhaseChecker` 한 곳에서 함
- 과제 기간이 지나도 서버는 단계를 바꾸거나 기록을 막지 않음
- 같은 트랜잭션에서 그 과제 모든 조의 조원에게 `EVALUATION_STARTED` 알림을 남김(아래 "알림")

---

## 조

### GET /teams/{teamId} — 조 정보 (T-1, 조 화면) · 2026.10.25

조 이름과 그 조가 속한 과제를 받음. 그 조의 조원과 그 과제를 만든 교수만 부를 수 있음

200
```json
{ "id": 1, "name": "1조", "assignmentId": 1, "assignmentTitle": "발자국 데모 과제", "phase": "RECORDING", "startDate": "2026-11-02", "endDate": "2026-12-11" }
```

- `startDate`, `endDate`: 과제 기간임. 진척 화면(R-4-1)이 이 기간 위에 업로드 시점을 놓음 · 2026.11.05

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도 아니고 그 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### GET /teams/{teamId}/members — 조원 목록 (T-1, T-2) · 2026.10.25

조원 목록을 합류한 순서로 받음. 조장이 맨 앞임. 권한과 오류는 `GET /teams/{teamId}`와 같음

200
```json
[
  { "id": 1, "memberId": 2, "name": "김민준", "roleName": "기획", "leader": true },
  { "id": 2, "memberId": 3, "name": "이서연", "roleName": null, "leader": false }
]
```

- `id`는 조 참여 기록 번호임. 역할 지정(T-2-1)의 주소에 씀. `memberId`는 회원 번호임
- `roleName`은 역할을 배정하기 전이면 `null`임

### GET /teams/{teamId}/invite-code — 초대 코드 보기 (T-1-1) · 2026.10.25

조장이 지금 유효한 초대 코드를 봄. 발급한 적이 없으면 `inviteCode`가 `null`임

200
```json
{ "inviteCode": "3f2b7c1e-8d4a-4c55-9b0e-2a6f1d9e7c10" }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조장이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### POST /teams/{teamId}/invite-code — 초대 코드 발급 (T-1-1) · 2026.10.25

조장이 초대 코드를 새로 발급함. 다시 발급하면 이전 코드는 더 이상 쓸 수 없음. 요청 body는 없음
응답과 오류는 `GET /teams/{teamId}/invite-code`와 같음. 평가 단계이면 400 `INVALID_INPUT`임

화면의 초대 링크는 `<화면 주소>/join/<inviteCode>`임(예: `http://localhost:5173/join/3f2b7c1e-...`)

### GET /teams/join/{inviteCode} — 합류 전 미리보기 (X-4) · 2026.11.12

학생이 초대 링크를 열면 합류하기 전에 어느 과제의 어느 조인지 봄. 합류는 하지 않음(아래 `POST /teams/join`을 따로 부름). 요청 body는 없음

200
```json
{ "assignmentTitle": "발자국 데모 과제", "teamName": "1조", "leaderName": "김민준" }
```

- 과제 제목, 조 이름, 조장 이름만 담음. 조원 수 같은 건수, 조원 목록, 조 번호, 다른 조의 정보는 담지 않음. 초대 코드를 아는 사람에게 필요한 최소만 줌

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 평가 단계인 과제의 조에 새로 합류하려 할 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 학생이 아닐 때 |
| 404 | `NOT_FOUND` | 없는 초대 코드일 때(다시 발급되어 무효가 된 코드 포함) |
| 409 | `DUPLICATE` | 같은 과제의 다른 조에 이미 있을 때(메시지 "이미 이 과제의 다른 조에 있습니다") |

서버 쪽 규칙
- 합류(`POST /teams/join`)와 같은 검사를 같은 순서로 하고 같은 오류를 돌려줌: 초대 코드(404) → 학생인지(403) → 같은 과제의 조 참여(같은 조면 200, 다른 조면 409) → 기록 단계인지(400). 미리보기가 200이면 곧바로 누른 합류도 200임(그 사이에 코드가 다시 발급되거나 평가가 시작되면 합류가 실패함)
- 이미 그 조의 조원이면 단계와 상관없이 200임. 합류도 그 경우 200이라 화면은 같은 버튼을 보여 줌
- 아무것도 바꾸지 않으므로 평가 단계의 쓰기 거부 규칙과 무관함. 평가 단계에서 400인 것은 이어서 누를 합류가 400이기 때문임
- 코드 자리에 조 주소의 끝 글자(`members`, `invite-code` 등)를 넣으면 `GET /teams/{teamId}/members` 같은 주소로 가서 400 `INVALID_INPUT` "주소의 값이 올바르지 않습니다. teamId: 정수여야 합니다"임. 서버가 발급하는 코드는 UUID, 시작 데이터의 코드는 `demo-team-1` 같은 값이라 겹치지 않음

### POST /teams/join — 초대 코드로 합류 (T-1-2) · 2026.10.25

학생이 초대 코드로 그 조에 조원으로 합류함. 역할은 비어 있음(조장이 T-2-1에서 지정)

요청
```json
{ "inviteCode": "3f2b7c1e-8d4a-4c55-9b0e-2a6f1d9e7c10" }
```

200: 합류한 조. 모양은 `GET /teams/{teamId}`와 같음
```json
{ "id": 3, "name": "1조", "assignmentId": 2, "assignmentTitle": "새 과제", "phase": "RECORDING", "startDate": "2026-11-02", "endDate": "2026-12-11" }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `inviteCode`가 비었을 때, 평가 단계인 과제의 조에 새로 합류할 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 학생이 아닐 때 |
| 404 | `NOT_FOUND` | 없는 초대 코드일 때(다시 발급되어 무효가 된 코드 포함) |
| 409 | `DUPLICATE` | 같은 과제의 다른 조에 이미 있을 때. 같은 학생의 합류 요청 두 개가 거의 동시에 와서 unique 제약에 걸린 두 번째 요청도 포함(메시지 "이미 이 과제의 조에 있습니다") |

서버 쪽 규칙
- 이미 그 조의 조원이면 오류 없이 200으로 그 조를 돌려줌(2026.10.25 추천안으로 결정). 링크를 두 번 눌러도 결과가 같음. 아무것도 바꾸지 않으므로 평가 단계에서도 200임
- 한 학생은 같은 과제에서 한 조에만 있음. Service가 먼저 확인하고, `team_member`의 `(assignment_id, member_id)` unique 제약이 동시 요청을 막음(2026.10.30 결정)
- 같은 순간에 같은 조로 두 번 보낸 경우도 두 번째는 409임. 이미 실패한 트랜잭션에서는 어느 조인지 다시 조회할 수 없어서 200으로 돌려주지 않음. 화면은 합류 중 버튼을 막음

### POST /teams/{teamId}/members/{teamMemberId}/role — 역할 지정 (T-2-1) · 2026.10.25

조장이 조원 한 명(조장 자신 포함)에게 역할을 지정함. 역할이 없는 조원에게만 지정할 수 있음. 이미 역할이 있으면 아래 `PUT`으로 바꿈
`teamMemberId`는 조원 목록의 `id`(조 참여 번호)임. 지정 시각은 서버가 기록함

요청
```json
{ "roleName": "백엔드" }
```

200: 역할이 지정된 조원. 모양은 조원 목록의 한 줄과 같음
```json
{ "id": 12, "memberId": 8, "name": "조은우", "roleName": "백엔드", "leader": false }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `roleName`이 비었거나 255자를 넘을 때, 주소의 번호가 숫자가 아닐 때, 평가 단계일 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조장이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조이거나, 그 조의 조원이 아닌 `teamMemberId`일 때 |
| 409 | `DUPLICATE` | 이미 역할이 있는 조원일 때 |

서버 쪽 규칙
- 역할명 앞뒤 공백은 지우고 저장함. 같은 역할을 여러 조원이 맡을 수 있음
- 메서드는 POST임. 첫 지정은 "새로 만든다"에 가깝고, CORS 허용 목록(GET, POST, PUT, DELETE)을 바꾸지 않아도 됨

### PUT /teams/{teamId}/members/{teamMemberId}/role — 역할 변경 (T-2-2) · 2026.11.04

조장이 역할이 있는 조원(조장 자신 포함)의 역할을 바꾸고 이전 역할을 이력으로 남김. 바뀐 시각은 서버가 기록함

요청
```json
{ "roleName": "데이터" }
```

200: 역할이 바뀐 조원. 모양은 조원 목록의 한 줄과 같음
```json
{ "id": 12, "memberId": 8, "name": "조은우", "roleName": "데이터", "leader": false }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `roleName`이 비었거나 255자를 넘을 때, 주소의 번호가 숫자가 아닐 때, 평가 단계일 때, 아직 역할이 없는 조원일 때(메시지 "아직 역할이 지정되지 않은 조원입니다"), 지금과 같은 역할일 때(메시지 "지금과 같은 역할입니다") |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조장이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조이거나, 그 조의 조원이 아닌 `teamMemberId`일 때 |

서버 쪽 규칙
- 역할명 앞뒤 공백은 지우고 비교·저장함
- 이미 올린 산출물은 올릴 때의 역할명을 그대로 가짐. 바꾼 뒤에 올리는 산출물부터 새 역할명이 복사됨
- 조원 목록의 `roleName`은 새 역할명이 됨. `TeamMember.roleAssignedAt`도 바뀐 시각이 됨
- 요청이 겹쳐도 조 행을 잠가 차례로 처리함. 이력의 이전 역할은 바로 앞 요청의 역할임

### PUT /teams/{teamId}/leader — 조장 교체 (X-3) · 2026.11.04

과제를 만든 교수가 같은 조의 다른 조원을 새 조장으로 바꿈. 바뀐 시각은 서버가 기록함

요청: `teamMemberId`는 새 조장의 조 참여 번호(조원 목록의 `id`)임
```json
{ "teamMemberId": 12 }
```

200: 새 조장. 모양은 조원 목록의 한 줄과 같음
```json
{ "id": 12, "memberId": 8, "name": "조은우", "roleName": "백엔드", "leader": true }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamMemberId`가 빠졌거나 숫자가 아닐 때, 주소의 번호가 숫자가 아닐 때, 평가 단계일 때, 이미 그 조의 조장일 때(메시지 "이미 이 조의 조장입니다") |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 과제를 만든 교수가 아닐 때(그 조의 조장 포함) |
| 404 | `NOT_FOUND` | 없는 조이거나, 그 조의 조원이 아닌 `teamMemberId`일 때 |

서버 쪽 규칙
- 이전 조장은 일반 조원으로 남음. 역할, 올린 산출물, 낸 평가는 그대로임
- 역할이 없는 조원도 조장이 될 수 있음. 새 조장이 자기 역할을 지정함
- 조장만 하는 일(초대 코드, 역할 지정·변경)은 바뀐 순간부터 새 조장이 함. 이미 발급한 초대 코드는 그대로 유효함
- 교체 요청끼리 겹치거나 역할 지정·변경과 겹쳐도 조장은 한 명임. 모두 같은 조 행을 잠가 차례로 처리함. 먼저 끝난 교체도 이력에 남음

### GET /teams/{teamId}/leader-histories — 조장 교체 이력 (X-3) · 2026.11.04

한 조의 조장 교체를 최근에 바뀐 것부터 돌려줌. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있음. 평가 단계에서도 볼 수 있음

200: 바뀐 적이 없으면 빈 배열
```json
[
  { "id": 1, "previousLeaderName": "김민준", "newLeaderName": "이서연", "changedAt": "2026-11-04T15:10:00" }
]
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도, 그 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### GET /teams/{teamId}/members/{teamMemberId}/role-histories — 역할 이력 (T-2-2) · 2026.11.04

한 조원의 이전 역할을 최근에 바뀐 것부터 돌려줌. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있음. 평가 단계에서도 볼 수 있음

200: 바뀐 적이 없으면 빈 배열
```json
[
  { "id": 2, "previousRoleName": "데이터", "changedAt": "2026-11-04T15:10:00" },
  { "id": 1, "previousRoleName": "백엔드", "changedAt": "2026-11-04T14:30:00" }
]
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도, 그 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 조이거나, 그 조의 조원이 아닌 `teamMemberId`일 때 |

---

## 산출물

### POST /teams/{teamId}/deliverables — 산출물 업로드 (R-1-1) · 2026.10.25

역할이 배정된 조원이 파일 하나를 올림. 요청은 `multipart/form-data`이고 파일 칸 이름은 `file`임. 고친 파일도 덮어쓰지 않고 새로 올림
올린 사람의 그때 역할명이 산출물에 복사되어 저장됨. 올린 시각은 서버가 기록함

200
```json
{ "id": 3, "roleName": "백엔드", "fileName": "API 명세.pdf", "fileSize": 52344, "uploaderMemberId": 3, "uploaderName": "이서연", "uploadedAt": "2026-11-05T14:03:22", "deletedAt": null }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `file`이 없거나 빈 파일일 때, 요청 전체가 20MB를 넘을 때(파일이 20MB에 가까우면 거절될 수 있음), `multipart/form-data` 요청이 아닐 때, 주소의 번호가 숫자가 아닐 때, 평가 단계일 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함), 역할이 배정되지 않은 조원일 때 |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### GET /teams/{teamId}/deliverables — 산출물 목록 (R-1-2) · 2026.10.25

조의 산출물을 최신순(올린 순서의 역순)으로 받음. 삭제된 산출물도 빠지지 않음. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있음. 오류는 `GET /teams/{teamId}`와 같음

200
```json
[
  { "id": 3, "roleName": "백엔드", "fileName": "API 명세 v2.pdf", "fileSize": 60211, "uploaderMemberId": 8, "uploaderName": "조은우", "uploadedAt": "2026-11-06T10:00:00", "deletedAt": null },
  { "id": 2, "roleName": "백엔드", "fileName": "API 명세.pdf", "fileSize": 52344, "uploaderMemberId": 3, "uploaderName": "이서연", "uploadedAt": "2026-11-05T14:03:22", "deletedAt": "2026-11-06T09:30:00" }
]
```

- 순번(같은 역할 안에서 1, 2, 3…)은 응답에 넣지 않음. 화면이 이 목록에서 같은 `roleName`끼리 올린 순서로 셈(CLAUDE.md "업무 규칙"의 산출물 순번). 삭제된 산출물도 세므로 순번이 끊기지 않음
- 역할별로 묶어 보여 주는 것은 화면이 함. API는 펼친 목록 그대로임
- `uploaderMemberId`는 화면이 내 산출물에만 삭제 버튼을 보여 줄 때 씀
- 조회 수, 다운로드 수는 없음

### GET /deliverables/{deliverableId}/download — 산출물 다운로드 (R-1-2) · 2026.10.25

파일 본문을 받음. 그 조의 조원과 그 과제를 만든 교수가 받을 수 있음

200: 파일 본문. header
- `Content-Type`: 파일 이름의 확장자로 정함. 모르면 `application/octet-stream`
- `Content-Disposition: attachment; filename="=?UTF-8?Q?...?="; filename*=UTF-8''<퍼센트 인코딩한 파일 이름>`. Spring의 `ContentDisposition`이 만듦. 예: `API 명세.txt` → `attachment; filename="=?UTF-8?Q?API_=EB=AA=85=EC=84=B8.txt?="; filename*=UTF-8''API%20%EB%AA%85%EC%84%B8.txt`. 영문 이름이어도 두 값을 모두 붙임
- 화면은 이 header를 읽지 않음. fetch로 받은 파일을 목록의 `fileName`으로 저장함(다른 주소의 응답이라 서버가 header를 노출하지 않으면 읽을 수도 없음)
- 가짜 API는 `filename="API ??.txt"; filename*=...`처럼 한글이 `?`로 바뀐 대체값을 붙이고, 영문 이름에는 `filename*`를 붙이지 않음

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도 아니고 그 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 산출물이거나, 삭제된 산출물이거나(메시지 "삭제된 산출물입니다. id=…"), 파일 본문이 저장소에 없을 때 |

서버 쪽 규칙
- 파일 본문은 MinIO에, 데이터베이스에는 저장 위치(key), 파일 이름, 크기만 둠(+ 올린 조원, 역할명, 시각)
- 업로드는 "MinIO 저장 → 데이터베이스 저장" 순서임. 데이터베이스 저장이 실패하면 올린 파일을 지움(O-7). 그 뒤 커밋 단계에서 실패하는 경우까지 막지는 않음
- 크기 제한은 `spring.servlet.multipart.max-file-size`, `max-request-size` 20MB

### DELETE /deliverables/{deliverableId} — 산출물 삭제 (R-1-3) · 2026.11.04

올린 조원이 기록 단계에서 자기 산출물을 삭제함. MinIO의 파일 본문만 지우고 데이터베이스 행은 남김. 삭제 시각은 서버가 기록함

200: 삭제 시각이 담긴 산출물. 모양은 목록의 한 줄과 같음
```json
{ "id": 3, "roleName": "백엔드", "fileName": "API 명세.pdf", "fileSize": 52344, "uploaderMemberId": 3, "uploaderName": "이서연", "uploadedAt": "2026-11-05T14:03:22", "deletedAt": "2026-11-06T09:30:00" }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때, 평가 단계일 때, 이미 삭제된 산출물일 때(메시지 "이미 삭제된 산출물입니다") |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 올린 조원이 아닐 때(조장, 교수 포함) |
| 404 | `NOT_FOUND` | 없는 산출물일 때 |

서버 쪽 규칙
- 목록에서 빠지지 않고 `deletedAt`이 채워짐. 순번은 그대로임
- 역할이나 조장이 바뀌어도 올린 사람이 지움. 올릴 때의 역할명은 그대로 남음
- 데이터베이스에 삭제 표시를 먼저 반영하고 파일을 지움. 파일 지우기가 실패하면 500이고 삭제 표시도 되돌아감

---

## 기여 항목

파일이 남지 않는 일(회의 진행, 자료 취합 등)의 기록임. 상태 열은 없음. `canceledAt`이 있으면 취소됨, `reportedAt`이 있으면 보고됨, 둘 다 없으면 보고 전임

### POST /teams/{teamId}/contributions — 기여 항목 등록 (R-2-1) · 2026.11.04

조장이 항목명과 담당 조원 한 명을 정해 등록함. 등록 시각은 서버가 기록함

요청
```json
{ "title": "회의록 정리", "teamMemberId": 2 }
```

- `title`: 필수. 255자까지. 앞뒤 공백은 지우고 저장함
- `teamMemberId`: 필수. 담당 조원의 조 참여 번호. 조장 자신도 됨. 역할이 없어도 됨

200: 저장된 항목. 모양은 목록의 한 줄과 같음

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 입력값이 틀렸을 때, 주소의 번호가 숫자가 아닐 때, 평가 단계일 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 조장이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조일 때, 담당이 이 조의 조원이 아닐 때 |

서버 쪽 규칙
- 등록한 항목은 고칠 수 없음
- 같은 트랜잭션에서 담당 조원에게 `CONTRIBUTION_ASSIGNED` 알림을 남김. 조장이 자신을 담당으로 정하면 남기지 않음(아래 "알림")

### GET /teams/{teamId}/contributions — 기여 항목 목록 (R-2-2) · 2026.11.04

조원과 그 과제를 만든 교수가 봄. 최근에 등록한 것부터 돌려줌. 평가 단계에서도 봄

200
```json
[
  { "id": 3, "title": "회의록 정리", "assigneeMemberId": 3, "assigneeName": "이서연", "createdAt": "2026-11-05T14:03:22", "reportMemo": null, "reportedAt": null, "canceledAt": null }
]
```

- `assigneeMemberId`: 담당 조원의 회원 번호. 화면이 내가 맡은 항목을 알아볼 때 씀

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도 그 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 조일 때 |

서버 쪽 규칙
- 취소된 항목도 목록에서 빼지 않음

### POST /contributions/{contributionId}/report — 완료 보고 (R-2-3) · 2026.11.04

담당 조원 본인이 기록 단계에서 한 번만 보고함. 보고 시각은 서버가 기록함

요청
```json
{ "memo": "회의록을 정리해 조 공유 폴더에 올렸습니다." }
```

- `memo`: 필수. 2000자까지. 앞뒤 공백은 지우고 저장함

200: 메모와 보고 시각이 담긴 항목. 모양은 목록의 한 줄과 같음

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 입력값이 틀렸을 때, 주소의 번호가 숫자가 아닐 때, 평가 단계일 때, 취소된 항목일 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 담당 조원이 아닐 때(조장, 교수 포함) |
| 404 | `NOT_FOUND` | 없는 항목일 때 |
| 409 | `DUPLICATE` | 이미 보고했을 때 |

서버 쪽 규칙
- 메모는 보고한 뒤 고칠 수 없음
- 보고 요청이 겹쳐도 항목 행을 잠가 하나만 저장됨. 나머지는 409임
- 같은 트랜잭션에서 같은 조의 다른 조원 전원에게 `CONTRIBUTION_REPORTED` 알림을 남김. 메모는 담지 않음(아래 "알림")

### POST /contributions/{contributionId}/cancel — 기여 항목 취소 (R-2-4) · 2026.11.04

조장이 기록 단계에서 보고 전 항목을 취소함. 요청 body는 없음. 취소 시각은 서버가 기록함. 고치고 싶으면 취소하고 다시 등록함

200: 취소 시각이 담긴 항목. 모양은 목록의 한 줄과 같음

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때, 평가 단계일 때, 이미 취소됐을 때, 이미 보고한 항목일 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 조장이 아닐 때(담당 조원, 교수 포함) |
| 404 | `NOT_FOUND` | 없는 항목일 때 |

서버 쪽 규칙
- 목록에서 빠지지 않고 `canceledAt`이 채워짐
- 보고와 취소가 겹쳐도 항목 행을 잠가 하나만 성공함
- 조장이 바뀌면 새 조장이 취소함

## 완료확인

### POST /deliverables/{deliverableId}/confirmations — 산출물 완료확인 (R-3-1) · 2026.11.04

올린 조원을 뺀 같은 조 조원이 기록 단계에서 확인함. 요청 body는 없음. 확인 시각은 서버가 기록함

200
```json
{
  "id": 1,
  "deliverableId": 1,
  "contributionId": null,
  "confirmerMemberId": 3,
  "confirmerName": "이서연",
  "confirmedAt": "2026-11-05T14:03:22",
  "canceledAt": null
}
```

- `deliverableId`, `contributionId`: 둘 중 하나만 값이 있음
- `confirmerMemberId`, `confirmerName`: 확인한 조원. 회원 번호와 이름으로 펼침

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때, 평가 단계일 때, 삭제된 산출물일 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함), 자기가 올린 산출물일 때 |
| 404 | `NOT_FOUND` | 없는 산출물일 때 |
| 409 | `DUPLICATE` | 이 조원의 유효한 확인이 이미 있을 때 |

서버 쪽 규칙
- 한 기록에 여러 조원이 확인할 수 있으나 한 조원의 유효한 확인은 하나임
- 요청이 겹쳐도 대상 행을 잠가 하나만 저장됨. 나머지는 409임
- 건수를 세거나 점수로 바꾸지 않음. 응답에도 건수가 없음

### POST /contributions/{contributionId}/confirmations — 기여 항목 완료확인 (R-3-1) · 2026.11.04

담당 조원을 뺀 같은 조 조원이 기록 단계에서 보고된 항목을 확인함. 요청 body는 없음

200: 산출물 완료확인과 같은 모양. `contributionId`에 값이 있고 `deliverableId`는 null임

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때, 평가 단계일 때, 취소된 항목일 때, 보고 전 항목일 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함), 자기가 맡은 항목일 때 |
| 404 | `NOT_FOUND` | 없는 항목일 때 |
| 409 | `DUPLICATE` | 이 조원의 유효한 확인이 이미 있을 때 |

### POST /confirmations/{confirmationId}/cancel — 완료확인 취소 (R-3-2) · 2026.11.04

확인한 조원 본인이 기록 단계에서 자기 확인을 취소함. 요청 body는 없음. 취소 시각은 서버가 기록함

200: 취소 시각이 담긴 확인. 모양은 완료확인 응답과 같음

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때, 평가 단계일 때, 이미 취소했을 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 확인한 조원이 아닐 때(올린 조원, 조장, 교수 포함) |
| 404 | `NOT_FOUND` | 없는 확인일 때 |

서버 쪽 규칙
- 이력에서 빠지지 않고 `canceledAt`이 채워짐
- 취소한 뒤 다시 확인할 수 있고, 다시 한 확인은 새 번호의 새 기록임
- 삭제된 산출물의 확인도 취소할 수 있음. 그 뒤 다시 확인하지는 못함
- 취소 요청이 겹쳐도 행을 잠가 하나만 성공함

### GET /deliverables/{deliverableId}/confirmations — 산출물 완료확인 이력 (R-3-3) · 2026.11.04

### GET /contributions/{contributionId}/confirmations — 기여 항목 완료확인 이력 (R-3-3) · 2026.11.04

그 조의 조원과 그 과제를 만든 교수가 봄. 최근 것부터 줌. 확인이 없으면 빈 배열임

200: 완료확인 응답의 배열

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도, 그 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 산출물이나 항목일 때 |

서버 쪽 규칙
- 삭제된 산출물의 이력도 그대로 줌
- 평가 단계에도 그대로 줌

---

## 동료평가

평가 항목 6개(신태섭 2018, 문장은 `frontend/src/constants/peerEvaluation.js`)는 요청·저장에서 아래 이름을 씀. 점수는 1~5 정수임

| 이름 | 항목 |
|---|---|
| `goalSharing` | 목표 공유·협업 |
| `roleFulfillment` | 역할 수행 |
| `encouragement` | 격려·도움 |
| `onlineCommunication` | 온라인 소통 |
| `offlineCommunication` | 대면 소통 |
| `progressCheck` | 진행 점검·보충 |

시스템은 점수를 합치거나 평균 내지 않음. 어떤 응답에도 합계·평균·순위가 없음

### GET /teams/{teamId}/evaluations/mine — 내 평가 제출 여부 (E-2) · 2026.10.25

내가 이 조에서 동료평가를 제출했는지 받음. 점수는 돌려주지 않음. 그 조의 조원만 부를 수 있음

200
```json
{ "submitted": true, "submittedAt": "2026-11-13T21:10:05" }
```

제출 전이면 `{ "submitted": false, "submittedAt": null }`

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### POST /teams/{teamId}/evaluations — 동료평가 제출 (E-2-1, E-2-3) · 2026.10.25

같은 조 조원 중 자신을 뺀 **전원**의 평가를 한 번에 제출함. 제출한 평가는 고칠 수 없음. 제출 시각은 서버가 기록함
`targetTeamMemberId`는 조원 목록(`GET /teams/{teamId}/members`)의 `id`임. `comment`(서술 의견)는 선택이고 2000자까지임

요청
```json
{
  "evaluations": [
    { "targetTeamMemberId": 2, "goalSharing": 5, "roleFulfillment": 4, "encouragement": 4,
      "onlineCommunication": 5, "offlineCommunication": 3, "progressCheck": 4, "comment": "API 명세를 먼저 정리해 줌" },
    { "targetTeamMemberId": 3, "goalSharing": 3, "roleFulfillment": 3, "encouragement": 3,
      "onlineCommunication": 3, "offlineCommunication": 3, "progressCheck": 3, "comment": null }
  ]
}
```

200: 제출 여부. 모양은 `GET /teams/{teamId}/evaluations/mine`과 같음

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `evaluations`가 비었거나, 원소가 null이거나, `targetTeamMemberId`나 점수가 빠졌거나, 점수가 1~5의 정수가 아니거나(`4.5`, `"5"` 포함, 위 "공통"), `comment`가 2000자를 넘을 때 |
| 400 | `INVALID_INPUT` | 조에 자신만 있어서 평가할 조원이 없을 때. 메시지 "평가할 조원이 없습니다. 조에 자신만 있습니다" |
| 400 | `INVALID_INPUT` | 대상이 "자신을 뺀 조원 전원 한 번씩"이 아닐 때(빠짐, 중복, 자기 자신, 다른 조 조원) |
| 400 | `INVALID_INPUT` | 기록 단계일 때. 메시지 "동료평가는 평가 단계에서만 입력할 수 있습니다" |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조일 때 |
| 409 | `DUPLICATE` | 이미 제출했을 때(두 요청을 거의 동시에 보낸 두 번째 요청 포함) |

서버 쪽 규칙
- 조에 자신만 있으면 평가할 대상이 없어서 제출할 것이 없음. 제출 여부는 계속 `submitted: false`임. 화면은 입력 폼 대신 "평가할 조원이 없습니다."를 보여 주고 이 요청을 보내지 않음(2026.10.30 결정)
- 평가는 회원이 아니라 조 참여(`TeamMember`)를 평가자·대상으로 참조함. (평가자, 대상)에 데이터베이스 unique 제약이 있음
- 평가와 의견은 과제를 만든 교수만 열람함(아래 "평가 결과"). 조원끼리는 서로의 평가를 볼 수 없음(조원용 조회 API 없음)
- 제출에 성공하면 그 평가자의 임시 저장을 같은 트랜잭션에서 지움. 제출에 실패하면 임시 저장은 그대로임
- 제출과 임시 저장은 조 행을 잠가 한 번에 하나씩 처리함

### GET /teams/{teamId}/evaluations/draft — 내 평가 임시 저장 불러오기 (E-2-2) · 2026.11.04

내가 이 조에서 임시 저장한 내용을 받음. 다른 조원의 임시 저장은 받을 수 없음. 그 조의 조원만 부를 수 있고 교수는 403임

200
```json
[
  { "targetTeamMemberId": 2, "goalSharing": 5, "roleFulfillment": null, "encouragement": null,
    "onlineCommunication": null, "offlineCommunication": null, "progressCheck": null,
    "comment": "API 명세를 먼저 정리해 줌", "savedAt": "2026-11-13T21:05:00" }
]
```

임시 저장이 없으면 `[]`임. 기록 단계와 제출한 뒤에도 `[]`임

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### PUT /teams/{teamId}/evaluations/draft — 내 평가 임시 저장 (E-2-2) · 2026.11.04

지금까지 입력한 내용을 저장함. 조원 일부만 보내도 되고 점수도 일부만 채워도 됨. 저장 시각은 서버가 기록함
보낸 내용이 이전 임시 저장 **전체**를 대신함. 보내지 않은 조원의 임시 저장은 사라짐. `evaluations`가 `[]`이면 모두 지움

요청
```json
{
  "evaluations": [
    { "targetTeamMemberId": 2, "goalSharing": 5, "roleFulfillment": null, "encouragement": null,
      "onlineCommunication": null, "offlineCommunication": null, "progressCheck": null,
      "comment": "API 명세를 먼저 정리해 줌" }
  ]
}
```

200: 저장한 내용. 모양은 `GET /teams/{teamId}/evaluations/draft`와 같음

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `evaluations`가 빠졌거나, 원소가 null이거나, `targetTeamMemberId`가 빠졌거나, 값이 있는 점수가 1~5의 정수가 아니거나(`4.5`, `"5"` 포함, 위 "공통"), `comment`가 2000자를 넘을 때 |
| 400 | `INVALID_INPUT` | 같은 조원이 두 번 있거나, 자기 자신이나 다른 조 조원일 때 |
| 400 | `INVALID_INPUT` | 기록 단계일 때. 메시지 "동료평가는 평가 단계에서만 입력할 수 있습니다" |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조일 때 |
| 409 | `DUPLICATE` | 이미 제출했을 때 |

서버 쪽 규칙
- 임시 저장은 제출본과 다른 테이블(`EvaluationDraft`)에 저장함. 제출본 `Evaluation`의 점수는 그대로 NOT NULL임
- 임시 저장만 있으면 제출 여부는 계속 `submitted: false`임
- 교수가 임시 저장을 볼 수 있는 API는 없음
- `comment`는 앞뒤 공백을 지우고, 비었으면 null로 저장함

## 평가 결과

세 API 모두 과제를 만든 교수만 부를 수 있음. 조원은 제출한 본인과 평가받은 대상도 403임. 기록 단계에도 부를 수 있고 제출이 없으니 미제출과 빈 결과가 옴

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 과제를 만든 교수가 아닐 때. 메시지 "과제를 만든 교수만 볼 수 있습니다" |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### GET /teams/{teamId}/evaluations/status — 조원별 제출 여부 (V-2-1) · 2026.11.04

조원 전원을 조원 번호 순으로 돌려줌. 몇 명이 제출했는지는 담지 않음. 임시 저장만 했으면 미제출임

200
```json
[
  { "teamMemberId": 11, "name": "김민준", "submitted": true, "submittedAt": "2026-11-13T21:10:05" },
  { "teamMemberId": 12, "name": "이서연", "submitted": false, "submittedAt": null }
]
```

### GET /teams/{teamId}/evaluations — 평가 결과 (V-2-2) · 2026.11.04

평가자별 점수와 의견을 입력된 그대로 제출 순으로 돌려줌. 평가자 한 명이 대상 한 명에게 준 평가가 한 줄임. 합계, 평균, 순위는 없음. 임시 저장은 나오지 않음

200
```json
[
  { "id": 1, "evaluatorTeamMemberId": 11, "evaluatorName": "김민준", "targetTeamMemberId": 12, "targetName": "이서연",
    "goalSharing": 5, "roleFulfillment": 4, "encouragement": 5, "onlineCommunication": 3, "offlineCommunication": 4,
    "progressCheck": 5, "comment": "자료를 잘 정리해 줬습니다.", "submittedAt": "2026-11-13T21:10:05" }
]
```

- `comment`: 쓰지 않았으면 null임
- 제출이 없으면 `[]`임

### GET /teams/{teamId}/evaluations/export — 평가 결과 CSV (X-5) · 2026.11.04

평가 결과를 조 단위 CSV 파일로 내려줌. header는 `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="evaluations-team-3.csv"`

200
```
평가자,대상,목표 공유·협업,역할 수행,격려·도움,온라인 소통,대면 소통,진행 점검·보충,의견,제출 시각
김민준,이서연,5,4,5,3,4,5,자료를 잘 정리해 줬습니다.,2026-11-13 21:10:05
```

- 맨 앞에 UTF-8 BOM이 있음. Excel이 한글을 읽는 데 필요함
- 줄 끝은 CRLF임. 제출이 없으면 제목 줄만 있음
- 합계, 평균, 순위 열은 없음
- 이름과 의견이 `=`, `+`, `-`, `@`로 시작하면 수식으로 실행되지 않게 앞에 `'`를 붙임
- 쉼표, 큰따옴표, 줄바꿈이 있는 칸은 큰따옴표로 감싸고 안의 큰따옴표는 두 번 씀

## 근거 패널

### GET /teams/{teamId}/members/{teamMemberId}/evidence — 조원의 근거 기록 (E-1-1) · 2026.11.04

한 조원의 기록을 종류별로 받음. 과제를 만든 교수는 단계와 상관없이, 같은 조 조원은 평가 단계에서만 부를 수 있음. 자기 기록도 볼 수 있음
`teamMemberId`는 조원 목록(`GET /teams/{teamId}/members`)의 `id`임

200
```json
{
  "deliverables": [
    { "id": 3, "roleName": "백엔드", "fileName": "최종 보고서.txt", "fileSize": 44, "uploaderMemberId": 3,
      "uploaderName": "이서연", "uploadedAt": "2026-11-13T21:00:00", "deletedAt": null }
  ],
  "contributions": [
    { "id": 2, "title": "자료 취합", "assigneeMemberId": 3, "assigneeName": "이서연", "createdAt": "2026-11-13T21:00:00",
      "reportMemo": "자료를 모아 조 공유 폴더에 올렸습니다.", "reportedAt": "2026-11-13T21:00:00", "canceledAt": null }
  ],
  "receivedConfirmations": [
    { "id": 1, "deliverable": { "id": 3, "fileName": "최종 보고서.txt" }, "contribution": null, "confirmerMemberId": 2,
      "confirmerName": "김민준", "confirmedAt": "2026-11-13T21:00:00", "canceledAt": null }
  ],
  "givenConfirmations": []
}
```

- `deliverables`: 이 조원이 올린 산출물. 모양은 산출물 목록과 같음. 삭제된 산출물도 `deletedAt`과 함께 나옴
- `contributions`: 이 조원이 맡은 기여 항목과 보고 메모. 모양은 기여 항목 목록과 같음. 취소된 항목도 `canceledAt`과 함께 나옴
- `receivedConfirmations`: 이 조원의 기록에 다른 조원이 남긴 완료확인
- `givenConfirmations`: 이 조원이 다른 조원의 기록에 남긴 완료확인
- 완료확인의 `deliverable`, `contribution`은 위 두 목록과 같은 모양의 기록 전체임(위 예시는 줄임). 둘 중 하나만 값이 있고 나머지는 null임. 취소된 확인도 `canceledAt`과 함께 나옴
- 네 목록 모두 최근 것부터임. 기록이 없으면 `[]`임

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamId`나 `teamMemberId`가 숫자가 아닐 때 |
| 400 | `INVALID_INPUT` | 조원이 기록 단계에서 부를 때. 메시지 "근거 패널은 평가 단계에서 볼 수 있습니다" |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도, 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 조이거나, 그 조의 조원이 아닐 때 |

서버 쪽 규칙
- 건수, 합계, 점수를 담지 않음. 기록을 있는 그대로 돌려줌
- 동료평가와 임시 저장은 담지 않음
- 파일 내려받기와 기록별 완료확인 이력은 기존 API를 씀

---

## 기록 열람

### GET /teams/{teamId}/records — 조별 기록 (V-1-1) · 2026.11.04

한 조의 기록을 한 줄씩 펼쳐 최근 것부터 돌려줌. 그 조의 조원과 그 과제를 만든 교수가 기록 단계와 평가 단계 모두 부를 수 있음

200
```json
[
  { "type": "CONFIRMATION_CREATED", "occurredAt": "2026-11-13T21:00:00", "teamMemberId": 11, "memberName": "김민준",
    "targetTeamMemberId": 12, "targetMemberName": "이서연", "title": "최종 보고서.txt", "roleName": null,
    "previousRoleName": null, "deliverableId": 3, "contributionId": null },
  { "type": "ROLE_CHANGED", "occurredAt": "2026-11-13T20:00:00", "teamMemberId": 12, "memberName": "이서연",
    "targetTeamMemberId": null, "targetMemberName": null, "title": null, "roleName": "백엔드",
    "previousRoleName": "기획", "deliverableId": null, "contributionId": null }
]
```

| `type` | 뜻 | `teamMemberId` | `target...` | `title` | `roleName` |
|---|---|---|---|---|---|
| `DELIVERABLE_UPLOADED` | 산출물 업로드 | 올린 조원 | | 파일 이름 | 올릴 때의 역할명 |
| `DELIVERABLE_DELETED` | 산출물 삭제 | 올린 조원 | | 파일 이름 | 올릴 때의 역할명 |
| `CONTRIBUTION_CREATED` | 기여 항목 등록 | 담당 조원 | | 항목명 | |
| `CONTRIBUTION_REPORTED` | 완료 보고 | 담당 조원 | | 항목명 | |
| `CONTRIBUTION_CANCELED` | 기여 항목 취소 | 담당 조원 | | 항목명 | |
| `CONFIRMATION_CREATED` | 완료확인 | 확인한 조원 | 기록의 주인 | 파일 이름 또는 항목명 | |
| `CONFIRMATION_CANCELED` | 완료확인 취소 | 확인한 조원 | 기록의 주인 | 파일 이름 또는 항목명 | |
| `ROLE_ASSIGNED` | 첫 역할 지정 | 그 조원 | | | 처음 역할 |
| `ROLE_CHANGED` | 역할 변경 | 그 조원 | | | 새 역할. 이전 역할은 `previousRoleName` |
| `LEADER_CHANGED` | 조장 교체 | 새 조장 | 이전 조장 | | |
| `EVALUATION_STARTED` | 평가 시작 | 없음 | | | |

- `occurredAt`: 그 일이 일어난 시각. 모두 서버가 저장한 값임
- `teamMemberId`, `targetTeamMemberId`: 조원 목록(`GET /teams/{teamId}/members`)의 `id`임. 화면의 조원별 보기가 이 값으로 고름
- `deliverableId`, `contributionId`: 줄의 기록 번호. 산출물과 완료확인, 기여 항목과 완료확인의 줄에 있음. 그 밖에는 null임
- 값이 없는 칸은 null임. 칸을 빼지 않음. 기록이 없으면 `[]`임
- 기여 항목의 등록과 취소는 조장이 하지만 누가 했는지 저장하지 않아서 담당 조원을 담음
- 시각이 같으면 평가 시작, 조장 교체, 완료확인, 기여 항목, 산출물, 역할 순임. 한 기록 안에서는 나중 단계가 위임

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도, 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 조일 때 |

서버 쪽 규칙
- 건수, 합계, 점수를 담지 않음. 기록을 있는 그대로 돌려줌
- 평가 제출과 임시 저장은 담지 않음
- 조원 합류와 초대 코드 발급은 시각을 저장하지 않아서 담지 않음

---

## 알림

평가 시작, 기여 항목 담당 지정, 완료 보고 때 서버가 받을 회원마다 한 줄씩 저장함. 화면이 불러올 때 조회함(실시간 전송 안 함)

| `type` | 언제 생김 | 받는 회원 |
|---|---|---|
| `EVALUATION_STARTED` | 교수가 평가를 시작함 | 그 과제의 모든 조의 조원(조장 포함) |
| `CONTRIBUTION_ASSIGNED` | 조장이 기여 항목을 등록함 | 담당 조원. 조장이 자신을 담당으로 정하면 만들지 않음 |
| `CONTRIBUTION_REPORTED` | 담당 조원이 완료 보고함 | 같은 조에서 보고한 조원을 뺀 조원 전원(조장 포함) |

### GET /notifications — 내 알림 목록 (X-6) · 2026.11.12

로그인한 회원이 받은 알림을 최근 것부터 돌려줌. 다른 회원의 알림은 담지 않음. 단계와 상관없이 봄

200
```json
[
  { "id": 7, "type": "CONTRIBUTION_ASSIGNED", "message": "[발자국 데모 과제 1조] 기여 항목 \"회의록 정리\"의 담당이 되었습니다.",
    "teamId": 1, "createdAt": "2026-11-12T10:03:22", "readAt": null }
]
```

- `message`: 서버가 알림을 만들 때 쓴 문장. 앞에 "[과제 제목 조 이름]"이 붙음. 과제 제목, 조 이름, 항목명, 조원 이름만 들어감
- `teamId`: 알림이 생긴 조. 화면이 누르면 갈 곳을 정할 때 씀
- `readAt`: 읽은 시각. 읽기 전에는 null임
- 알림이 없으면 `[]`임

| 상태 | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |

서버 쪽 규칙
- 점수, 평가 의견, 완료 보고 메모는 담지 않음
- 안 읽은 개수 같은 건수 칸은 두지 않음. 화면은 `readAt`이 null인 줄이 있는지만 봄
- 만든 시각과 읽은 시각은 서버가 저장함

### POST /notifications/{notificationId}/read — 알림 읽음 표시 (X-6) · 2026.11.12

받은 회원 본인이 알림 하나를 읽음으로 표시함. 요청 body는 없음

200: 읽은 시각이 담긴 알림. 모양은 목록의 한 줄과 같음

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `notificationId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 다른 회원의 알림일 때 |
| 404 | `NOT_FOUND` | 없는 알림일 때 |

서버 쪽 규칙
- 이미 읽은 알림을 다시 표시해도 200이고, 처음 읽은 시각을 그대로 둠
- 알림은 지우지 않음

---

## 상태 확인

서버를 돌리는 사람과 docker의 healthcheck가 쓰는 주소임. 화면은 부르지 않음. Spring Boot Actuator가 만드는 주소라 응답 모양이 다른 API와 다름(아래 "다른 API와 다른 점")

### GET /actuator/health — 상태 확인 (O-8) · 2026.11.12

token 없이 부름. 서버가 켜져 있고 데이터베이스에 닿는지를 `UP`/`DOWN` 하나로 알려 줌

200
```json
{ "status": "UP" }
```

503
```json
{ "status": "DOWN" }
```

- `Content-Type`은 `application/vnd.spring-boot.actuator.v3+json`임(JSON임)
- `status` 칸 하나만 있음. 데이터베이스 종류, 디스크 경로·남은 용량, 버전, 항목별 상태는 담지 않음. 개발 설정과 운영 설정이 같음
- `DOWN`이 되는 경우: 데이터베이스에 연결하지 못함, 서버가 도는 디스크의 남은 용량이 10MB보다 작음
  - 데이터베이스가 꺼져 있으면 연결을 기다리느라 503이 **30초 뒤에** 옴(연결 풀의 기다리는 시간 기본값)
- MinIO는 보지 않음. MinIO가 꺼져도 `UP`임. MinIO 상태는 MinIO 컨테이너의 healthcheck(`docker compose ps`)로 봄. 그동안 업로드·다운로드는 500 `SERVER_ERROR`임

| 상태 | code | 언제 |
|---|---|---|
| 503 | 없음. body는 `{ "status": "DOWN" }` | 데이터베이스에 닿지 않거나 디스크가 거의 찼을 때 |
| 404 | `NOT_FOUND` | GET이 아닌 method일 때. 다른 주소와 같은 "없는 주소입니다" |

다른 API와 다른 점
- 503과 `{ status }`는 오류 응답 `{ code, message }`가 아님. 사람이 아니라 docker와 운영자가 상태 코드로 판단하는 주소라 actuator 모양을 그대로 둠. 새 `code`는 만들지 않음
- 다른 actuator 주소(`/actuator`, `/actuator/env`, `/actuator/beans`, `/actuator/metrics`, `/actuator/info` 등)는 열지 않음. token이 없으면 401 `UNAUTHORIZED`, 있으면 404 `NOT_FOUND` "없는 주소입니다"(다른 없는 주소와 같음)
- 항목별 주소(`/actuator/health/db`, `/actuator/health/diskSpace`)도 token이 없으면 401 `UNAUTHORIZED`. token이 있으면 **body가 빈 404**임. actuator가 직접 내는 응답이라 `{ code, message }`가 붙지 않음. 항목별 상태는 내보내지 않으므로 어느 쪽이든 정보는 나가지 않음
- 가짜 API에는 이 주소가 없음(화면이 부르지 않음)

---

## 완성 범위에서 더할 API · 2026.11.04

아직 만들지 않은 API의 주소 추천안을 적던 절임(관리자 결정 2026.11.04 "최대한 완성", 추가 기능 필수·권장 포함). 순서 20(O-8)까지 모두 만들어 위쪽에 옮겼고(2026.11.12) 남은 API는 없음. 남은 단위(O-3 배포 구성, O-4 백업·복구)는 API를 더하지 않음. 새로 더할 API가 생기면 이 절에 표를 다시 두고, 만들 때 위쪽에 요청·응답·오류 표를 적고 표에서 그 줄을 지움. 규칙은 `TODO.md` 1절

이미 있는 API의 응답이 바뀌는 곳

- 새로 만드는 조 운영·과정 기록 쓰기 요청도 평가 단계에서 400 `INVALID_INPUT`
- 어떤 응답에도 합계, 평균, 순위, 진척률, 근거 패널의 건수를 넣지 않음
