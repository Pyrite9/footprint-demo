# 작업 기록

Claude Code가 단위 업무 하나를 끝낼 때마다 오늘 날짜 아래에 적는다. /read-check 결과도 여기에 적는다.

<!--
## 2026.10.26

### <단위 업무 ID> <이름>
- 만든 파일:
- 고친 파일:
- 읽을 때 볼 곳:
- 내가 채운 가정:

### 읽기 점검
- 점검한 파일:
- 설명 못 한 줄:
- 처리:
-->

## 2026.10.25

### 0-1 회원·시작 계정·SecurityFilterChain 뼈대 (M-2-1 준비)
- 관리자 결정: 로그인은 당분간 시작 계정의 이메일·비밀번호로 한다. 구글 로그인으로 나중에 바꿀 예정이다(demo-scope 9절 미정 1을 잠정 결정).
- 만든 파일: `backend/` 전체(`../footprint-study/example/backend` 복사, Assignment 예시 7개 삭제), `entity/Member`, `entity/MemberType`, `repository/MemberRepository`, `config/SecurityConfig`, `exception/ForbiddenException`, `exception/DuplicateException`, `backend/README.md`
- 고친 파일: `settings.gradle`(footprint-backend), `build.gradle`(spring-boot-starter-security 추가), `config/DataInitializer`(교수 1명·학생 10명), `exception/GlobalExceptionHandler`(403·409 추가, 400 메시지 한국어로), `infra/docker-compose.yml`(`name: footprint-demo`)
- 확인: `gradlew build` 성공. 실행 후 `GET /anything` → 401 `UNAUTHORIZED` JSON, `OPTIONS` preflight(Origin 5173) → 200, member 테이블 11행에 BCrypt 값 저장.
- 읽을 때 볼 곳:
  - `SecurityConfig`의 401은 `GlobalExceptionHandler`가 아니라 `authenticationEntryPoint`가 쓴다. Security filter는 Controller보다 앞에서 막기 때문이다.
  - `.cors(Customizer.withDefaults())`가 `WebConfig`의 CORS 설정을 Security에 연결한다. 빠지면 preflight가 401이 된다.
  - `/error`를 열어 둔 이유: 서버 오류 때 Spring이 `/error`로 다시 보내는데, 막혀 있으면 500이 401로 보인다.
  - `@Enumerated(EnumType.STRING)`: 없으면 enum이 0, 1로 저장된다.
  - 실행 로그의 `Using generated security password`는 Spring Security 기본 계정이고 쓰지 않는다.
- 내가 채운 가정:
  - 회원 엔티티 이름 `Member`(테이블 `member`), 회원 유형 필드 `memberType`, enum `MemberType { PROFESSOR, STUDENT }`. `role`은 조 역할과 헷갈려서 쓰지 않았다.
  - 로그인 아이디는 이메일. `password`는 NOT NULL. 구글 로그인으로 바꾸면 nullable로 바꾼다.
  - 시작 계정 이메일은 `@example.com`, 비밀번호는 전원 `pass1234`, 이름은 가상의 이름.
  - 403 응답은 Security의 accessDeniedHandler 대신 Service의 `ForbiddenException`으로만 낸다(주소별 권한 규칙을 두지 않으므로).
  - compose project 이름을 `footprint-demo`로 정했다. 학습 저장소 `infra`와 폴더 이름이 같아 컨테이너·volume이 섞였기 때문이다.

### 0-2 로그인 API (M-2-1 로그인, token 발급)
- 관리자 결정: JWT 라이브러리는 jjwt(demo-scope 9절 미정 7의 JWT 부분). 버전은 Maven Central 최신 안정판 0.13.0(2025.08).
- 만든 파일: `docs/api-draft.md`(`POST /auth/login`), `security/JwtProvider`, `dto/LoginRequest`, `dto/LoginResponse`, `service/AuthService`, `controller/AuthController`, `exception/UnauthorizedException`
- 고친 파일: `build.gradle`(jjwt-api, jjwt-impl, jjwt-jackson), `application.yml`(`jwt.secret`, `jwt.expiration-minutes`), `repository/MemberRepository`(`findByEmail`), `exception/GlobalExceptionHandler`(`handleUnauthorized`), `backend/README.md`(확인 방법)
- 확인: `gradlew build` 성공. 맞는 계정 → 200과 token(header `HS256`, payload `sub`·`memberType`·`iat`·`exp`, 24시간). 틀린 비밀번호·없는 이메일 → 둘 다 401로 같은 메시지. 빈 값·형식 오류 → 400.
- 읽을 때 볼 곳:
  - `build.gradle`의 jjwt 3줄: 코드는 `jjwt-api`만 import 하고, `impl`·`jackson`은 실행할 때만 쓰여서 `runtimeOnly`다.
  - `JwtProvider` 생성자의 `@Value`: yml 값을 생성자 인자로 받는다. 서명 키가 32바이트보다 짧으면 서버 시작 때 `WeakKeyException`.
  - `signWith(secretKey, Jwts.SIG.HS256)`: 알고리즘을 빼면 jjwt가 키 길이에 따라 HS384·HS512로 바꿀 수 있다.
  - 인터넷 자료의 `setSubject`, `parserBuilder`는 0.11 이전 방식이다. 0.12 이후는 `subject`, `parser().verifyWith`.
  - `passwordEncoder.matches(입력값, 저장값)`의 인자 순서.
  - 401이 두 군데서 나온다. token 없이 막힌 주소는 `SecurityConfig`, 로그인 실패는 `GlobalExceptionHandler`.
- 내가 채운 가정:
  - token payload: `sub` = 회원 id 문자열, `memberType` claim. 유효 24시간, refresh token 없음.
  - 서명 키 기본값 `footprint-demo-dev-secret-key-change-me-0000`(44바이트), 환경 변수 `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`.
  - 로그인 응답은 `accessToken` 하나. 이름·회원 유형은 0-3의 `GET /members/me`로 받는다.
  - `AuthService.login`은 Entity가 아니라 token 문자열을 돌려준다("Service는 Entity를 돌려준다"의 예외).
  - 없는 이메일과 틀린 비밀번호를 같은 401 메시지로 묶었다.
  - 가짜 API의 `/auth/login`은 frontend가 생기는 0-4에서 만든다.

### 규칙 검사(rule-check) 0-1, 0-2
- 결과: 금지 패턴 0건. 수정 필요 2건, 확인 필요 4건, 선택 사항 3건.
- 고친 것(수정 필요 2건): `exception/GlobalExceptionHandler`의 handler 6개와 `config/WebConfig.addCorsMappings`에 메서드 주석을 달았다.
- 남은 확인 필요:
  - `WebConfig`의 CORS 허용 method 목록을 T-2-1의 method와 맞춘다(PATCH를 쓰면 추가해야 한다).
  - `BackendApplicationTests`가 PostgreSQL에 붙어서, Docker가 꺼져 있으면 `gradlew build`가 실패한다.
  - `/auth/login`의 가짜 API는 0-4에서 만든다.
  - 404·500 응답 형식(미정 6)은 0-4 `client.js` 전에 정한다.

### 0-3 token 검증과 내 정보 API (M-2-1 서버 쪽 후반부)
- 관리자 결정: 로그인한 회원은 Controller에서 `@AuthenticationPrincipal Long memberId`로 받는다(demo-scope 9절 미정 3). 회원 유형이 필요하면 Service가 DB에서 읽는다.
- 만든 파일: `security/JwtAuthenticationFilter`, `dto/MemberResponse`, `service/MemberService`, `controller/MemberController`
- 고친 파일: `security/JwtProvider`(`getMemberId`), `config/SecurityConfig`(필터 연결, 생성자에 JwtProvider), `docs/api-draft.md`(`GET /members/me`), `backend/README.md`(확인 방법)
- 확인: `gradlew build` 성공. 학생·교수 token → 200과 각자 정보. token 없음, 아무 문자열, `Bearer ` 빠짐, payload 조작, 만료(`JWT_EXPIRATION_MINUTES=0`) → 모두 401. `/auth/login`은 token이 없거나 틀린 token이 붙어도 200. `/members/me` preflight → 200.
- 읽을 때 볼 곳:
  - `JwtAuthenticationFilter`는 막지 않고 "누구인지 적기"만 한다. 막는 건 `SecurityConfig`의 `authorizeHttpRequests`다.
  - 필터 끝의 `filterChain.doFilter`: 빠지면 요청이 응답 없이 멈춘다.
  - 필터에 `@Component`가 없는 이유: 붙이면 Security 밖의 일반 servlet filter로 한 번 더 등록된다. 등록이 두 군데가 되면 어디서 실행되는지 추적하기 어렵다. (`OncePerRequestFilter`가 같은 요청의 두 번째 실행은 건너뛰므로 "두 번 실행"은 아니다. 규칙 검사에서 고침)
  - `new UsernamePasswordAuthenticationToken(memberId, null, List.of())`: 인자 3개짜리 생성자여야 "로그인한 요청"이 된다. 첫 인자가 `@AuthenticationPrincipal`로 들어간다.
  - `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`: 기준 자리일 뿐, formLogin을 꺼서 그 필터는 실행되지 않는다.
  - 빈 `catch` 블록: 틀린 token은 일부러 무시한다. 그러면 막힌 주소에서 entry point가 401을 낸다.
- 내가 채운 가정:
  - 만료·위조·형식 오류를 구분하지 않고 같은 401 메시지로 낸다.
  - 필터에 회원 유형별 권한(ROLE_...)은 넣지 않는다. 권한 검사는 Service의 메서드 호출로만 한다.
  - token의 `memberType` claim은 서버가 쓰지 않지만 api-draft와 맞추려고 남겨 둔다.
  - 가짜 API의 `/auth/login`, `/members/me`는 0-4에서 만든다.

### 규칙 검사(rule-check) 0-3
- 결과: 금지 패턴 0건. 수정 필요 1건, 확인 필요 2건, 선택 사항 1건.
- 고친 것: `security/JwtAuthenticationFilter`의 `@Component`를 붙이지 않는 이유 주석이 틀려서 고쳤다("두 번 실행" → "Security 밖에 한 번 더 등록"). 같은 문장이 있던 이 파일의 0-3 항목도 고쳤다.
- 남은 확인 필요:
  - `@AuthenticationPrincipal Long memberId`는 `permitAll` 주소에서 `null`이 된다. token이 필요한 주소에서만 쓴다는 규칙을 아직 적어 두지 않았다.
  - `/auth/login`, `/members/me`의 가짜 API는 0-4에서 만든다.

### 0-4a 가짜 API(/auth/login, /members/me)와 frontend 시작점
- 관리자 결정:
  - token 저장 위치는 sessionStorage(demo-scope 9절 미정 4). 탭마다 다른 계정으로 로그인해 시연할 수 있어서다. 0-4b에서 적용한다.
  - 가짜 API middleware 파일은 `mock/middleware.cjs`(CommonJS). CLAUDE.md의 파일 이름도 고쳤다.
- 만든 파일: `frontend/` 전체(`../footprint-study/example/frontend` 복사, Assignment 예시 4개 삭제), `mock/middleware.cjs`, `frontend/README.md`
- 고친 파일: `package.json`(이름 `footprint-frontend`, mock 스크립트에 `--middlewares mock/middleware.cjs`), `index.html`(lang ko, 제목 "발자국"), `src/App.jsx`(임시 화면), `db.json`(members 11명), `CLAUDE.md`(가짜 API 파일 이름과 CommonJS 이유)
- 확인: `npm run build`, `npm run lint` 성공. 가짜 API: 로그인 성공 200·틀린 계정 401·빈 값 400, `/members/me` 학생·교수 200, token 없음·아무 문자열 401, preflight 204, 호출 뒤 `db.json` 변경 없음.
- 읽을 때 볼 곳:
  - `package.json`이 `"type": "module"`이라 `mock/` 안의 `.js`도 ESM이 된다. json-server 0.17.4는 middleware를 `require`로 읽고, Node 24는 ESM을 `{ default: 함수 }` 객체로 돌려줘서 `app.use()`가 거부한다. 그래서 `.cjs`다.
  - `req.app.db`: json-server가 읽어 둔 `db.json`. `get('members').find({ email }).value()`는 lodash 문법이다.
  - `req.body`가 바로 있는 이유: json-server가 custom middleware보다 먼저 bodyParser를 붙인다.
  - `next()`: 직접 응답하지 않은 요청은 json-server의 기본 처리(db.json 자동 주소)로 넘어간다.
- 내가 채운 가정:
  - 가짜 token은 `mock-token-<회원 id>`. 화면은 token을 풀어 읽지 않는다.
  - token의 회원 id가 없으면 가짜 API는 401이다(실제 백엔드는 404). 서명이 없어서 위조와 구분할 수 없기 때문이다. 시작 데이터를 바꾸지 않으면 생기지 않는다.
  - `db.json` members에 비밀번호 평문을 둔다. `/members/me` 응답에서는 뺀다. `GET /members` 같은 json-server 자동 주소는 token만 있으면 열려 있다.
  - `mock/routes.json`은 아직 필요 없어 만들지 않았다.
  - eslint 설정이 `*.{js,jsx}`만 검사해서 `mock/middleware.cjs`는 lint 대상이 아니다.
  - `npm install`은 package-lock 그대로 설치했다. `npm audit` 안내는 손대지 않았다.

### 0-4b 화면 로그인 (M-2-1 로그인·로그아웃, 화면 쪽)
- 관리자 결정: React Router는 7.18.4(2026.09.15). 8.x는 확실히 아는 API 범위 밖이고 자료가 적어서. token 저장은 sessionStorage(0-4a에서 결정).
- 만든 파일: `src/api/token.js`, `src/api/client.js`, `src/api/auth.js`, `src/constants/memberTypes.js`, `src/components/RequireLogin.jsx`, `src/pages/LoginPage.jsx`, `src/pages/HomePage.jsx`
- 고친 파일: `src/App.jsx`(Router, 주소 연결), `package.json`(`react-router` 7.18.4 고정), `frontend/README.md`(화면 확인)
- 확인: `npm run build`, `npm run lint` 성공. 내장 browser로 확인 —
  - 가짜 API(3001): token 없이 `/` → `/login`, 틀린 비밀번호 메시지, 로그인 → "김민준 (학생)님", 새로고침 유지, 없는 주소 → `/`, 새 탭은 로그인 화면, 교수 탭·학생 탭 동시 로그인, 망가진 token → `/login`과 token 삭제, 로그아웃 후 뒤로 가기 → `/login`.
  - 실제 백엔드(8080): 틀린 비밀번호 메시지, 로그인(JWT 저장), 서명 위조 token → `/login`, 서버 꺼짐 → "요청을 처리하지 못했습니다", 서버 재시작 뒤 기존 token 유지, 교수 탭 로그인.
- 확인 중 고친 것: 서버가 꺼져 있을 때 `fetch` 자체가 실패해서 화면에 영어 "Failed to fetch"가 나왔다. `client.js`에 `.catch`를 붙여 기본 메시지로 바꿨다.
- 읽을 때 볼 곳:
  - `client.js`의 순서: ① fetch 실패(서버 꺼짐) → ② 성공이면 JSON → ③ token을 보냈는데 401이면 token 삭제·`/login` → ④ 실패 body를 읽어 `status`, `code`를 담은 오류를 던진다.
  - `LoginPage`의 `removeToken()`: 이전 token이 남은 채로 로그인에 실패하면 ③에 걸려 실패 메시지가 사라진다. 그래서 로그인 전에 지운다.
  - `RequireLogin`의 `<Outlet />`: App.jsx에서 이 Route 안쪽에 적은 화면이 들어갈 자리다. 새 화면은 그 안에 Route 한 줄을 추가한다.
  - `Navigate`의 `replace`: 뒤로 가기로 막힌 주소에 돌아가지 않게 한다.
  - 개발 모드(StrictMode)에서 `/members/me` 요청이 2번 보이는 것은 정상이다.
  - import는 `'react-router'`에서 한다(v7부터 `react-router-dom`은 같은 것을 다시 내보내기만 한다).
- 내가 채운 가정:
  - sessionStorage 키 `accessToken`.
  - 서버가 `code`를 주지 않는 실패(서버 꺼짐, Spring 기본 404·500)는 `code: null`, 메시지 "요청을 처리하지 못했습니다". 새 `code` 값은 만들지 않았다. backend 쪽 미정 6은 남아 있다.
  - 401 이동은 `window.location.assign('/login')`(페이지를 새로 연다).
  - `HomePage`는 임시 첫 화면이다. A-2-1이 생기면 바뀐다. 내 정보는 page마다 `getMe()`로 부른다(App으로 올릴지는 A-1에서 정한다).
  - 이미 로그인한 상태로 `/login`에 와도 로그인 화면을 보여 준다.
  - `react-router`는 `--save-exact`로 `7.18.4`를 고정했다(다른 패키지는 `^` 범위). 조원 모두 같은 버전을 쓰게 하려는 것이다.
  - 화면 디자인(CSS)은 손대지 않았다. 예시의 `index.css` 그대로다.

### 규칙 검사(rule-check) 0-4a, 0-4b
- 결과: 금지 패턴 0건, 수정 필요 0건, 확인 필요 2건, 선택 사항 3건.
- 고친 것(선택 사항 1·2번):
  - `src/pages/HomePage.jsx`: 오류 화면에도 로그아웃 버튼을 둔다(서버가 꺼졌을 때 다른 계정으로 바꿀 수 있게).
  - `src/main.jsx`: 시작 파일 주석과 StrictMode가 일부 코드를 두 번 실행하는 이유를 적었다.
- 관리자 결정으로 그대로 둔 확인 필요:
  - `RequireLogin`이 props 없이 sessionStorage의 token을 직접 읽는다(주소를 감싸는 컴포넌트).
  - 없는 회원 id가 든 token에 가짜 API는 401, 실제 백엔드는 404.
- 남은 선택 사항: `client.js`는 성공 응답을 항상 JSON으로 읽는다. R-1-2 다운로드 때 파일용 함수를 따로 만든다.

### 0-5 StorageService와 MinIO 버킷
- 관리자 결정: S3 클라이언트는 AWS SDK for Java v2 `software.amazon.awssdk:s3:2.55.14`(2026.10.09, demo-scope 9절 미정 7의 S3 부분). 거의 매일 새 판이 나와서 2주 이상 지난 판을 골랐다.
- 만든 파일: `config/S3Config`, `service/StorageService`, `test/.../StorageServiceTests`
- 고친 파일: `build.gradle`(s3), `application.yml`(`storage.endpoint`, `access-key`, `secret-key`, `bucket`), `config/DataInitializer`(첫 줄에 `createBucketIfMissing()`), `backend/README.md`(MinIO 콘솔 확인, 빌드에 Docker 필요)
- 확인: `gradlew build` 성공. 테스트 3개 통과(`contextLoads`, `saveAndLoad` 한글 이름 파일 올리고 꺼내 내용 비교, `loadMissingKey` → `NotFoundException`). 서버 실행 후 MinIO에 `footprint` 버킷 생성, 두 번째 실행도 오류 없이 시작.
- 읽을 때 볼 곳:
  - `S3Config`의 `forcePathStyle(true)`: 없으면 SDK가 `footprint.localhost:9000`으로 찾아가 연결에 실패한다.
  - `region(Region.US_EAST_1)`: MinIO는 안 쓰지만 SDK가 요구한다.
  - `StorageService.save`의 key는 UUID다. 원래 파일 이름과 크기는 호출한 쪽(R-1-1 산출물)이 DB에 저장한다.
  - `load`가 돌려준 `InputStreamResource`는 Controller가 `ResponseEntity<Resource>`로 내보낸 뒤 Spring이 닫는다.
  - `StorageServiceTests` 생성자의 `@Autowired`: 테스트 클래스는 JUnit이 만들어서 생성자 주입에 이 어노테이션이 필요하다(필드 주입 금지 규칙과 다르다).
- 내가 채운 가정:
  - 버킷 이름 `footprint`, 테스트 버킷 `footprint-test`. 접속값 기본값은 `infra/.env.example`과 같은 MinIO root 계정이다(개발용).
  - 버킷 만들기는 `@PostConstruct`가 아니라 `DataInitializer.run()` 첫 줄에서 부른다.
  - key에 `artifacts/` 같은 접두어를 붙이지 않는다.
  - 테스트가 `footprint-test` 버킷에 빌드마다 작은 파일을 남긴다. 지우는 메서드를 만들면 "산출물 삭제"(8절)와 겹쳐 보여서 만들지 않았다.
  - AWS SDK 2.30 이후의 기본 checksum 설정은 이 MinIO에서 문제가 없어 추가하지 않았다.
  - 시작 데이터의 산출물 2건은 R-1-1에서 넣는다.

### 규칙 검사(rule-check) 0-5
- 결과: 금지 패턴 해당 없음(테스트 생성자의 `@Autowired` 1건은 필드 주입이 아님), 수정 필요 1건, 확인 필요 1건, 선택 사항 2건.
- 고친 것: `service/StorageService.save`가 `RequestBody.fromInputStream(file.getInputStream(), ...)`로 연 stream을 닫지 않았다. AWS SDK 소스의 javadoc에 "SDK가 stream을 닫지 않는다, 재시도할 때 128 KiB까지만 되감는다"고 적혀 있다. `RequestBody.fromBytes(file.getBytes())`로 바꿨다(업로드 상한 20MB라 메모리에 올려도 된다).
- 남은 확인 필요: R-1-1 업로드는 "MinIO 저장 → DB 저장" 순서로 한다. MinIO는 `@Transactional`로 되돌아가지 않으므로, DB가 실패해도 주인 없는 파일만 남게 하기 위해서다.
- 남은 선택 사항: `storage.endpoint`를 `http://localhost:${MINIO_API_PORT:9000}`로 맞추기, 테스트 생성자의 `@Autowired`(그대로 둔다).

### A-1a 과제·조·조 참여 엔티티와 시작 데이터
- 관리자 결정(demo-scope 9절 미정 2):
  - 조장은 조 참여의 행에 둔다(`TeamMember.leader`).
  - 과제 필드는 `title`, `startDate`, `endDate`, `professor`. `description`, `status`는 두지 않는다(demo-scope 입력이 과제명·기간뿐이고 과제 단계는 2단계).
  - 영문 이름 `Assignment`, `Team`, `TeamMember`(테이블 assignment, team, team_member).
  - A-1b에서 Service의 400용으로 `InvalidInputException`(code `INVALID_INPUT`)을 추가한다.
- 만든 파일: `entity/Assignment`, `entity/Team`, `entity/TeamMember`, `repository/AssignmentRepository`, `repository/TeamRepository`, `repository/TeamMemberRepository`
- 고친 파일: `config/DataInitializer`(과제 1개, 조 2개, 조원 10명과 역할명. `saveMember`가 Member를 돌려주게 바꿈), `backend/README.md`(시작 과제와 조 표)
- 확인: `gradlew build` 성공(테스트 3개 통과). 실행 후 assignment 1행, team_member 10행(조장 2명, 역할명과 역할 지정 시각 저장). `(team_id, member_id)` 중복 insert는 unique 제약 오류로 막힌다.
- 읽을 때 볼 곳:
  - `@ManyToOne(fetch = FetchType.LAZY)`: 기본값 EAGER면 목록 조회마다 연관 쿼리가 줄줄이 붙는다.
  - `@JoinColumn(name = "professor_id")`: 테이블의 FK 열 이름이다. 필드 이름(professor)과 다르다.
  - `@Table(uniqueConstraints = ...)`: 같은 회원이 같은 조에 두 번 들어가는 것을 DB가 막는다.
  - `boolean leader`의 getter는 `isLeader()`다(Lombok 규칙).
  - `TeamMember.assignRole`: 역할 지정 시각을 서버 시각(`LocalDateTime.now()`)으로 기록한다.
  - 실행 로그의 `spring.jpa.open-in-view is enabled by default` 경고: LAZY 연관을 Controller의 `XxxResponse.from()`에서 꺼낼 수 있게 하는 기본 설정이라 지금은 켜 둔다.
- 내가 채운 가정:
  - 연관은 모두 단방향 `@ManyToOne`. `@OneToMany`는 두지 않고 목록은 Repository 조회로 얻는다.
  - "한 학생은 같은 과제에서 한 조에만"은 team_member에 과제 열이 없어 DB unique로 걸 수 없다. T-1-2에서 Service가 확인한다.
  - "조마다 조장은 한 명"도 DB가 보장하지 않는다. 과제 생성(A-1b) 때 Service가 지킨다(조장 교체 없음).
  - `Team.inviteCode`는 nullable, unique. 시작 데이터의 초대 코드는 T-1-1에서 넣는다.
  - Repository에는 아직 custom 메서드가 없다. 필요한 단위에서 추가한다.

### A-1b 과제 생성 API (A-1-1 과제 정보 입력, A-1-2 조장 지정 — 백엔드)
- 관리자 결정: 추천안대로. 없는 조장 이메일은 404가 아니라 400 `INVALID_INPUT`(주소가 아니라 body 안의 값이 틀린 것). api-draft에 `POST /assignments` 추가.
- 만든 파일: `exception/InvalidInputException`, `dto/AssignmentCreateRequest`, `dto/AssignmentResponse`, `service/AssignmentService`, `controller/AssignmentController`
- 고친 파일: `exception/GlobalExceptionHandler`(`handleInvalidInput`), `backend/README.md`(과제 생성 확인 명령), `docs/api-draft.md`
- 확인: `gradlew build` 성공. 서버를 켜고 직접 호출했다.
  - 교수 token: 200, 과제 id 2와 "1조"·"2조"·"3조", 조장 3명(`leader` true, 역할명 비어 있음)이 저장됐다.
  - 학생 token 403, token 없음 401.
  - 400: 종료일이 시작일보다 앞, 이메일 중복, 교수 이메일, 없는 이메일, 빈 배열, 빈 제목, 날짜 형식(`2026/11/02`).
  - 400으로 실패한 요청 뒤에 assignment, team 행은 늘지 않았다.
- 읽을 때 볼 곳:
  - `AssignmentService.create`의 `@Transactional`: 과제·조·조장 저장이 한 묶음이다. 예외(RuntimeException)가 나면 앞에서 저장한 것도 취소된다. 그래도 검사(`checkProfessor`, 날짜, `findLeaders`)를 저장 앞에 모아 둔 이유는 "저장했다가 취소"보다 "아예 저장하지 않음"이 읽기 쉽기 때문이다.
  - 권한 검사는 `checkProfessor(professor)` 한 줄로 Service 안에 보인다. token에는 회원 번호만 있어서 DB에서 회원을 읽어 `memberType`을 비교한다.
  - 400이 두 갈래다. 모양 검사(`@NotBlank`, `@NotNull`, `@NotEmpty`)는 Controller 앞에서 `handleNotValid`가, 업무 규칙(날짜 순서, 이메일)은 Service의 `InvalidInputException`이 맡는다.
  - 요청 body가 JSON으로 읽히지 않으면 Service까지 가지 않는다. 그래서 학생이 형식이 틀린 body를 보내면 403이 아니라 400이 먼저 나온다.
  - `Set<String> checkedEmails`: 이미 본 이메일을 모아 두고 중복을 찾는다. `List.contains`로 회원 Entity를 비교하지 않은 이유는 Entity에 `equals`가 없어서다.
- 내가 채운 가정:
  - 조 개수는 `leaderEmails` 길이. 조 이름은 "1조", "2조"…로 자동.
  - 성공 응답은 200(예시 프로젝트의 `create`와 같게). 응답에 조 목록은 넣지 않는다(A-2-2에서).
  - Service는 DTO가 아니라 값을 하나씩 받는다(예시 프로젝트와 같게).
  - 로그인한 회원은 `MemberService.findById`로 읽는다(없으면 404, `/members/me`와 같게). 조장은 `MemberRepository.findByEmail`로 찾는다(없으면 400).
  - 이메일 비교는 대소문자를 구분한다. 조 개수 상한은 없다.
  - Git Bash의 `curl -d`로 한글 body를 보내면 UTF-8로 가지 않아 400이 났다. 파일(`--data-binary @file`)로 보내서 확인했다. Windows PowerShell 5.1의 한글 제목 동작은 확인하지 못했다(README 예시는 영어 제목).

### 규칙 검사(rule-check) A-1b
- 수정 필요: 없음.
- 확인 필요 1건 고침: 256자 이상의 `title`이 DB 저장에서 500이 되던 것을 `@Size(max = 255)`로 400으로 막았다. 256자 400, 255자 200 확인.
- 선택 사항 2건 고침: api-draft `POST /assignments`의 401 문장을 `/members/me`와 같게 맞췄다. `AssignmentService`에 회원을 읽는 길이 둘인 이유를 주석으로 달았다.

### 작업 방식 변경 (관리자 결정)
- 단위마다 묻지 않고 추천안으로 진행한다. 결정은 각 단위 항목의 "결정" 줄에 선택지와 이유를 남긴다.
- 단위 하나가 확인까지 끝나면 commit 한다. push와 원격 설정은 하기 전에 반드시 관리자에게 말한다.
- CLAUDE.md의 "묻고 나서 할 것" 절을 "묻지 않고 추천안으로 진행하고 기록할 것"과 "반드시 먼저 말할 것"으로 바꿨다.
- 이 폴더를 git 저장소로 만들었다(`git init -b main`). 원격 저장소는 설정하지 않았다.

### A-1c 과제 생성 — 가짜 API와 화면
- 결정:
  - 가짜 API의 데이터 이름은 `assignments`, `teams`, `teamMembers`. 연결은 `assignmentId`, `teamId`, `memberId`(번호). 실제 DB의 FK 열과 같은 뜻이다.
  - 가짜 API는 처리하지 않는 주소를 json-server 기본 처리(`next()`)로 넘기지 않고 404 `NOT_FOUND`로 막는다. 넘기면 `/members`로 비밀번호까지 보이고, 실제 API에 없는 주소가 가짜에서만 동작한다.
  - 과제를 만든 뒤 `/assignments/{id}`(과제 상세, A-2-2)로 이동한다. A-2-2 전까지는 없는 주소라 첫 화면으로 돌아간다.
- 만든 파일: `src/api/assignments.js`, `src/pages/AssignmentCreatePage.jsx`, `.claude/launch.json`(Claude Code 브라우저 확인용 개발 서버 설정)
- 고친 파일: `mock/middleware.cjs`(주소마다 handle 함수로 나눔, `POST /assignments`, 그 밖의 주소 404), `db.json`(과제·조·조 참여 시작 데이터), `src/App.jsx`(`/assignments/new`), `src/pages/HomePage.jsx`(교수에게만 "과제 만들기" 링크), `frontend/README.md`
- 확인: `npm run build`, `npm run lint` 통과. 같은 요청 11개(성공, 403, 401, 400 8가지)를 실제 API와 가짜 API에 보내 응답 body와 status가 모두 같은 것을 확인했다. 브라우저에서 교수로 로그인해 조 추가·중복 이메일 오류 메시지·성공 후 이동을 확인했다. 확인 뒤 `db.json`을 처음 상태로 되돌렸다.
- 읽을 때 볼 곳:
  - `AssignmentCreatePage`의 `leaderEmails` 배열 state: 바꿀 때 `map`, 늘릴 때 `[...배열, '']`, 뺄 때 `filter`로 항상 새 배열을 만든다. 직접 `push`하면 React가 바뀐 것을 모른다.
  - `key={index}`: 칸에 id가 없어서 순서를 key로 쓴다. 입력값이 state에서 오기 때문에 괜찮다.
  - `submitting` state: 응답을 기다리는 동안 버튼을 막아 과제가 두 번 만들어지지 않게 한다. 성공하면 화면을 떠나므로 실패할 때만 되돌린다.
  - 교수가 아니면 링크를 숨기지만, 실제로 막는 것은 서버의 403이다. 학생이 주소를 직접 열고 제출하면 403 메시지가 보인다.
  - `mock/middleware.cjs`의 검사 순서(날짜 형식 → 빈 값 → 교수 → 날짜 순서 → 이메일)는 실제 백엔드의 순서(JSON 읽기 → @Valid → Service)를 따른다.
- 내가 채운 가정:
  - 가짜 API의 시작 데이터 역할 지정 시각은 `2026-10-25T09:00:00`으로 고정했다.
  - 실제 백엔드의 "없는 주소" 응답은 Spring 기본 모양이고 가짜는 `{ code, message }`다(미정 6, 아직 다름).
  - 학생은 이 화면 주소를 열 수 있다. 화면에서 따로 막지 않는다.

### A-2 과제 목록·상세 (A-2-1, A-2-2)
- 결정:
  - `GET /assignments`: 교수는 자기가 만든 과제, 학생은 참여한 과제. 응답 모양은 `POST /assignments`와 같은 `AssignmentResponse` 배열.
  - `GET /assignments/{assignmentId}`: 과제 + `professorName` + `teams: [{ id, name, leaderName }]`. 교수는 조 전부, 학생은 자기 조만. 다른 사람은 403.
  - 접근 검사는 `service/TeamAccessChecker` 한 곳에 모은다(demo-scope 4절 예시 이름). 지금 메서드는 `isProfessorOf`, `checkAssignmentAccess`, `checkAssignmentMember`. 조 단위 검사는 T-1부터 여기에 더한다.
  - 주소의 숫자 자리에 숫자가 아닌 값이 오면 400 `INVALID_INPUT`("주소의 값이 올바르지 않습니다"). `handleTypeMismatch` 추가. 이전에는 Spring 기본 400 모양이었다.
  - 조 목록은 "조장 기록 = 조"로 읽는다. 조마다 조장이 한 명이라 조장의 `TeamMember`에서 조 이름과 조장 이름을 함께 꺼낸다. Service는 Entity(`TeamMember` 목록)를 돌려주고 Controller가 DTO로 바꾼다.
- 만든 파일: `service/TeamAccessChecker`, `dto/AssignmentDetailResponse`, `dto/TeamSummaryResponse`, `frontend/src/pages/AssignmentDetailPage.jsx`, `frontend/src/components/AssignmentList.jsx`, `frontend/src/components/TeamSummaryList.jsx`
- 고친 파일: `repository/AssignmentRepository`, `repository/TeamMemberRepository`(이름으로 만드는 조회 메서드 5개), `service/AssignmentService`(`findAllForMember`, `findById`, `findVisibleTeamLeaders`), `controller/AssignmentController`, `exception/GlobalExceptionHandler`, `mock/middleware.cjs`, `src/api/assignments.js`, `src/pages/HomePage.jsx`(과제 목록), `src/App.jsx`, `docs/api-draft.md`
- 확인: `gradlew build`, `npm run build`, `npm run lint` 통과. 같은 요청 12개를 실제 API와 가짜 API에 보내 결과가 모두 같았다.
  - 교수 목록, 학생 목록, 교수 상세(조 2개), 1조 학생 상세(1조만), 2조 학생 상세(2조만), 404, `abc` 400, token 없음 401
  - 새 과제를 만든 뒤: 그 과제의 조장 학생 목록에 과제 2개(최신 먼저), 그 과제에 참여하지 않은 학생의 상세 403
  - 브라우저에서 교수로 목록 → 상세 → 없는 과제(오류 문장)를 확인했다. 확인 뒤 `db.json`을 처음 상태로 되돌렸다.
- 읽을 때 볼 곳:
  - `TeamMemberRepository`의 메서드 이름: `findByTeamAssignmentIdAndMemberId`는 `team.assignment.id = ? and member.id = ?`다. Spring Data JPA가 이름을 쪼개 연관을 따라간다. 이름이 길어지면 읽기 어려우니 메서드마다 무엇을 찾는지 주석을 달았다.
  - `findAllForMember`의 `.map(teamMember -> teamMember.getTeam().getAssignment())`: LAZY 연관을 꺼내는 줄이다. 꺼낼 때마다 쿼리가 나간다(시연 데이터가 작아서 그대로 둔다).
  - `AssignmentController.findById`: Service 메서드 두 개를 부른다. 첫 번째가 404·403을 걸러 내고, 두 번째가 볼 수 있는 조를 고른다.
  - `HomePage`의 `Promise.all`: 내 정보와 과제 목록을 함께 불러온다.
  - `AssignmentDetailPage`의 `useParams`: App.jsx의 `:assignmentId`와 이름이 같아야 값이 들어온다.
- 내가 채운 가정:
  - 목록 순서는 교수는 과제 id 큰 순, 학생은 참여 기록 id 큰 순(합류 최신순).
  - 목록에는 교수 이름을 넣지 않았다(상세에만).
  - 조 링크(`/teams/{teamId}`)는 다음 단위(T-1)에서 화면을 만든다. 지금은 없는 주소라 첫 화면으로 간다.

### T-1a 조 정보·조원 목록·초대 코드 발급·합류 — 백엔드와 가짜 API (T-1-1, T-1-2)
- 결정:
  - 미정 5(같은 회원이 같은 조에 다시 합류): 추천안으로 오류 없이 200, 그 조를 돌려준다. 선택지는 (A) 200 그대로 성공, (B) 409 `DUPLICATE`였다. A는 링크를 두 번 눌러도 결과가 같아 화면 처리가 단순하고, B는 "이미 있음"을 알려 주지만 화면이 409를 성공처럼 다시 처리해야 한다. 같은 과제의 **다른** 조에 있으면 409다.
  - 주소: `GET /teams/{teamId}`, `GET /teams/{teamId}/members`, `GET·POST /teams/{teamId}/invite-code`, `POST /teams/join { inviteCode }`. 초대 링크는 화면 주소 `/join/{inviteCode}`.
  - 초대 코드는 `UUID.randomUUID()`. 시작 데이터는 시험하기 쉽게 `demo-team-1`, `demo-team-2`.
  - 초대 코드 보기·발급은 조장만(교수도 403). demo-scope "초대 링크는 조장만 발급한다"를 보기에도 적용했다.
  - 교수는 합류할 수 없다(403). 조원 목록 응답은 `{ id(조 참여 번호), memberId, name, roleName, leader }`.
  - Entity 변경: `Team.changeInviteCode(String)` 메서드 추가(필드는 그대로).
- 만든 파일: `service/TeamService`, `controller/TeamController`, `dto/TeamResponse`, `dto/TeamMemberResponse`, `dto/InviteCodeResponse`, `dto/TeamJoinRequest`
- 고친 파일: `entity/Team`, `repository/TeamRepository`(`findByInviteCode`), `repository/TeamMemberRepository`(2개 추가), `service/TeamAccessChecker`(`checkTeamAccess`, `checkTeamMember`, `checkLeader`), `config/DataInitializer`(초대 코드), `backend/README.md`, `mock/middleware.cjs`, `db.json`, `docs/api-draft.md`
- 확인: `gradlew build` 통과. 같은 요청 25개를 실제 API와 가짜 API에 보내 결과가 모두 같았다(UUID와 새로 생긴 id만 가려서 비교).
  - 조 정보·조원 목록: 조원 200, 교수 200, 다른 조 403, 없는 조 404, `abc` 400
  - 초대 코드: 조장 200, 조원·교수 403, 발급 전 `null`
  - 합류: 같은 조 다시 200, 같은 과제 다른 조 409, 교수 403, 없는 코드 404, 빈 코드 400
  - 재발급: 재발급 뒤 이전 코드 404, 새 코드로 합류 200, 합류 뒤 조원 목록과 과제 목록에 나타남
- 읽을 때 볼 곳:
  - `TeamService.issueInviteCode`: `save`를 부르지 않는다. `@Transactional` 안에서 읽은 Entity의 값을 바꾸면 트랜잭션이 끝날 때 JPA가 바뀐 것을 찾아 UPDATE 한다(변경 감지, dirty checking).
  - `TeamService.join`: 검사 순서가 응답 코드를 정한다. 코드 없음(404) → 학생 아님(403) → 같은 과제에 이미 있음(같은 조면 200, 다른 조면 409) → 저장.
  - `TeamAccessChecker`의 세 메서드는 쓰는 곳이 다르다. `checkTeamAccess`는 보기(교수 포함), `checkTeamMember`는 조원만 하는 일(교수 제외), `checkLeader`는 조장만 하는 일이다.
  - `TeamController`의 `/join`과 `/{teamId}`: 주소 모양이 비슷하지만 메서드(POST)와 글자가 달라 섞이지 않는다. `GET /teams/join`은 `{teamId}`로 읽혀 400이 된다.
- 내가 채운 가정:
  - 조원 목록은 합류 순서(조 참여 id 순)라서 조장이 맨 앞이다.
  - 초대 코드에 만료 시간은 없다(다시 발급하기 전까지 유효).
  - 시작 학생 10명은 모두 시작 과제의 조에 있으므로, 합류 시연은 새 과제를 만들어서 한다(README에 적음).

### T-1b 조 화면·초대 링크·합류 화면 (T-1-1, T-1-2 — 화면)
- 결정:
  - 조 화면 `/teams/:teamId`: 조원 목록, 조장에게만 초대 링크와 발급 버튼.
  - 합류 화면 `/join/:inviteCode`: 열자마자 합류하지 않고 "합류하기" 버튼을 누르게 했다. 잘못 연 링크로 바로 합류되지 않게 하고, StrictMode가 useEffect를 두 번 실행해 요청이 두 번 가는 것도 피한다.
  - 로그인하지 않고 초대 링크를 열면 로그인 뒤 그 링크로 돌아온다. `RequireLogin`이 원래 주소를 `state.from`으로 넘기고 `LoginPage`가 그 주소로 이동한다.
- 만든 파일: `src/api/teams.js`, `src/pages/TeamPage.jsx`, `src/pages/JoinPage.jsx`, `src/components/TeamMemberList.jsx`, `src/components/InviteLinkPanel.jsx`
- 고친 파일: `src/App.jsx`(두 주소), `src/components/RequireLogin.jsx`(`state.from`), `src/pages/LoginPage.jsx`(원래 주소로 이동)
- 확인: `npm run build`, `npm run lint` 통과. 실제 백엔드로 브라우저에서 확인했다.
  - 로그아웃 상태로 `/join/demo-team-1`을 열면 로그인 화면으로 갔다가, 로그인 뒤 합류 화면으로 돌아왔다.
  - 2조 학생이 1조 코드로 합류하면 "이미 이 과제의 다른 조에 있습니다"가 보였다.
  - 새 과제의 링크로 합류하면 조 화면으로 이동했고, 조원 목록에 "역할 없음"으로 나왔다.
  - 조장으로 조 화면을 열면 초대 링크가 보였고, "다시 발급"을 누르면 링크가 새 코드로 바뀌었다.
- 읽을 때 볼 곳:
  - `TeamPage`의 `isLeader`: 내 정보와 조원 목록으로 계산하는 값이라 state로 두지 않는다(CLAUDE.md "계산되는 값은 state로 두지 않는다").
  - `TeamPage`의 `useEffect` 안 `return getInviteCode(...)`: `.then` 안에서 promise를 return 하면 그 요청까지 끝난 뒤에 `.finally`가 실행된다. return을 빼면 초대 코드가 오기 전에 loading이 끝난다.
  - `error`와 `actionError`를 나눈 이유: 불러오기에 실패하면 화면 전체를 오류로 바꾸고, 발급에 실패하면 화면은 그대로 두고 메시지만 보인다.
  - `InviteLinkPanel`의 `window.location.origin`: 지금 화면의 주소(예: `http://localhost:5173`)다. 배포 주소가 바뀌어도 코드를 고치지 않아도 된다.
  - `navigate(from, { replace: true })`: 로그인 화면을 방문 기록에서 지워 뒤로 가기로 다시 로그인 화면에 오지 않게 한다.
- 내가 채운 가정:
  - 링크 복사 버튼은 두지 않았다(화면 디자인은 뒤로 미룬 항목). 링크를 직접 선택해 복사한다.
  - 교수는 조 화면에서 조원 목록만 본다(초대 링크 없음).

### T-2-1 역할 지정
- 결정:
  - `POST /teams/{teamId}/members/{teamMemberId}/role { roleName }`. 조장만, 역할이 없는 조원에게만 지정한다. 이미 역할이 있으면 409 `DUPLICATE`(역할 변경 T-2-2는 2단계).
  - 메서드는 POST로 정했다. 선택지는 (A) POST: 한 번만 지정하는 "새로 만들기"에 가깝고 CORS 허용 목록을 바꿀 필요가 없다. (B) PUT/PATCH: "바꾸기"에 가까워 2단계의 역할 변경과 헷갈리고, PATCH는 CORS 허용 목록에 더해야 한다. A를 골랐다. 이 결정으로 0단계 rule-check의 "CORS 허용 method 목록을 T-2-1의 method와 맞춘다"는 바꿀 것이 없어 끝났다.
  - 역할명 앞뒤 공백은 서버가 지운다(산출물 순번이 역할명으로 나뉘기 때문).
  - 조원 번호는 조 번호와 함께 확인한다. 다른 조의 조원 번호면 404.
- 만든 파일: `dto/RoleAssignRequest`, `frontend/src/components/RoleAssignForm.jsx`
- 고친 파일: `service/TeamService`(`assignRole`), `controller/TeamController`, `mock/middleware.cjs`, `src/api/teams.js`, `src/components/TeamMemberList.jsx`, `src/pages/TeamPage.jsx`, `docs/api-draft.md`
- 확인: `gradlew build`, `npm run build`, `npm run lint` 통과. 같은 요청 11개를 실제 API와 가짜 API에 보내 결과가 모두 같았다.
  - 조장 자신에게 지정(공백 제거 확인), 조원에게 지정
  - 다시 지정 409, 조원 403, 교수 403, 다른 조의 조원 404, 없는 조 404, 빈 역할명 400, `abc` 400, 시작 데이터 조원(이미 역할 있음) 409
  - DB의 `role_assigned_at`에 서버 시각이 기록됐다. 브라우저에서 조장으로 역할 없는 조원에게 "디자인"을 지정하면 목록이 바로 바뀌었다.
- 읽을 때 볼 곳:
  - `TeamService.assignRole`의 `.filter(teamMember -> teamMember.getTeam().getId().equals(teamId))`: Optional에도 filter가 있다. 조건에 맞지 않으면 빈 Optional이 되어 `orElseThrow`로 간다.
  - `TeamMember.assignRole`: 시각을 서버가 넣는다. 요청에는 시각이 없다.
  - `TeamPage`의 `roleNameInputs`: 조원마다 입력칸이 있어서 `{ 조 참여 번호: 입력값 }` 객체 하나로 관리한다. 바꿀 때 `{ ...이전, [id]: 값 }`으로 새 객체를 만든다.
  - `setMembers(members.map(...))`: 서버가 돌려준 조원 한 명으로 그 줄만 바꾼다. 목록 전체를 다시 불러오지 않는다.
  - 화면은 form 상태(입력값)도 page가 갖고, `TeamMemberList`·`RoleAssignForm`은 props로만 그린다.
- 내가 채운 가정:
  - 가짜 API의 `roleAssignedAt`은 JavaScript의 `toISOString()`(UTC)로 저장한다. 응답에 나오지 않는 값이라 모양을 맞추지 않았다.

### R-1a 산출물 업로드·목록·다운로드 — 백엔드 (R-1-1, R-1-2)
- 결정:
  - 엔티티 영문 이름 `Deliverable`(테이블 deliverable). ERD 확정본에 이름이 없어 정했다. 선택지는 `Deliverable`(과제 결과물), `Artifact`(개발 산출물에 흔하지만 빌드 결과물과 헷갈림), `Output`. 학습 문서의 "산출물(과제 결과물)"에 가장 가까운 `Deliverable`을 골랐다.
  - 필드: `teamMember`(올린 조원의 조 참여), `roleName`(복사), `fileName`, `fileSize`, `storageKey`, `uploadedAt`. demo-scope "파일 위치, 이름, 크기만"을 지켜 Content-Type은 데이터베이스에 두지 않고, 다운로드할 때 확장자로 정한다(`MediaTypeFactory`).
  - 순번은 응답에 넣지 않고 화면이 센다(demo-scope 4절 "화면은 … 순번을 붙이고"). 학습 저장소 `03_leader.md`에는 "산출물 버전 번호 규칙(같은 역할 안에서 1씩 증가)을 정한다"는 할 일이 있다. 나중에 버전을 열로 저장하기로 정해지면 `Deliverable`에 열을 더한다.
  - 주소: `POST·GET /teams/{teamId}/deliverables`, `GET /deliverables/{deliverableId}/download`.
  - 올리기는 역할이 있는 조원만(교수 403, 역할 없음 403). 보기·다운로드는 조원과 그 과제의 교수.
  - 업로드 순서는 "MinIO 저장 → DB 저장"(0단계 rule-check에서 정한 대로). DB 저장이 실패하면 MinIO에 주인 없는 파일이 남는다.
  - `uploadedAt`은 초 아래를 버린다(`truncatedTo(SECONDS)`).
  - 400 응답 3가지 추가: `handleMissingPart`(file 칸 없음), `handleMultipart`(multipart가 아니거나 20MB 초과). 0단계에서 정한 code 목록 안(`INVALID_INPUT`)이다.
  - 시작 데이터 산출물 2건(1조 기획서.txt, API 명세.txt). `StorageService.save(String contentType, byte[])`를 더해 시작 데이터도 같은 저장 메서드를 쓴다.
- 만든 파일: `entity/Deliverable`, `repository/DeliverableRepository`, `service/DeliverableService`, `controller/DeliverableController`, `dto/DeliverableResponse`
- 고친 파일: `service/StorageService`(byte 배열 저장), `exception/GlobalExceptionHandler`, `config/DataInitializer`(산출물 2건, `saveTeam`이 조 참여 목록을 돌려줌), `application.yml`(20MB), `backend/README.md`, `docs/api-draft.md`
- 확인: `gradlew build` 통과. 실제 API에 직접 호출했다.
  - 목록: 조원 200, 교수 200, 다른 조 403
  - 업로드: 역할 있는 조원 200(그때 역할명 복사), 교수 403, 다른 조 403, 역할 없는 조원 403, 빈 파일 400, file 칸 없음 400, multipart 아님 400, 없는 조 404, 21MB 400
  - 다운로드: 교수가 시작 산출물을 받으면 `Content-Disposition: attachment; filename*=UTF-8''%EA%B8%B0...`(RFC 5987)와 원래 내용이 왔다. 다른 조 403, 없는 산출물 404, `abc` 400.
  - Git Bash의 `curl -F`는 한글 파일 이름을 UTF-8로 보내지 않아 이름이 깨졌다(서버 문제 아님). 화면(R-1c)에서 다시 확인한다.
- 읽을 때 볼 곳:
  - `Deliverable.roleName`: 조 참여의 역할명을 참조하지 않고 값을 복사한다. 연관으로 두면 역할이 바뀔 때 예전 산출물의 역할도 바뀐다.
  - `DeliverableController.download`의 `ResponseEntity<Resource>`: body가 JSON이 아니라 파일이다. header(`Content-Type`, `Content-Length`, `Content-Disposition`)를 직접 붙인다.
  - `ContentDisposition.attachment().filename(이름, UTF_8)`: Spring이 `filename*=UTF-8''...`을 만들어 준다. 옛 브라우저용 `filename="=?UTF-8?Q?...?="`도 함께 붙는다.
  - `handleMultipart`의 `instanceof`: 20MB 초과 예외가 multipart 예외의 한 종류라서 handler 하나에서 메시지만 나눈다.
  - `@Transactional` 안에서 MinIO에 저장한다. DB가 rollback 되어도 MinIO 파일은 되돌아가지 않는다(트랜잭션은 DB만 묶는다).
- 내가 채운 가정:
  - 파일 이름이 비어 오면 `"file"`로 저장한다.
  - 시작 산출물의 Content-Type은 MinIO에 `text/plain; charset=UTF-8`로 넣지만, 다운로드 응답은 확장자로 정한 `text/plain`이다.

### R-1b 산출물 — 가짜 API
- 결정:
  - multipart 업로드는 새 npm 의존성 없이 `mock/middleware.cjs`에서 직접 읽는다(`readRawBody`, `findFilePart`). 선택지는 (A) 직접 읽기 약 40줄, (B) `multer` 추가(의존성 하나, 코드는 짧음)였다. 가짜 API 하나 때문에 조원 전원의 `npm install`이 늘어나는 것보다 A가 낫다고 봤다.
  - 파일 본문은 `frontend/mock/files/`에 둔다. 시작 파일 `seed-1.txt`, `seed-2.txt`만 저장소에 올리고 나머지는 `.gitignore`.
  - 가짜 API의 시각(`uploadedAt`, `roleAssignedAt`)을 실제 응답과 같은 `yyyy-MM-ddTHH:mm:ss`로 맞췄다(`nowLocalDateTime`). T-2-1 때 넣은 `toISOString()`도 이것으로 바꿨다.
- 만든 파일: `frontend/mock/files/seed-1.txt`, `frontend/mock/files/seed-2.txt`
- 고친 파일: `mock/middleware.cjs`(목록·업로드·다운로드), `db.json`(`deliverables` 2건), `.gitignore`, `frontend/README.md`
- 확인: R-1a와 같은 요청 20개를 실제와 가짜에 보냈다. JSON 응답과 status는 모두 같았고, 다운로드 header의 세부 모양만 달랐다(`Content-Disposition`의 옛 브라우저용 `filename=`, `Content-Type`의 `charset`). 다운로드한 내용은 같았다.
- 읽을 때 볼 곳:
  - `findFilePart`: multipart body가 `--boundary`로 칸을 나누는 모양을 그대로 따라 읽는다. 실제 백엔드에서는 Spring이 이 일을 한다는 것만 알면 된다.
  - `Buffer.from(이름, 'latin1').toString('utf8')`: header를 1byte씩 읽은 뒤 파일 이름만 UTF-8로 다시 읽어 한글 이름을 살린다.
  - `handleUploadDeliverable` 안의 `readRawBody(req).then(...)`: body를 다 읽을 때까지 기다린 뒤 검사한다. 그래서 검사 코드가 `.then` 안에 있다.
- 내가 채운 가정:
  - 가짜 API에서 20MB를 넘으면 body를 끝까지 읽은 뒤 400을 보낸다(읽는 도중에 끊지 않는다).

### R-1c 산출물 화면 (R-1-1, R-1-2 — 화면)
- 결정:
  - 산출물은 조 화면과 나눠 `/teams/:teamId/deliverables` 화면 하나로 둔다. 조 화면에 링크를 둔다. 한 page가 조원·초대·역할·산출물을 모두 갖으면 초심자가 읽기 어렵다.
  - 순번은 `DeliverableList`가 목록에서 센다(같은 역할 중 id가 작거나 같은 것의 개수). 표시는 `[백엔드 2번째]`. 목록은 하나로 두고 최신이 맨 위다.
  - 다운로드는 `<a href>`로 바로 열지 않고 `fetch`로 받는다. `<a href>`는 token header를 붙일 수 없다. 받은 Blob을 임시 주소(object URL)로 만들어 `<a download>`를 누른다.
  - `client.js`를 `send`(공통: token, 실패 처리) + `request`(JSON) + `requestBlob`(파일)로 나눴다. 실패 처리 규칙은 그대로다.
- 만든 파일: `src/api/deliverables.js`, `src/pages/DeliverablesPage.jsx`, `src/components/DeliverableList.jsx`, `src/components/DeliverableUploadForm.jsx`
- 고친 파일: `src/api/client.js`, `src/App.jsx`, `src/pages/TeamPage.jsx`(산출물 링크)
- 확인: `npm run build`, `npm run lint` 통과. 실제 백엔드로 브라우저에서 확인했다.
  - 역할이 있는 조원(프론트엔드)이 한글 이름 파일 `화면 설계서 수정본.txt`를 올리면 이름이 깨지지 않고 목록 맨 위에 `[프론트엔드 1번째]`로 나왔다. 기존 목록의 순번은 기획 1·2, 백엔드 1·2로 맞았다.
  - 다운로드 경로는 화면과 같은 방식(fetch + token)으로 받아 내용이 같은 것을 확인했다. 관리자 컴퓨터에 파일이 저장되지 않게 "다운로드" 버튼은 누르지 않았다.
  - 파일 고르기는 브라우저 자동화의 한계로 화면 JavaScript에서 파일 칸에 시험 파일을 넣어 했다. 올리기 버튼은 화면에서 눌렀다.
- 읽을 때 볼 곳:
  - `client.js`의 `send`/`request`/`requestBlob`: 성공 응답을 JSON으로 읽을지 Blob으로 읽을지만 다르다.
  - `uploadDeliverable`의 `FormData`: `formData.append('file', file)`의 `'file'`이 서버의 `@RequestParam("file")`과 같아야 한다. Content-Type은 붙이지 않는다(브라우저가 구분선과 함께 붙인다).
  - `DeliverableUploadForm`의 `key={inputKey}`: 파일 칸은 value로 비울 수 없어서, key를 바꿔 React가 칸을 새로 만들게 한다.
  - `saveBlob`의 `URL.revokeObjectURL`: 임시 주소를 다 쓴 뒤 지워 메모리를 돌려준다.
  - `setDeliverables([created, ...deliverables])`: 새 산출물을 맨 앞에 둔 새 배열이다(최신이 맨 위).
- 내가 채운 가정:
  - 교수와 역할 없는 조원에게는 올리기 form을 숨긴다. 역할 없는 조원에게는 "역할이 배정되면 산출물을 올릴 수 있습니다"를 보인다.
  - 올린 시각은 `T`를 공백으로 바꿔 `2026-10-25 22:35:55`처럼 보인다.

### E-2 동료평가 입력·제출 (E-2-1, E-2-3 — 백엔드, 가짜 API, 화면)
- 결정:
  - 주소는 `POST /teams/{teamId}/evaluations`(제출)와 `GET /teams/{teamId}/evaluations/mine`(내 제출 여부)다. 선택지는 (A) 제출 여부만 돌려주기, (B) 내가 낸 점수도 돌려주기였다. 업무 규칙이 "평가와 의견은 교수만 열람"이라서 A로 했다. 제출한 사람도 자기가 낸 점수를 다시 볼 수 없다.
  - 엔티티 이름은 `Evaluation`, 점수 열은 항목 이름(`goalSharing`, `roleFulfillment`, `encouragement`, `onlineCommunication`, `offlineCommunication`, `progressCheck`)으로 했다. `s1`~`s6`보다 열 이름만 보고 무슨 항목인지 알 수 있다. 같은 이름을 요청 body, `db.json`, 화면 상수의 `key`에 똑같이 쓴다.
  - 평가자·대상은 회원이 아니라 조 참여(`TeamMember`)를 참조한다(`evaluator_id`, `target_id`). (evaluator_id, target_id)에 unique 제약을 뒀다.
  - 검사 순서: body 검사(@Valid) 400 → 조 404 → 조원이 아니면 403(교수 포함) → 이미 제출 409 → 대상이 "나를 뺀 조원 전원 한 번씩"이 아니면 400. 이미 제출한 사람이 틀린 body를 보내도 409가 먼저다.
  - "이미 제출"은 평가자 기준으로 본다. 대상 한 명이라도 평가한 행이 있으면 409다. 한 번에 전원을 저장하므로 일부만 저장된 상태는 생기지 않는다(`@Transactional`).
  - Service는 요청 DTO의 `EvaluationSubmitRequest.Item` 목록을 그대로 받는다. 다른 Service처럼 값을 하나씩 넘기면 6개 점수 × 조원 수라서 목록으로 넘겼다.
  - 화면은 `/teams/:teamId/evaluation` 하나에서 "입력 → 확인(한 표로 전원) → 제출" 순서다. 확인 단계에서 "고치기"로 돌아가도 입력은 남는다. 조 화면의 "동료평가" 링크는 조원에게만 보인다.
  - 평가 항목 문장은 `src/constants/peerEvaluation.js`에 demo-scope 7절 문장 그대로 뒀다. 2점·4점은 "5점과 3점의 중간", "3점과 1점의 중간"으로 표시한다. 위쪽 고정 문구 2개도 같은 파일에 뒀다.
- 만든 파일:
  - backend: `entity/Evaluation.java`, `repository/EvaluationRepository.java`, `dto/EvaluationSubmitRequest.java`, `dto/EvaluationStatusResponse.java`, `service/EvaluationService.java`, `controller/EvaluationController.java`
  - frontend: `src/constants/peerEvaluation.js`, `src/api/evaluations.js`, `src/pages/EvaluationPage.jsx`, `src/components/EvaluationNotice.jsx`, `src/components/EvaluationTargetForm.jsx`, `src/components/EvaluationReview.jsx`
- 고친 파일: `docs/api-draft.md`, `frontend/mock/middleware.cjs`, `frontend/db.json`(`evaluations: []`), `src/App.jsx`, `src/pages/TeamPage.jsx`, `backend/README.md`, `frontend/README.md`
- 확인:
  - `gradlew build`, `npm run build`, `npm run lint` 통과.
  - 같은 요청 23개(제출 여부, 빈 목록, 빠진 점수, 0점·6점, 대상 빠짐·중복·자기 자신·다른 조, 교수·다른 조 403, 없는 조 404, 정상 제출, 다시 제출 409)를 실제와 가짜에 보냈다. 응답과 status가 모두 같았다.
  - 데이터베이스에 unique 제약 `evaluation_evaluator_id_target_id_key`가 생긴 것과, 한 번에 낸 행의 `submitted_at`이 모두 같은 것을 확인했다. 의견의 앞뒤 공백은 지워져 저장됐다.
  - 실제 백엔드로 브라우저에서 student03이 제출했다. 아무것도 고르지 않고 확인을 누르면 빠진 조원 이름이 나오고, 확인 표에 4명의 점수와 의견이 나오고, 제출 후 "제출했습니다. 제출 시각: ..."이 나왔다. 저장된 행이 입력과 같았다.
- 읽을 때 볼 곳:
  - `EvaluationSubmitRequest`의 `@Valid`(목록 안쪽까지 검사)와 `Integer`(빠지면 null이라 `@NotNull`에 걸림, `int`면 0이 됨).
  - `EvaluationService.submit`의 `Set.add`가 false면 중복, `submittedIds.equals(targetsById.keySet())`로 "전원 한 번씩"을 한 줄로 확인하는 곳.
  - `Evaluation` 생성자에 시각을 밖에서 넘기는 이유: 한 번에 제출한 행이 같은 시각을 갖게 한다.
  - `EvaluationPage`의 `inputs` 모양 `{ [조 참여 번호]: { goalSharing: 5, ..., comment: '' } }`과 `handleScoreChange`의 두 겹 spread.
  - radio의 `name={`${target.id}-${criterion.key}`}`: 같은 name끼리 하나만 고를 수 있다.
- 내가 채운 가정:
  - 서술 의견은 2000자까지. 공백만 있으면 저장하지 않는다(null).
  - 같은 사람이 동시에 두 번 제출하면(두 요청이 거의 같은 순간) 두 번째는 unique 제약에 걸려 500이 된다. 데이터는 지켜지고, 화면은 제출 중 버튼을 막아서 시연에서는 생기지 않는다. 409로 바꾸는 처리는 하지 않았다.
  - 교수가 동료평가 주소를 직접 열면 403 메시지가 화면에 나온다(교수 열람은 2단계).
  - 시작 데이터에는 평가를 넣지 않았다.

### 한 번에 실행 (2단계 — docker compose로 백엔드·화면·PostgreSQL·MinIO·시작 데이터)
- 결정:
  - 저장소 맨 위에 `docker-compose.yml`을 새로 두고, PostgreSQL·MinIO는 `include: infra/docker-compose.yml`로 가져온다. 선택지는 (A) include, (B) 맨 위 파일에 네 서비스를 모두 다시 쓰기, (C) infra 파일에 백엔드·화면을 넣고 profile로 나누기였다. 같은 설정을 두 곳에 쓰지 않고, 개발할 때의 `infra`만 켜는 방법도 그대로 남는 A로 했다. 이름이 둘 다 `footprint-demo`라 같은 컨테이너·volume을 쓴다.
  - 화면은 개발 서버가 아니라 nginx 이미지로 내려준다(demo-scope에서 "밀리면 개발 서버로 시연"이라 한 항목). Node 이미지로 `npm run build` → `nginx:1.28-alpine`이 `dist`를 내려주는 두 단계 Dockerfile. `/teams/1`처럼 직접 연 주소가 404가 되지 않게 `try_files ... /index.html`.
  - 백엔드는 JDK 이미지에서 `bootJar -x test`로 jar를 만들고 JRE 이미지에서 실행하는 두 단계 Dockerfile. 테스트는 실제 DB·MinIO가 있어야 해서 이미지 빌드에서는 뺐다.
  - 새로 clone 한 폴더에서 `.env` 없이 켜지도록 `infra/docker-compose.yml`의 값을 `${이름:-기본값}`(기본값은 `.env.example`과 같음)으로 바꿨다. 백엔드는 `infra/.env`가 있으면 읽는다(`env_file`, `required: false`).
  - `application.yml`의 DB 주소에 `${POSTGRES_HOST:localhost}`를 넣었다. 컨테이너 안에서는 `postgres`, MinIO는 `STORAGE_ENDPOINT=http://minio:9000`.
  - 백엔드가 DB·MinIO 준비 뒤에 켜지도록 `depends_on: condition: service_healthy`. MinIO에 healthcheck(`/minio/health/live`)를 새로 붙였다.
  - 컨테이너 시간대가 UTC라 기록 시각이 9시간 이르게 저장되는 것을 확인해서 백엔드에 `TZ: Asia/Seoul`을 넣었다.
  - 백엔드·화면 포트는 `127.0.0.1`에만 열고, 화면 이미지의 API 주소도 `http://127.0.0.1:8080`으로 했다. 아래 "문제" 참고. 화면은 그대로 `http://localhost:5173`으로 연다(CORS 허용 주소). 같은 이유로 LAN의 다른 컴퓨터에서는 접속할 수 없다(시연은 한 컴퓨터).
  - `server.tomcat.max-swallow-size: 100MB`를 넣었다. 20MB를 넘는 업로드에서 Tomcat이 기본 2MB만 읽고 연결을 끊으면 브라우저가 400 대신 연결 오류를 볼 수 있다.
- 문제와 처리:
  - 21MB 업로드 시험 뒤 `localhost:8080`(IPv6 `::1`)이 응답하지 않게 됐다. `::1` 쪽은 Docker Desktop이 아니라 WSL의 `wslrelay.exe`가 중계하고 있었고, 그 프로세스가 멈춘 상태로 남았다. `127.0.0.1:8080`은 정상이었다. 멈춘 프로세스를 끝내는 것은 권한 검사에서 막혀서 하지 않았다. 대신 위처럼 앱이 `::1` 중계를 쓰지 않게 했다.
  - 관리자 컴퓨터에는 지금도 멈춘 `wslrelay.exe`가 `[::1]:8080`, `[::1]:5432`를 잡고 있다. Docker Desktop을 다시 시작하거나 PowerShell에서 `wsl --shutdown` 하면 풀린다. 풀기 전에는 개발용 백엔드(`bootRun`)가 8080을 못 잡거나 브라우저의 `localhost:8080`이 멈출 수 있다.
- 만든 파일: `docker-compose.yml`, `README.md`(맨 위), `backend/Dockerfile`, `backend/.dockerignore`, `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/.dockerignore`
- 고친 파일: `infra/docker-compose.yml`(기본값, MinIO healthcheck), `backend/src/main/resources/application.yml`(`POSTGRES_HOST`, `max-swallow-size`), `backend/README.md`, `frontend/README.md`
- 확인:
  - `docker compose up -d --build`로 네 컨테이너가 켜지고 시작 데이터(과제·조·조원·산출물 2건, MinIO 파일 본문)가 들어간 것을 확인했다. 시작 산출물 다운로드 내용이 맞았다.
  - 지금까지 단위의 시험 요청(A-1, A-2, T-1, T-2, R-1, E-2 모두 99개)을 컨테이너 백엔드에 보냈다. 500이나 무응답이 없었고, E-2는 이전 실제 응답과 같았다. 21MB 업로드는 400 "파일은 20MB까지 올릴 수 있습니다"이고 그 뒤에도 서버가 응답했다.
  - 브라우저로 `http://localhost:5173/teams/1/deliverables`를 바로 열어(nginx 주소 처리) 목록, 한글 이름 파일 업로드, 21MB 업로드의 오류 문구, 한국 시간 표시를 확인했다.
  - 이미지 안 화면 파일에 API 주소 `http://127.0.0.1:8080`이 들어간 것을 확인했다.
  - commit 뒤 저장소를 새 폴더에 clone 하고(`infra/.env` 없음) `docker compose build --no-cache` → `up -d`로 켰다. 빌드와 시작이 성공했고, R-1·E-2 시험 요청 결과가 같았다. 브라우저에서 교수로 로그인해 과제 목록이 보였고, 교수의 조 화면에는 "동료평가" 링크가 없었다(E-2 때 확인하지 못한 것).
- 읽을 때 볼 곳:
  - Dockerfile의 두 단계(`AS build` → `COPY --from=build`): 만드는 도구(JDK, Node)는 최종 이미지에 들어가지 않는다.
  - `COPY package*.json` → `npm ci` → `COPY . .` 순서: 소스만 바뀌면 설치 단계를 cache로 건너뛴다. 백엔드의 `gradlew dependencies`도 같은 이유다.
  - `VITE_API_URL`은 실행할 때가 아니라 **빌드할 때** 화면 파일에 들어간다. 주소를 바꾸면 `--build`로 다시 만들어야 한다.
  - compose의 `environment`가 `env_file`보다 우선이라 `POSTGRES_PORT`를 컨테이너 안쪽 포트 5432로 덮어쓴다.
  - 컨테이너끼리는 `localhost`가 아니라 서비스 이름(`postgres`, `minio`)으로 찾아간다.
- 내가 채운 가정:
  - 이미지 버전: `eclipse-temurin:21-jdk`/`21-jre`, `node:24-alpine`, `nginx:1.28-alpine`.
  - JWT 서명 키는 컨테이너에서도 `application.yml`의 개발용 기본값을 쓴다(시연용).
  - Postman 모음은 다음 단위로 남겼다.

### Postman 모음 (2단계)
- 결정:
  - 위치는 `docs/postman/`(모음 JSON, 업로드 시험 파일, README). 새 최상위 폴더를 만들지 않고 문서 폴더에 둔다.
  - 환경(Environment) 파일 없이 **모음 변수**만 쓴다. 선택지는 (A) 모음 변수, (B) 환경 파일 따로(실제·가짜 2개)였다. Import 한 번으로 끝나는 A로 했다. 가짜 API는 `baseUrl` 하나만 바꾸면 된다.
  - 순서는 시연 흐름 그대로 폴더 0~5. 요청마다 test script가 상태 코드를 확인하고 다음 요청에 쓸 값(token, 과제·조·조 참여 번호, 초대 코드, 산출물 번호)을 저장한다. 그래서 Run collection 한 번이 1단계 흐름 전체의 회귀 시험이 된다.
  - 시작 과제 대신 **새 과제를 만들어** 흐름을 진행한다. 시작 학생은 모두 시작 과제의 조에 있어서 합류를 시험할 수 없고, 새 과제를 쓰면 서버를 다시 켜지 않고 몇 번이든 다시 실행할 수 있다.
  - `baseUrl` 기본값은 `http://127.0.0.1:8080`(한 번에 실행 단위의 `::1` 문제와 같은 이유).
  - 확인 도구로 Newman 6.2.1을 `npx`로만 썼다. `package.json`에 넣지 않았다(조원 전원의 `npm install`이 늘지 않게). README에는 선택 사항으로 적었다.
  - 동료평가 body의 `"targetTeamMemberId": {{leaderTeamMemberId}}`는 따옴표 없이 넣었다. 따옴표를 붙이면 문자열 `"5"`가 되어 가짜 API가 400을 준다(실제 백엔드는 숫자로 바꿔 받는다). Postman 편집기가 이 줄에 JSON 경고를 보일 수 있지만 보낼 때는 숫자로 바뀐다.
- 만든 파일: `docs/postman/footprint-demo.postman_collection.json`(요청 29개, test 52개), `docs/postman/sample-upload.txt`, `docs/postman/README.md`
- 고친 파일: `README.md`(문서 목록)
- 확인:
  - 컨테이너 백엔드(`127.0.0.1:8080`)에 Newman으로 실행: 요청 29개, test 52개 모두 통과. 서버를 다시 켜지 않고 한 번 더 실행해도 모두 통과.
  - 가짜 API(`localhost:3001`)에 같은 모음 실행: 모두 통과. 실행 뒤 `db.json`을 되돌리고 올린 시험 파일을 지웠다.
  - Postman 앱에서 직접 Import 해 보지는 않았다(이 컴퓨터에서 Postman 화면을 조작하지 않았다). 파일 형식은 Newman이 읽는 Collection v2.1과 같다.
- 읽을 때 볼 곳:
  - 로그인 요청의 test script `pm.collectionVariables.set('professorToken', ...)`: 응답에서 값을 꺼내 변수에 넣고, 뒤 요청의 Authorization 탭이 `{{professorToken}}`으로 쓴다.
  - "조원 목록 (조장)"의 `members.find((m) => m.leader).id`: 조원 목록에서 조 참여 번호를 골라 역할 지정·동료평가 주소에 쓴다.
  - 업로드 요청의 form-data `file` 칸: 서버의 `@RequestParam("file")`과 이름이 같아야 한다.
- 내가 채운 가정:
  - 시험 계정 배치: 조장 student01, 조원 student02, 다른 조 student06(새 과제의 2조 조장).
  - 20MB를 넘는 업로드는 큰 파일이 필요해서 모음에 넣지 않았다(시험 스크립트에서 확인함).

### 전체 rule-check (backend/src, frontend/src, frontend/mock)
- 검사: 금지 패턴 grep(백엔드·프론트엔드·계산 코드), 클래스·public 메서드 주석, Controller·Service 구조, `@Transactional`, 접근 범위 검사, 시각 출처, 화면 state, 실제·가짜 API 응답 비교(경계값 10개를 양쪽에 보냄).
- 문제 없음: 금지 라이브러리·어노테이션 없음(`StorageServiceTests`의 `@Autowired`는 테스트 생성자 주입에 필요해서 둠), 화면의 `async/await`·`src/api` 밖 `fetch` 없음, 점수 합계·평균·순위 계산 없음, Controller가 Entity를 그대로 돌려주는 곳 없음, 값을 바꾸는 Service 메서드는 모두 `@Transactional`, 조의 기록을 읽는 Service 메서드는 모두 `TeamAccessChecker`를 부름, 클라이언트가 보낸 시각을 저장하는 곳 없음, 계산 가능한 값을 state로 둔 곳 없음, demo-scope 8절 기능 없음.
- 고친 것(수정 필요):
  - `GlobalExceptionHandler`의 handler 이름 4개가 "handle + 예외 이름에서 Exception 뺀 것" 규칙과 달랐다. `handleNotValid` → `handleMethodArgumentNotValid`, `handleTypeMismatch` → `handleMethodArgumentTypeMismatch`, `handleMissingPart` → `handleMissingServletRequestPart`, `handleNotReadable` → `handleHttpMessageNotReadable`.
  - 없는 주소와 없는 method에서 실제 백엔드는 Spring 기본 모양 `{ timestamp, status, error, path }`(404, 405)이고 가짜 API는 `{ code, message }` 404였다. 실제 백엔드에 `handleNoResourceFound`, `handleHttpRequestMethodNotSupported`를 넣어 둘 다 404 `NOT_FOUND` "없는 주소입니다"로 맞췄다.
  - 가짜 API가 빈 날짜 `""`(화면에서 날짜를 비우고 제출하면 생김)에 "요청 body의 형식이 올바르지 않습니다"를 줬다. 실제는 `""`를 null로 읽어 "입력값이 올바르지 않습니다"다. 가짜 API를 실제에 맞췄다.
  - 가짜 API가 달력에 없는 날짜(`2026-02-30`)를 통과시켰다. `isValidDate`로 실제처럼 400 "요청 body의 형식이 올바르지 않습니다"로 막았다. `leaderEmails`가 목록이 아닐 때도 같은 메시지로 맞췄다.
  - `BackendApplication.main`에 주석이 없었다.
- 고친 것(선택 사항):
  - `TeamMember.roleAssignedAt`만 초 아래를 버리지 않았다. 다른 기록 시각(`uploadedAt`, `submittedAt`)처럼 `truncatedTo(SECONDS)`.
  - `spring.jpa.open-in-view: true`를 명시하고 이유를 적었다. Controller의 `XxxResponse.from(entity)`가 LAZY 연관을 읽으려면 필요하다. 시작할 때 나던 WARN이 사라졌다.
- 결정:
  - 미정 6 중 "없는 주소": 404 `NOT_FOUND` "없는 주소입니다". method가 없는 요청도 405가 아니라 404로 했다. 선택지는 (A) 404 NOT_FOUND, (B) 405 + 새 code였다. code 목록을 늘리지 않는 A로 했다. 가짜 API와 같은 응답이다.
  - 미정 6 중 "500 응답 형식"과 "400에 틀린 필드 담기"는 남겼다. 500을 `{ code, message }`로 바꾸려면 새 code(예: `SERVER_ERROR`)가 필요한데, CLAUDE.md 코드 규칙이 "새 code가 필요하면 먼저 묻는다"라서 관리자 결정으로 남긴다. 화면은 code 없는 실패를 이미 "요청을 처리하지 못했습니다"로 보여 준다.
- 고치지 않은 것(선택 사항):
  - 가짜 API에 JSON이 아닌 body를 보내면 json-server의 body-parser가 HTML 400을 준다(실제는 `{ code, message }` 400). 화면은 항상 JSON을 보내서 생기지 않는다. 막으려면 error 처리 middleware를 따로 둬야 해서 두지 않았다.
  - 숫자 칸에 글자 `"2"`를 보내면 실제(Jackson)는 숫자로 바꿔 받고 가짜는 400 "입력값이 올바르지 않습니다"다. 화면과 Postman 모음은 숫자로 보내서 생기지 않는다.
- 고친 파일: `exception/GlobalExceptionHandler.java`, `BackendApplication.java`, `entity/TeamMember.java`, `application.yml`, `frontend/mock/middleware.cjs`, `docs/api-draft.md`(공통 규칙에 없는 주소·500 상태)
- 확인: `gradlew build`, `npm run build`, `npm run lint` 통과. 컨테이너 백엔드를 새로 만들어 A-1~E-2 시험 요청을 다시 보냈고 고치기 전 결과와 모두 같았다. Postman 모음 52개 test가 실제·가짜 모두 통과. 경계값 10개 비교에서 남은 차이는 위 "고치지 않은 것" 2건뿐이다.
- 읽을 때 볼 곳:
  - `GlobalExceptionHandler`의 새 handler 2개: Controller에 닿지 못한 요청(없는 주소)도 `@RestControllerAdvice`가 받는다. 단 token이 없으면 Security가 먼저 401로 막는다.
  - `isValidDate`: `new Date`는 13월이면 Invalid Date, 2월 30일이면 3월 2일로 넘어간다. 두 경우를 다 막는다.
