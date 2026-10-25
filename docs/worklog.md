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
