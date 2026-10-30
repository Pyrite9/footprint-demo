# 구조 그림

코드를 처음 읽는 사람이 먼저 볼 그림. GitHub가 Mermaid를 바로 그려 주므로 따로 그림 파일을 두지 않음.

- 그림의 글자는 실제 파일·클래스 이름과 같게 맞춤. 그림에서 본 이름으로 파일을 찾을 수 있어야 함
- 코드를 고치면 이 문서도 같은 작업 안에서 고칠 것
- API 주소와 응답 모양은 `api-draft.md`, 코드 규칙은 `../CLAUDE.md`

## 1. 전체 구조

사람이 브라우저를 열었을 때 어떤 프로그램이 몇 번 포트에서 무엇을 맡는지.

```mermaid
flowchart TB
    user["사용자 브라우저"]

    subgraph front["frontend · 화면"]
        react["React 19 + Vite<br/>5173 (개발은 Vite · 데모는 nginx)"]
    end

    subgraph back["backend · 서버"]
        boot["Spring Boot 3.5<br/>8080"]
    end

    subgraph infra["infra · docker compose"]
        db[("PostgreSQL 17<br/>5432")]
        minio[("MinIO<br/>9000 · 콘솔 9001")]
    end

    google["Google Identity Services<br/>구글 공개키"]
    mock["json-server<br/>3001 · 가짜 API"]

    user --> react
    react -- "fetch · src/api/client.js" --> boot
    react -. "백엔드 없이 개발할 때" .-> mock
    react -- "구글 버튼 · ID token 받기" --> google
    boot -- "JPA" --> db
    boot -- "S3 API · 파일 본문" --> minio
    boot -- "구글 공개키 받기" --> google
```

- 화면은 **PostgreSQL이나 MinIO를 직접 부르지 않음.** 모든 데이터는 8080을 거침
- 파일 본문만 MinIO에 있고, 파일 위치·이름·크기는 PostgreSQL에 있음
- 데모(맨 위 `docker-compose.yml`)에서는 nginx가 컨테이너 안 80번에서 듣고, 밖으로는 5173으로 열림. 사람은 두 경우 모두 5173으로 접속함
- docker compose로 켠 포트는 `127.0.0.1`에만 열어 둠. 같은 네트워크의 다른 컴퓨터에서는 접속되지 않음. 단, 개발 중 `gradlew bootRun`으로 직접 켠 백엔드 8080은 모든 주소에 열림(`application.yml`에 `server.address`가 없음)
- 가짜 API(3001)는 Java와 Docker 없이 화면만 만들 때 쓰는 갈래. 실제 API와 응답 모양이 같음

## 2. JWT 흐름

로그인한 사람이 누구인지 요청마다 어떻게 알아내는지. 서버는 session을 만들지 않음. 그림 두 장으로 나눔.

### 2-1. 로그인하고 token 받기

```mermaid
sequenceDiagram
    actor U as 사용자
    participant B as GoogleLoginButton.jsx
    participant G as Google
    participant P as LoginPage.jsx
    participant AC as AuthController
    participant A as AuthService
    participant V as GoogleTokenVerifier
    participant J as JwtProvider

    U->>B: 구글 버튼 누름
    B->>G: 구글 로그인 창 (google.js가 붙인 script)
    G-->>B: ID token
    B->>P: onCredential(credential)
    P->>AC: POST /auth/google · credential<br/>api/auth.js → client.js
    Note over AC: /auth/**는 permitAll · token 없이 통과
    AC->>A: loginWithGoogle
    A->>V: verify
    V->>G: 구글 공개키 받기 (받은 뒤에는 보관해 두고 씀)
    V->>V: 서명 · 만료 · aud · email_verified 확인
    V-->>A: GoogleAccount (이메일 · 이름 · hd)
    A->>A: hd가 학교 도메인인가 · 처음이면 회원 저장
    alt 성공
        A->>J: createToken
        J-->>A: token (sub에 회원 id)
        A-->>AC: token 문자열
        AC-->>P: 200 · LoginResponse { accessToken }
        P->>P: saveToken · sessionStorage
    else 구글 검증 실패 401 · 학교 계정 아님 403
        AC-->>P: 401 또는 403 · { code, message }
        P->>P: error state에 message 표시 · /login 이동 없음
    end
```

- 서버는 ID token을 구글에 보내지 않음. 구글 공개키만 받아 와서 서명은 서버 안에서 확인함. 이메일·이름·`hd`는 ID token 안에 들어 있음
- 로그인 실패 때 `/login`으로 다시 보내지 않는 이유: `LoginPage`가 요청 전에 `removeToken()`을 부름. `client.js`는 "token을 보냈는데 401"일 때만 token을 지우고 이동함
- 이메일·비밀번호 로그인(`POST /auth/login`)은 `AuthController.login` → `AuthService.login`. 구글 검증·도메인 검사·회원 저장 없이 BCrypt 비교만 함. 시작 계정 전용이고, 구글로 만든 회원(비밀번호 없음)은 이 방법으로 로그인할 수 없음

### 2-2. 그 다음 요청마다

```mermaid
sequenceDiagram
    actor U as 사용자
    participant T as TeamPage.jsx
    participant CL as api/client.js
    participant F as JwtAuthenticationFilter
    participant J as JwtProvider
    participant S as authorizeHttpRequests (SecurityConfig 설정)
    participant C as TeamController

    U->>T: 조 화면 열기
    T->>CL: getTeamMembers (api/teams.js)
    CL->>F: GET /teams/1/members<br/>Authorization: Bearer ...
    F->>J: getMemberId
    alt 서명과 만료가 맞음
        J-->>F: 회원 id
        F->>F: SecurityContextHolder에 회원 id를 적음
        F->>S: 다음 단계로 넘김
        S->>C: 로그인한 요청 · 통과
        C->>C: @AuthenticationPrincipal Long memberId
        C-->>CL: 200 · 응답 DTO
        CL-->>T: 조원 목록
    else 틀렸거나 만료됨
        J--xF: JwtException
        F->>S: 아무것도 적지 않고 넘김
        S-->>CL: 401 · authenticationEntryPoint가 쓴 { code, message }
        CL->>CL: token을 지우고 /login으로
    end
```

- 막는 일은 filter가 아님. `SecurityConfig`가 서버를 켤 때 설정한 `authorizeHttpRequests` 단계가 함. filter는 "누가 보냈는지"만 적어 둠
- token이 아예 없으면 요청이 서버까지 가지 않음. `RequireLogin.jsx`가 화면 단계에서 `/login`으로 보냄
- token에 `memberType`도 들어 있지만 **참고용.** 권한 검사는 Service가 데이터베이스의 값으로 함
- token은 `sessionStorage`에 둠. 탭마다 따로라서 한 탭은 교수, 다른 탭은 학생으로 동시에 로그인할 수 있음

## 3. 프론트엔드 구조

화면 하나를 그릴 때 어떤 파일을 거치는지. 조 화면(`/teams/:teamId`)을 예로 둠.

```mermaid
flowchart TB
    main["main.jsx"] --> app["App.jsx<br/>주소와 화면 짝 짓기"]
    app --> require["components/RequireLogin.jsx<br/>token 없으면 /login으로"]
    require --> layout["pages/AppLayout.jsx<br/>상단 바 · Outlet"]
    layout --> page["pages/TeamPage.jsx<br/>데이터 불러오기 · loading · error state"]

    page --> comp1["components/TeamMemberList.jsx"]
    comp1 --> comp2["components/RoleAssignForm.jsx"]
    page --> comp3["components/InviteLinkPanel.jsx"]

    page -- "함수 호출" --> api["api/teams.js<br/>주소 한 줄씩"]
    page -- "getMe" --> auth["api/auth.js"]
    api --> client["api/client.js<br/>주소 붙이기 · token 붙이기 · 오류 던지기"]
    auth --> client
    client --> token["api/token.js<br/>sessionStorage"]
    require -- "getToken" --> token
    client -- "fetch" --> server["backend 8080"]
```

- **page만 데이터를 불러오고 state를 가짐.** component는 props로 받아 그리기만 함. 예외 하나: `GoogleLoginButton.jsx`는 구글 script를 불러옴(서버 데이터는 아님)
- `fetch`는 `src/api/` 밖에 쓰지 않음. 모든 호출이 `client.js` 한 곳을 거침
- `client.js`는 실패하면 `status`와 `code`를 담은 오류를 던짐. page의 `.catch`가 그것을 받아 `error` state에 넣음
- 비동기는 `.then` 방식으로 통일. `async`/`await`를 섞지 않음
- 화면을 하나 더할 때 손대는 곳: `App.jsx`에 `Route` 한 줄, `pages/`에 page 하나, 필요하면 `api/`에 함수 하나
- `src/constants/`는 화면에 그대로 쓰는 고정 문장. 동료평가 6개 항목과 점수 기준(`peerEvaluation.js`)이 여기 있고 **고치지 않음**

## 4. 백엔드 구조

요청 하나가 Controller부터 데이터베이스까지 내려가는 길. 산출물 업로드를 예로 둠.

```mermaid
flowchart TB
    req["POST /teams/1/deliverables<br/>multipart file"] --> filter["security/JwtAuthenticationFilter<br/>회원 id 꺼내기"]
    filter --> ctrl["controller/DeliverableController<br/>요청 받기 · DeliverableResponse.from"]
    ctrl -.-> dto["dto/DeliverableResponse<br/>record"]
    ctrl --> svc["service/DeliverableService<br/>@Transactional · 업무 규칙"]

    svc --> teamRepo["repository/TeamRepository<br/>findById · 없으면 404"]
    svc --> checker["service/TeamAccessChecker<br/>checkTeamMember · 403"]
    svc --> repo["repository/DeliverableRepository<br/>JpaRepository"]
    svc --> storage["service/StorageService<br/>S3 API"]

    checker --> tmRepo["repository/TeamMemberRepository"]
    repo --> entity["entity/Deliverable<br/>@Entity"]
    entity --> db[("PostgreSQL")]
    teamRepo --> db
    tmRepo --> db
    storage --> minio[("MinIO")]

    svc -. "던진 예외를 Spring이 넘김<br/>InvalidInputException 400<br/>NotFoundException 404<br/>ForbiddenException 403" .-> handler["exception/GlobalExceptionHandler<br/>ApiErrorResponse code · message"]
    checker -. "ForbiddenException 403" .-> handler
```

- 한 방향으로만 내려감. **Service가 Controller를 부르거나 Repository가 Service를 부르는 일은 없음**
- Service는 Entity를 돌려주고, Entity → DTO 변환은 Controller에서 `XxxResponse.from(entity)`로 함(여기서는 `DeliverableResponse.from`)
- 예외는 Controller가 handler를 부르는 게 아님. Service에서 던진 예외가 Controller를 지나 위로 올라가면 Spring이 `GlobalExceptionHandler`로 넘김
- 400이 Service보다 먼저 나는 경우: 파일 칸이 빠졌거나 20MB를 넘으면 Spring이 Controller 전에 막고, 그 예외도 `GlobalExceptionHandler`가 400 `INVALID_INPUT`으로 바꿈. Service의 `InvalidInputException`은 빈 파일일 때만 남
- 권한 검사는 `TeamAccessChecker`의 메서드 호출로 **코드에 보이게** 함. 어노테이션이나 AOP로 숨기지 않음
- 오류 응답은 `ApiErrorResponse { code, message }` 하나뿐이고 만드는 곳은 `GlobalExceptionHandler` 한 곳. 다만 Security 단계에서 막힌 401은 Controller에 닿지 않아 `SecurityConfig`가 같은 모양으로 직접 씀
- 업로드는 "MinIO 저장 → 데이터베이스 저장" 순서. 뒤가 실패하면 파일만 남음(`../TODO.md` "알려진 한계")

### 데이터 구조

엔티티 6개. 산출물과 평가는 회원이 아니라 **조 참여**(`TeamMember`)를 가리킴.

```mermaid
erDiagram
    MEMBER ||--o{ ASSIGNMENT : "만듦 · professor_id"
    MEMBER ||--o{ TEAM_MEMBER : "참여"
    ASSIGNMENT ||--o{ TEAM : "assignment_id"
    ASSIGNMENT ||--o{ TEAM_MEMBER : "assignment_id"
    TEAM ||--o{ TEAM_MEMBER : "team_id"
    TEAM_MEMBER ||--o{ DELIVERABLE : "올림"
    TEAM_MEMBER ||--o{ EVALUATION : "평가자 evaluator_id"
    TEAM_MEMBER ||--o{ EVALUATION : "대상 target_id"

    MEMBER {
        Long id PK "회원 번호 · 자동 증가"
        String email UK "로그인 아이디 · 같은 이메일 두 번 저장 불가"
        String password "BCrypt로 바꾼 값 · 구글 회원은 null"
        String name "화면에 보이는 이름"
        MemberType memberType "PROFESSOR 교수 · STUDENT 학생 · 권한 검사 기준"
    }
    ASSIGNMENT {
        Long id PK "과제 번호"
        String title "과제 제목"
        LocalDate startDate "과제 시작일"
        LocalDate endDate "과제 마감일"
        Long professor_id FK "과제를 만든 교수 · 교수 열람 권한 기준"
    }
    TEAM {
        Long id PK "조 번호"
        Long assignment_id FK "이 조가 속한 과제"
        String name "화면에 보이는 조 이름 · 예: 1조"
        String inviteCode UK "초대 링크에 들어가는 코드 · 발급 전 null · 재발급하면 이전 코드 무효"
    }
    TEAM_MEMBER {
        Long id PK "조 참여 번호 · 산출물과 평가가 가리키는 번호"
        Long team_id FK "참여한 조"
        Long assignment_id FK "team의 과제를 복사 · 한 과제 한 조 unique 제약용"
        Long member_id FK "참여한 회원"
        boolean leader "조장이면 true · 조장도 조원으로 함께 등록"
        String roleName "조장이 정한 역할명 · 배정 전에는 null"
        LocalDateTime roleAssignedAt "역할 지정 시각 · 서버가 기록 · 배정 전에는 null"
    }
    DELIVERABLE {
        Long id PK "산출물 번호"
        Long team_member_id FK "올린 조원의 조 참여"
        String roleName "업로드 당시 역할명을 복사 · 역할이 바뀌어도 순번 유지"
        String fileName "올린 파일의 원래 이름 · 다운로드할 때 이 이름으로 내려줌"
        long fileSize "파일 크기(byte)"
        String storageKey "MinIO 안의 위치 · 파일 본문을 꺼낼 때 씀"
        LocalDateTime uploadedAt "올린 시각 · 서버가 기록"
    }
    EVALUATION {
        Long id PK "평가 번호"
        Long evaluator_id FK "평가한 조원의 조 참여"
        Long target_id FK "평가받은 조원의 조 참여"
        int goalSharing "목표 공유·협업 · 1~5점"
        int roleFulfillment "역할 수행 · 1~5점"
        int encouragement "격려·도움 · 1~5점"
        int onlineCommunication "온라인 소통 · 1~5점"
        int offlineCommunication "대면 소통 · 1~5점"
        int progressCheck "진행 점검·보충 · 1~5점"
        String comment "조원별 서술 의견 · 선택 · 최대 2000자"
        LocalDateTime submittedAt "제출 시각 · 서버가 기록 · 한 번에 낸 평가는 같은 시각"
    }
```

- 각 열의 오른쪽 칸은 비고. 그 값이 무엇에 쓰이는지 적음. `EVALUATION` 점수 열의 항목 이름은 `frontend/src/constants/peerEvaluation.js`의 `name`과 같음
- 열 이름 표기: FK는 데이터베이스 열 이름(`professor_id`), 나머지는 Java 필드 이름(`startDate`)으로 적음. 실제 데이터베이스 열은 snake_case임(`startDate` → `start_date`)
- `TEAM_MEMBER`에 `(assignment_id, member_id)` unique, `EVALUATION`에 `(evaluator_id, target_id)` unique
- `TEAM_MEMBER.assignment_id`는 `TEAM.assignment_id`와 같은 값. unique 제약은 한 테이블의 열만 쓸 수 있어서, "한 학생은 한 과제에서 한 조"를 DB가 지키도록 복사해 둠. 생성자가 team에서 채우므로 둘이 달라질 수 없음
- 합류: 같은 조에 다시 합류하면 오류 없이 200. 같은 과제의 다른 조에 이미 있으면 Service 검사로 409 `DUPLICATE`. `TEAM_MEMBER`의 unique는 두 요청이 거의 동시에 올 때를 막는 마지막 장치
- 평가: 이미 제출했으면 Service 검사로 409 `DUPLICATE`. `EVALUATION`의 unique는 두 요청이 거의 동시에 올 때를 막는 마지막 장치
- 조장을 따로 두지 않고 조 참여의 `leader`로 표시함. 선택 이유와 열린 부분은 `../TODO.md` "아직 정하지 않은 것"
- `DELIVERABLE.roleName`은 `TEAM_MEMBER.roleName`을 가리키지 않고 **값을 복사해 둔 것.** 나중에 역할이 바뀌어도 산출물 순번이 끊기지 않게 함
- 점수 열은 6개 그대로 저장함. 합계·평균 열은 두지 않음
- 기록 시각(`uploadedAt`, `roleAssignedAt`, `submittedAt`)은 모두 서버가 넣음. 클라이언트가 보낸 시각은 받지 않음
