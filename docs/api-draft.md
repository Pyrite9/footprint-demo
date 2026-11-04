# API 초안

`../footprint-study/docs/api.md`가 생기기 전까지 쓰는 데모의 API 초안임. Controller를 만들기 전에 여기에 먼저 적고, 확인을 기다리지 않고 바로 만듦(2026.10.25 관리자 결정). 정한 이유는 `docs/worklog.md`의 그 단위 항목 "결정" 줄에 남김. 제목 끝의 날짜는 그 API를 정한 날임. "확인 2026.10.25"는 이 결정 전에 관리자 확인을 받은 API, 날짜만 있으면 확인 없이 추천안으로 만든 API라는 표시임

공통
- 오류 응답은 모두 `{ "code": "...", "message": "..." }`. `code`는 `NOT_FOUND` 404, `INVALID_INPUT` 400, `UNAUTHORIZED` 401, `FORBIDDEN` 403, `DUPLICATE` 409, `SERVER_ERROR` 500
- `/auth/**` 말고는 모두 header에 `Authorization: Bearer <accessToken>`이 필요함. 없거나 틀리면 401 `UNAUTHORIZED`
- 없는 주소, 또는 주소는 있지만 그 method가 없는 요청(예: `DELETE /assignments/1`)은 404 `NOT_FOUND` "없는 주소입니다"(2026.10.25 결정). token이 없으면 그보다 먼저 401임
- 서버 내부 오류는 500 `SERVER_ERROR` "서버에서 오류가 났습니다"(2026.10.26 관리자 결정). 원인(예외 내용)은 응답에 담지 않고 서버 로그에만 남김
- 위 code에 들어가지 않는 요청 오류(예: 지원하지 않는 Content-Type)는 400 `INVALID_INPUT` "요청의 형식이 올바르지 않습니다"
- 요청 body의 값은 타입이 정확해야 함(2026.10.30 결정). 숫자 필드(점수, id)에 소수(`4.5`, `5.0`)나 문자열(`"5"`)을 보내면 400 `INVALID_INPUT` "요청 body의 형식이 올바르지 않습니다". 서버가 비슷한 값으로 바꿔 받지 않음. 가짜 API는 `5.0`을 `5`로 받음(JavaScript가 JSON을 읽으면 둘이 같은 값이 됨). 화면은 정수만 보내므로 차이가 드러나지 않음

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

token
- 서명 HS256. 유효 시간 24시간(`jwt.expiration-minutes`). refresh token은 없음
- payload: `sub` = 회원 id(문자열), `memberType` = `PROFESSOR` 또는 `STUDENT`
- 화면은 token을 풀어 읽지 않음. 회원 정보는 `GET /members/me`로 받음
- 가짜 API의 token은 JWT가 아니라 문자열 `mock-token-<회원 id>`이고 만료가 없음
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
- 회원 유형: 이메일이 `google.professor-emails`(쉼표로 구분한 목록)에 있으면 `PROFESSOR`, 아니면 `STUDENT`. 대소문자는 가리지 않음. 처음 만들 때만 정하고, 나중에 목록을 바꿔도 이미 있는 회원의 유형은 바뀌지 않음(X-1에서 바뀜. `TODO.md` 1절)
- 비밀번호가 없는 회원(구글로 만든 회원)은 `POST /auth/login`으로 로그인할 수 없음(401)
- 가짜 API는 구글 서명을 확인하지 못함. payload만 풀어 같은 규칙(`email_verified`, `hd`, 교수 목록)을 적용함

---

## 회원

### GET /members/me — 내 정보 (M-2-1) · 확인 2026.10.25

로그인한 회원 자신의 정보를 받음. token이 필요함

200
```json
{ "id": 2, "email": "student01@example.com", "name": "김민준", "memberType": "STUDENT" }
```

| 상태 | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때. 셋을 구분하지 않고 같은 메시지를 줌 |
| 404 | `NOT_FOUND` | token의 회원 id가 DB에 없을 때(시작 데이터 순서를 바꾼 뒤 예전 token을 쓴 경우). 가짜 API는 이때 401임 |

서버 쪽 규칙
- Controller는 로그인한 회원을 `@AuthenticationPrincipal Long memberId`로 받음(2026.10.25 결정). 회원 유형이 필요하면 Service가 DB에서 읽음

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

---

## 조

### GET /teams/{teamId} — 조 정보 (T-1, 조 화면) · 2026.10.25

조 이름과 그 조가 속한 과제를 받음. 그 조의 조원과 그 과제를 만든 교수만 부를 수 있음

200
```json
{ "id": 1, "name": "1조", "assignmentId": 1, "assignmentTitle": "발자국 데모 과제", "phase": "RECORDING" }
```

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

### POST /teams/join — 초대 코드로 합류 (T-1-2) · 2026.10.25

학생이 초대 코드로 그 조에 조원으로 합류함. 역할은 비어 있음(조장이 T-2-1에서 지정)

요청
```json
{ "inviteCode": "3f2b7c1e-8d4a-4c55-9b0e-2a6f1d9e7c10" }
```

200: 합류한 조. 모양은 `GET /teams/{teamId}`와 같음
```json
{ "id": 3, "name": "1조", "assignmentId": 2, "assignmentTitle": "새 과제", "phase": "RECORDING" }
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
- 요청 두 개가 겹쳐도 조장은 한 명임. 조 행을 잠가 차례로 처리함. 먼저 끝난 교체도 이력에 남음

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
| 409 | `DUPLICATE` | 이미 제출했을 때(두 요청을 거의 동시에 보내 unique 제약에 걸린 두 번째 요청 포함) |

서버 쪽 규칙
- 조에 자신만 있으면 평가할 대상이 없어서 제출할 것이 없음. 제출 여부는 계속 `submitted: false`임. 화면은 입력 폼 대신 "평가할 조원이 없습니다."를 보여 주고 이 요청을 보내지 않음(2026.10.30 결정)
- 평가는 회원이 아니라 조 참여(`TeamMember`)를 평가자·대상으로 참조함. (평가자, 대상)에 데이터베이스 unique 제약이 있음
- 평가와 의견은 과제를 만든 교수만 열람함. 교수 열람 화면·API(V-2)는 아직 없고 지금은 저장까지임. 조원끼리는 서로의 평가를 볼 수 없음(조회 API 없음)

---

## 완성 범위에서 더할 API · 2026.11.04

아직 만들지 않은 API의 주소 추천안임(관리자 결정 2026.11.04 "최대한 완성", 추가 기능 필수·권장 포함). 위 절과 달리 **지금 부르면 404임.** 만들 때 위쪽에 요청·응답·오류 표를 적고 이 표에서 그 줄을 지움. 규칙은 `TODO.md` 1절

| 순서 | ID | method 주소 | 누가 |
|---|---|---|---|
| 8 | R-3-2 | `POST /confirmations/{confirmationId}/cancel` | 확인한 조원 |
| 9 | E-2-2 | `GET /teams/{teamId}/evaluations/draft` | 조원 |
| 9 | E-2-2 | `PUT /teams/{teamId}/evaluations/draft` | 조원 |
| 10 | E-1-1 | `GET /teams/{teamId}/members/{teamMemberId}/evidence` | 조원(평가 단계), 교수 |
| 11 | V-1-1 | `GET /teams/{teamId}/records` | 조원, 교수 |
| 12 | V-2-1 | `GET /teams/{teamId}/evaluations/status` | 교수 |
| 12 | V-2-2 | `GET /teams/{teamId}/evaluations` | 교수 |
| 12 | X-5 | `GET /teams/{teamId}/evaluations/export` | 교수 |
| 14 | X-6 | `GET /notifications` | 받는 회원 |
| 14 | X-6 | `POST /notifications/{notificationId}/read` | 받는 회원 |
| 15 | X-4 | `GET /teams/join/{inviteCode}` | 학생 |
| 18 | X-2 | `POST /members/me/privacy-consent` | 회원 |
| 20 | O-8 | `GET /actuator/health` | token 없이 |

이미 있는 API의 응답이 바뀌는 곳

- 조 정보(`GET /teams/{teamId}`)에 과제 기간 `startDate`, `endDate` 추가. 진척 화면(R-4-1)이 씀
- 새로 만드는 조 운영·과정 기록 쓰기 요청도 평가 단계에서 400 `INVALID_INPUT`
- 내 정보(`GET /members/me`)에 `privacyConsentedAt` 추가(X-2)
- 400 응답의 `message`에 틀린 칸을 적음. 모양은 그대로임(X-7)
- 어떤 응답에도 합계, 평균, 순위, 진척률, 근거 패널의 건수를 넣지 않음
