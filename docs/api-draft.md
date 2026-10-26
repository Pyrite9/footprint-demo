# API 초안

`../footprint-study/docs/api.md`가 생기기 전까지 쓰는 시연본의 API 초안이다. Controller를 만들기 전에 여기에 먼저 적고, 확인을 기다리지 않고 바로 만든다(2026.10.25 관리자 결정). 정한 이유는 `docs/worklog.md`의 그 단위 항목 "결정" 줄에 남긴다. 제목의 "확인 2026.10.25"는 이 결정 전에 관리자 확인을 받은 API라는 표시다.

공통
- 오류 응답은 모두 `{ "code": "...", "message": "..." }`. `code`는 `NOT_FOUND` 404, `INVALID_INPUT` 400, `UNAUTHORIZED` 401, `FORBIDDEN` 403, `DUPLICATE` 409, `SERVER_ERROR` 500.
- `/auth/**` 말고는 모두 header에 `Authorization: Bearer <accessToken>`이 필요하다. 없거나 틀리면 401 `UNAUTHORIZED`.
- 없는 주소, 또는 주소는 있지만 그 method가 없는 요청(예: `DELETE /assignments/1`)은 404 `NOT_FOUND` "없는 주소입니다"(2026.10.25, 미정 6 중 404 부분). token이 없으면 그보다 먼저 401이다.
- 서버 내부 오류는 500 `SERVER_ERROR` "서버에서 오류가 났습니다"(2026.10.26 관리자 결정, 미정 6 중 500 부분). 원인(예외 내용)은 응답에 담지 않고 서버 로그에만 남긴다.
- 위 code에 들어가지 않는 요청 오류(예: 지원하지 않는 Content-Type)는 400 `INVALID_INPUT` "요청의 형식이 올바르지 않습니다".

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

---

## 조

### GET /teams/{teamId} — 조 정보 (T-1, 조 화면) · 2026.10.25

조 이름과 그 조가 속한 과제를 받는다. 그 조의 조원과 그 과제를 만든 교수만 부를 수 있다.

200
```json
{ "id": 1, "name": "1조", "assignmentId": 1, "assignmentTitle": "발자국 시연 과제" }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도 아니고 그 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### GET /teams/{teamId}/members — 조원 목록 (T-1, T-2) · 2026.10.25

조원 목록을 합류한 순서로 받는다. 조장이 맨 앞이다. 권한과 오류는 `GET /teams/{teamId}`와 같다.

200
```json
[
  { "id": 1, "memberId": 2, "name": "김민준", "roleName": "기획", "leader": true },
  { "id": 2, "memberId": 3, "name": "이서연", "roleName": null, "leader": false }
]
```

- `id`는 조 참여 기록 번호다. 역할 지정(T-2-1)의 주소에 쓴다. `memberId`는 회원 번호다.
- `roleName`은 역할을 배정하기 전이면 `null`이다.

### GET /teams/{teamId}/invite-code — 초대 코드 보기 (T-1-1) · 2026.10.25

조장이 지금 유효한 초대 코드를 본다. 발급한 적이 없으면 `inviteCode`가 `null`이다.

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

조장이 초대 코드를 새로 발급한다. 다시 발급하면 이전 코드는 더 이상 쓸 수 없다. 요청 body는 없다.
응답과 오류는 `GET /teams/{teamId}/invite-code`와 같다.

화면의 초대 링크는 `<화면 주소>/join/<inviteCode>`이다(예: `http://localhost:5173/join/3f2b7c1e-...`).

### POST /teams/join — 초대 코드로 합류 (T-1-2) · 2026.10.25

학생이 초대 코드로 그 조에 조원으로 합류한다. 역할은 비어 있다(조장이 T-2-1에서 지정).

요청
```json
{ "inviteCode": "3f2b7c1e-8d4a-4c55-9b0e-2a6f1d9e7c10" }
```

200: 합류한 조. 모양은 `GET /teams/{teamId}`와 같다.
```json
{ "id": 3, "name": "1조", "assignmentId": 2, "assignmentTitle": "새 과제" }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `inviteCode`가 비었을 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 학생이 아닐 때 |
| 404 | `NOT_FOUND` | 없는 초대 코드일 때(다시 발급되어 무효가 된 코드 포함) |
| 409 | `DUPLICATE` | 같은 과제의 다른 조에 이미 있을 때 |

서버 쪽 규칙
- 이미 그 조의 조원이면 오류 없이 200으로 그 조를 돌려준다(demo-scope 9절 미정 5, 2026.10.25 추천안으로 결정). 링크를 두 번 눌러도 결과가 같다.
- 한 학생은 같은 과제에서 한 조에만 있다. DB 제약이 아니라 Service가 확인한다.
- 같은 학생이 같은 순간에 합류 요청을 두 번 보내면 500이 날 수 있다. 화면은 합류 중 버튼을 막아서 시연에서는 생기지 않는다.

### POST /teams/{teamId}/members/{teamMemberId}/role — 역할 지정 (T-2-1) · 2026.10.25

조장이 조원 한 명(조장 자신 포함)에게 역할을 지정한다. 역할이 없는 조원에게만 지정할 수 있다. 역할 변경(T-2-2)은 2단계다.
`teamMemberId`는 조원 목록의 `id`(조 참여 번호)다. 지정 시각은 서버가 기록한다.

요청
```json
{ "roleName": "백엔드" }
```

200: 역할이 지정된 조원. 모양은 조원 목록의 한 줄과 같다.
```json
{ "id": 12, "memberId": 8, "name": "조은우", "roleName": "백엔드", "leader": false }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `roleName`이 비었거나 255자를 넘을 때, 주소의 번호가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조장이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조이거나, 그 조의 조원이 아닌 `teamMemberId`일 때 |
| 409 | `DUPLICATE` | 이미 역할이 있는 조원일 때 |

서버 쪽 규칙
- 역할명 앞뒤 공백은 지우고 저장한다. 같은 역할을 여러 조원이 맡을 수 있다.
- 메서드는 POST다. 한 번 지정하면 바꾸지 않으므로 "새로 만든다"에 가깝고, CORS 허용 목록(GET, POST, PUT, DELETE)을 바꾸지 않아도 된다.

---

## 산출물

### POST /teams/{teamId}/deliverables — 산출물 업로드 (R-1-1) · 2026.10.25

역할이 배정된 조원이 파일 하나를 올린다. 요청은 `multipart/form-data`이고 파일 칸 이름은 `file`이다. 고친 파일도 덮어쓰지 않고 새로 올린다.
올린 사람의 그때 역할명이 산출물에 복사되어 저장된다. 올린 시각은 서버가 기록한다.

200
```json
{ "id": 3, "roleName": "백엔드", "fileName": "API 명세.pdf", "fileSize": 52344, "uploaderName": "이서연", "uploadedAt": "2026-11-05T14:03:22" }
```

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `file`이 없거나 빈 파일일 때, 20MB를 넘을 때, 주소의 번호가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함), 역할이 배정되지 않은 조원일 때 |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### GET /teams/{teamId}/deliverables — 산출물 목록 (R-1-2) · 2026.10.25

조의 산출물을 최신순(올린 순서의 역순)으로 받는다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있다. 오류는 `GET /teams/{teamId}`와 같다.

200
```json
[
  { "id": 3, "roleName": "백엔드", "fileName": "API 명세 v2.pdf", "fileSize": 60211, "uploaderName": "조은우", "uploadedAt": "2026-11-06T10:00:00" },
  { "id": 2, "roleName": "백엔드", "fileName": "API 명세.pdf", "fileSize": 52344, "uploaderName": "이서연", "uploadedAt": "2026-11-05T14:03:22" }
]
```

- 순번(같은 역할 안에서 1, 2, 3…)은 응답에 넣지 않는다. 화면이 이 목록에서 같은 `roleName`끼리 올린 순서로 센다(demo-scope 4절 "화면은 … 순번을 붙이고").
- 조회 수, 다운로드 수는 없다.

### GET /deliverables/{deliverableId}/download — 산출물 다운로드 (R-1-2) · 2026.10.25

파일 본문을 받는다. 그 조의 조원과 그 과제를 만든 교수가 받을 수 있다.

200: 파일 본문. header
- `Content-Type`: 파일 이름의 확장자로 정한다. 모르면 `application/octet-stream`.
- `Content-Disposition: attachment; filename*=UTF-8''<퍼센트 인코딩한 파일 이름>`(RFC 5987). 한글 이름이 깨지지 않는다.

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 주소의 번호가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원도 아니고 그 과제를 만든 교수도 아닐 때 |
| 404 | `NOT_FOUND` | 없는 산출물이거나, 파일 본문이 저장소에 없을 때 |

서버 쪽 규칙
- 파일 본문은 MinIO에, 데이터베이스에는 저장 위치(key), 파일 이름, 크기만 둔다(+ 올린 조원, 역할명, 시각).
- 업로드는 "MinIO 저장 → 데이터베이스 저장" 순서다. 데이터베이스 저장이 실패하면 MinIO에 주인 없는 파일이 남는다(시연 범위에서는 지우지 않는다).
- 크기 제한은 `spring.servlet.multipart.max-file-size`, `max-request-size` 20MB.

---

## 동료평가

평가 항목 6개(신태섭 2018, demo-scope 7절)는 요청·저장에서 아래 이름을 쓴다. 점수는 1~5 정수다.

| 이름 | 항목 |
|---|---|
| `goalSharing` | 목표 공유·협업 |
| `roleFulfillment` | 역할 수행 |
| `encouragement` | 격려·도움 |
| `onlineCommunication` | 온라인 소통 |
| `offlineCommunication` | 대면 소통 |
| `progressCheck` | 진행 점검·보충 |

시스템은 점수를 합치거나 평균 내지 않는다. 어떤 응답에도 합계·평균·순위가 없다.

### GET /teams/{teamId}/evaluations/mine — 내 평가 제출 여부 (E-2) · 2026.10.25

내가 이 조에서 동료평가를 제출했는지 받는다. 점수는 돌려주지 않는다. 그 조의 조원만 부를 수 있다.

200
```json
{ "submitted": true, "submittedAt": "2026-11-13T21:10:05" }
```

제출 전이면 `{ "submitted": false, "submittedAt": null }`.

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `teamId`가 숫자가 아닐 때 |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조일 때 |

### POST /teams/{teamId}/evaluations — 동료평가 제출 (E-2-1, E-2-3) · 2026.10.25

같은 조 조원 중 자신을 뺀 **전원**의 평가를 한 번에 제출한다. 제출한 평가는 고칠 수 없다. 제출 시각은 서버가 기록한다.
`targetTeamMemberId`는 조원 목록(`GET /teams/{teamId}/members`)의 `id`다. `comment`(서술 의견)는 선택이고 2000자까지다.

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

200: 제출 여부. 모양은 `GET /teams/{teamId}/evaluations/mine`과 같다.

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | `evaluations`가 비었거나, 점수가 빠졌거나 1~5가 아니거나, `comment`가 2000자를 넘을 때 |
| 400 | `INVALID_INPUT` | 대상이 "자신을 뺀 조원 전원 한 번씩"이 아닐 때(빠짐, 중복, 자기 자신, 다른 조 조원) |
| 401 | `UNAUTHORIZED` | token이 없거나, 서명이 틀렸거나, 만료됐을 때 |
| 403 | `FORBIDDEN` | 그 조의 조원이 아닐 때(교수 포함) |
| 404 | `NOT_FOUND` | 없는 조일 때 |
| 409 | `DUPLICATE` | 이미 제출했을 때(두 요청을 거의 동시에 보내 unique 제약에 걸린 두 번째 요청 포함) |

서버 쪽 규칙
- 평가는 회원이 아니라 조 참여(`TeamMember`)를 평가자·대상으로 참조한다. (평가자, 대상)에 데이터베이스 unique 제약이 있다.
- 평가와 의견은 과제를 만든 교수만 열람한다. 교수 열람 화면·API는 2단계라 1단계는 저장까지다. 조원끼리는 서로의 평가를 볼 수 없다(조회 API 없음).
