# API 초안

`../footprint-study/docs/api.md`가 생기기 전까지 쓰는 시연본의 API 초안이다. Controller를 만들기 전에 여기에 먼저 적고 관리자 확인을 받는다.

공통
- 오류 응답은 모두 `{ "code": "...", "message": "..." }`. `code`는 `NOT_FOUND` 404, `INVALID_INPUT` 400, `UNAUTHORIZED` 401, `FORBIDDEN` 403, `DUPLICATE` 409.
- `/auth/**` 말고는 모두 header에 `Authorization: Bearer <accessToken>`이 필요하다. 없거나 틀리면 401 `UNAUTHORIZED`.

---

## 인증

### POST /auth/login — 로그인 (M-2-1) · 확인 2026.10.25

이메일과 비밀번호로 로그인하고 token을 받는다. token 없이 부를 수 있다.

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
| 401 | `UNAUTHORIZED` | 이메일이 없거나 비밀번호가 틀렸을 때. 둘을 구분하지 않고 같은 메시지를 준다 |

token
- 서명 HS256. 유효 시간 24시간(`jwt.expiration-minutes`). refresh token은 없다.
- payload: `sub` = 회원 id(문자열), `memberType` = `PROFESSOR` 또는 `STUDENT`.

---

## 회원

### GET /members/me — 내 정보 (M-2-1) · 확인 2026.10.25

로그인한 회원 자신의 정보를 받는다. token이 필요하다.

200
```json
{ "id": 2, "email": "student01@example.com", "name": "김민준", "memberType": "STUDENT" }
```

| 상태 | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때. 셋을 구분하지 않고 같은 메시지를 준다 |
| 404 | `NOT_FOUND` | token의 회원 id가 DB에 없을 때(시작 데이터 순서를 바꾼 뒤 예전 token을 쓴 경우) |

서버 쪽 규칙
- Controller는 로그인한 회원을 `@AuthenticationPrincipal Long memberId`로 받는다(demo-scope 9절 미정 3, 2026.10.25 결정). 회원 유형이 필요하면 Service가 DB에서 읽는다.

---

## 과제

### POST /assignments — 과제 생성 (A-1-1, A-1-2) · 확인 2026.10.25

교수가 과제, 조, 조장을 한 번에 만든다. 등록한 과제는 고치거나 지울 수 없다(수정·삭제 API 없음).

요청
```json
{
  "title": "발자국 시연 과제",
  "startDate": "2026-11-02",
  "endDate": "2026-12-11",
  "leaderEmails": ["student01@example.com", "student06@example.com"]
}
```

200
```json
{ "id": 2, "title": "발자국 시연 과제", "startDate": "2026-11-02", "endDate": "2026-12-11" }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `title`이 비었거나 255자를 넘거나, 날짜가 없거나 형식이 틀렸거나, `leaderEmails`가 없거나 빈 배열일 때 |
| 400 | `INVALID_INPUT` | 종료일이 시작일보다 앞일 때 |
| 400 | `INVALID_INPUT` | 같은 이메일이 두 번 있을 때(한 학생은 같은 과제의 두 조에 조장이 될 수 없다) |
| 400 | `INVALID_INPUT` | 가입하지 않은 이메일이거나 학생이 아닌 이메일일 때. 메시지에 그 이메일을 넣는다 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때. 셋을 구분하지 않고 같은 메시지를 준다 |
| 403 | `FORBIDDEN` | 교수가 아닌 회원이 부를 때 |

서버 쪽 규칙
- 조 개수는 `leaderEmails`의 길이다. 조 이름은 순서대로 "1조", "2조"…로 붙인다.
- 조장은 그 조의 첫 번째 조원으로 함께 등록된다(`TeamMember.leader = true`). 역할명은 비어 있고 역할 지정(T-2-1)에서 넣는다.
- 검사를 모두 통과한 뒤에 저장한다. 하나의 트랜잭션이라 저장 중에 실패해도 일부만 남지 않는다.
- 응답에 조 목록은 넣지 않는다. 조 목록은 과제 상세(A-2-2)에서 받는다.

### GET /assignments — 과제 목록 (A-2-1) · 2026.10.25

내가 볼 수 있는 과제 목록을 받는다. 교수는 자기가 만든 과제를 만든 순서의 최신순으로, 학생은 자기가 조원으로 참여한 과제를 참여한 순서의 최신순으로 받는다.

200
```json
[
  { "id": 1, "title": "발자국 시연 과제", "startDate": "2026-11-02", "endDate": "2026-12-11" }
]
```

볼 과제가 없으면 빈 배열 `[]`이다.

| 상태 | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |

### GET /assignments/{assignmentId} — 과제 상세 (A-2-2) · 2026.10.25

과제 정보와 조 목록을 받는다. 과제를 만든 교수는 조 전부, 학생은 자기 조만 받는다.

200
```json
{
  "id": 1, "title": "발자국 시연 과제", "startDate": "2026-11-02", "endDate": "2026-12-11",
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
- 접근 검사는 `TeamAccessChecker` 한 곳에서 한다(demo-scope 4절 "권한 검사는 Service 안에서 보이는 메서드 호출로").
- 조 목록은 조 id 순서다.
