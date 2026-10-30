# 작업 기록

Claude Code가 단위 업무 하나를 끝낼 때마다 오늘 날짜 아래에 적는다. /read-check 결과도 여기에 적는다.

<!--
## 2026.10.25

### <단위 업무 ID> <이름>
- 결정: (선택지·이유. 관리자 결정이면 날짜)
- 만든 파일:
- 고친 파일:
- 확인: (빌드·lint·실제로 눌러 본 것)
- 읽을 때 볼 곳:
- 내가 채운 가정:
해당 없는 칸은 지우지 말고 "없음"으로 적는다.

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
  - token 저장 위치는 sessionStorage(demo-scope 9절 미정 4). 탭마다 다른 계정으로 로그인해 데모할 수 있어서다. 0-4b에서 적용한다.
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
  - `findAllForMember`의 `.map(teamMember -> teamMember.getTeam().getAssignment())`: LAZY 연관을 꺼내는 줄이다. 꺼낼 때마다 쿼리가 나간다(데모 데이터가 작아서 그대로 둔다).
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
  - 시작 학생 10명은 모두 시작 과제의 조에 있으므로, 합류 데모는 새 과제를 만들어서 한다(README에 적음).

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
  - 같은 사람이 동시에 두 번 제출하면(두 요청이 거의 같은 순간) 두 번째는 unique 제약에 걸려 500이 된다. 데이터는 지켜지고, 화면은 제출 중 버튼을 막아서 데모에서는 생기지 않는다. 409로 바꾸는 처리는 하지 않았다.
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
  - 백엔드·화면 포트는 `127.0.0.1`에만 열고, 화면 이미지의 API 주소도 `http://127.0.0.1:8080`으로 했다. 아래 "문제" 참고. 화면은 그대로 `http://localhost:5173`으로 연다(CORS 허용 주소). 같은 이유로 LAN의 다른 컴퓨터에서는 접속할 수 없다(데모는 한 컴퓨터).
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
  - JWT 서명 키는 컨테이너에서도 `application.yml`의 개발용 기본값을 쓴다(데모용).
  - Postman 모음은 다음 단위로 남겼다.

### Postman 모음 (2단계)
- 결정:
  - 위치는 `docs/postman/`(모음 JSON, 업로드 시험 파일, README). 새 최상위 폴더를 만들지 않고 문서 폴더에 둔다.
  - 환경(Environment) 파일 없이 **모음 변수**만 쓴다. 선택지는 (A) 모음 변수, (B) 환경 파일 따로(실제·가짜 2개)였다. Import 한 번으로 끝나는 A로 했다. 가짜 API는 `baseUrl` 하나만 바꾸면 된다.
  - 순서는 데모 흐름 그대로 폴더 0~5. 요청마다 test script가 상태 코드를 확인하고 다음 요청에 쓸 값(token, 과제·조·조 참여 번호, 초대 코드, 산출물 번호)을 저장한다. 그래서 Run collection 한 번이 1단계 흐름 전체의 회귀 시험이 된다.
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

## 2026.10.26

### 500 응답 형식 SERVER_ERROR (미정 6 중 500 부분, 관리자 결정)
- 결정:
  - 관리자 결정으로 새 code `SERVER_ERROR` 500을 추가했다. 응답은 `{ "code": "SERVER_ERROR", "message": "서버에서 오류가 났습니다" }`. 예외 내용은 응답에 담지 않고 서버 로그(`log.error`)에만 남긴다.
  - 백엔드는 `GlobalExceptionHandler` 맨 아래에 `@ExceptionHandler(Exception.class)`를 하나 둔다. 이름은 규칙(handle + 예외 이름에서 Exception 뺀 것)대로면 `handle`만 남아서 `handleException`으로 했다.
  - 모든 예외를 받으면 Spring이 4xx로 처리하던 예외(예: 지원하지 않는 Content-Type 415)까지 500이 된다. 그래서 `ErrorResponse` 중 4xx는 400 `INVALID_INPUT` "요청의 형식이 올바르지 않습니다"로 돌려준다. 선택지는 (A) 4xx는 400 INVALID_INPUT, (B) 원래 상태(415 등)를 그대로 두고 code만 INVALID_INPUT, (C) `ResponseEntityExceptionHandler` 상속이었다. "INVALID_INPUT은 400"을 지키고 상속을 쓰지 않는 A로 했다.
  - 가짜 API는 `module.exports`를 `[handleRequest, handleError]` 목록으로 바꿨다. json-server가 body-parser 뒤에 차례로 등록하므로, 인자 4개짜리 `handleError`가 처리 중 오류와 body-parser 오류를 받는다. 업로드의 `.then` 안 오류는 `.catch(next)`로 넘긴다.
  - 같은 handler로 지난 rule-check에서 남긴 "JSON이 아닌 body에 가짜 API가 HTML 400을 줌"도 고쳤다(`err.type === 'entity.parse.failed'` → 400 "요청 body의 형식이 올바르지 않습니다").
- 고친 파일: `exception/GlobalExceptionHandler.java`, `frontend/mock/middleware.cjs`, `frontend/src/api/client.js`(주석), `docs/api-draft.md`(공통 규칙), `CLAUDE.md`(code 목록), `frontend/README.md`
- 확인:
  - 일부러 500 만들기: 실제는 MinIO 컨테이너를 멈추고 다운로드 → 500 `SERVER_ERROR`, 서버 로그에 "처리하지 못한 예외입니다"와 원인. 가짜는 시작 파일 자리를 폴더로 바꾸고 다운로드 → 같은 500 body. 시험 뒤 MinIO와 파일을 되돌렸다.
  - JSON이 아닌 body: 실제·가짜 모두 400 "요청 body의 형식이 올바르지 않습니다".
  - `Content-Type: text/plain`: 실제 400 "요청의 형식이 올바르지 않습니다"(전에는 Spring 기본 415), 가짜 400 "입력값이 올바르지 않습니다". 상태와 code는 같고 문구만 다르다(가짜는 body를 읽지 못해 빈 body로 검사).
  - `gradlew build`, `npm run build`, `npm run lint` 통과. A-1~E-2 시험 요청 결과가 고치기 전과 같았다. Postman 52개 test가 실제·가짜 모두 통과.
- 읽을 때 볼 곳:
  - `handleException`이 맨 아래 있어도 순서 때문에 먼저 잡히지 않는다. Spring은 예외 종류가 가장 가까운 handler를 고른다(`NotFoundException`은 `handleNotFound`).
  - `e instanceof ErrorResponse errorResponse`: 검사와 변수 선언을 한 번에 하는 Java 문법(pattern matching)이다.
  - 가짜 API의 `handleError(err, req, res, next)`: `next`를 쓰지 않아도 인자 4개를 맞춰야 Express가 오류 처리용으로 알아본다.
- 내가 채운 가정:
  - 문구 "서버에서 오류가 났습니다", "요청의 형식이 올바르지 않습니다".
  - 미정 6의 남은 부분(400에 틀린 필드 담기)은 이번에도 하지 않았다. 지금은 "입력값이 올바르지 않습니다" 한 문구다.

### 데모 리허설과 데모 대본
- 결정:
  - 새로 clone 한 폴더(`git clone` 직후, `infra/.env` 없음)에서 `docker compose up -d --build`로 켜고, 브라우저 탭 4개(교수, 조장 student01, 조원 student02·student03)로 데모 흐름 전체를 처음부터 끝까지 했다. 교수가 새 과제(조 2개)를 만들고 → 조장이 초대 링크 발급 → 두 학생이 로그인 전 링크를 열어 로그인 뒤 합류 → 조장이 역할 3개 지정 → 두 조원이 산출물 3건 업로드(같은 역할 2건으로 순번 1·2 확인) → 교수가 목록을 보고 내려받기(한글 이름, 본문 일치) → 조원이 2명을 평가해 제출. 데이터베이스에 평가 2행과 unique 제약을 확인했다. 백엔드 수정 없이 모두 통과했다.
  - 가장자리도 확인했다: 다 입력하지 않고 "입력 내용 확인" → 빠진 조원 이름이 나옴, "고치기"로 돌아가도 입력 유지, 제출 뒤 새로 고침해도 다시 입력 불가, 다른 조 조장(student06)이 1조 산출물 주소를 직접 입력 → "이 조에 접근할 권한이 없습니다", 로그아웃 → 로그인 화면.
  - 걸린 것 1건: 교수의 조 화면에 "산출물 보기·올리기"가 보였다(교수는 올릴 수 없음). 조원이 아니면 "산출물 보기"로 보이게 고쳤다. 선택지는 (A) 조원 여부로 문구만 바꾸기, (B) 모두에게 "산출물"로 통일이었다. 학생에게는 올리는 곳이라는 안내가 남는 A로 했다.
  - 고치지 않은 것: 초대 링크 복사 버튼이 없다. 대본에서 "주소를 세 번 클릭해 선택 → Ctrl+C"로 안내했다. 합류 화면은 어느 과제·조에 합류하는지 보여 주지 않는다(초대 코드로 조를 미리 보는 API가 없음). 합류 뒤 조 화면에서 보인다. 둘 다 데모는 막지 않아서 API·코드를 늘리지 않았다.
  - 대본은 `docs/demo-script.md`. 준비(전날·5분 전), 계정 표, 장면 7개(+선택 1개)마다 누를 것·화면에 나오는 것·말할 것, 막혔을 때, 예상 질문.
- 만든·고친 파일: `docs/demo-script.md`(새로), `frontend/src/pages/TeamPage.jsx`, `README.md`(문서 목록)
- 확인: `gradlew build`, `npm run build`, `npm run lint` 통과. 저장소 폴더에서 다시 `docker compose up -d --build` 한 뒤 교수는 "산출물 보기", 조원은 "산출물 보기·올리기"·"동료평가"가 보이는 것을 브라우저로 확인했다.
- 읽을 때 볼 곳:
  - 로그인 정보가 sessionStorage에 있어서 탭마다 다른 사람으로 로그인할 수 있다. 대신 링크를 "새 탭에서 열기"로 열면 원래 탭의 로그인이 복사될 수 있어서, 대본은 새 탭에 주소를 직접 입력하라고 적었다.
  - 조장 화면은 다른 탭에서 조원이 합류해도 저절로 바뀌지 않는다. 대본의 장면 4가 F5로 시작하는 이유다.
- 내가 채운 가정:
  - 데모 데이터: 과제명 "웹프로그래밍 조별과제", 기간 2026-11-16 ~ 2026-12-11, 1조 조장 student01·2조 조장 student06, 역할 기획·백엔드·프론트엔드, 올릴 파일 이름 3개.
  - 장면별 "말할 것" 문장과 예상 질문의 답. 발표자가 바꿔 써도 된다.
  - 실시간 진행이 꼬였을 때 시작 데이터의 "발자국 데모 과제"로 넘어가는 대안.

### 화면 디자인 (1단계 "밀리면 뒤로 보내는 것"이던 항목)
- 결정:
  - 새 라이브러리 없이 `src/index.css` 한 파일로 했다. 선택지는 (A) CSS 한 파일, (B) Tailwind, (C) MUI 같은 컴포넌트 라이브러리, (D) 화면마다 CSS Module이었다. B·C는 조원 6명이 라이브러리 문법까지 배워야 하고, D는 파일이 화면 수만큼 늘어난다. 읽을 곳이 한 곳인 A로 했다. 새 의존성은 없다.
  - Vite 템플릿의 CSS(보라색, 56px 제목, 가운데 정렬, 어두운 모드)를 모두 걷어 냈다. 색과 둥근 정도는 `:root` 변수로 모았다.
  - 규칙은 먼저 태그(`button`, `input`, `table`, `fieldset`)에 걸고, 태그로 구분할 수 없는 곳에만 class를 붙였다. class는 17개: `secondary`, `muted`, `error`, `success`, `notice`, `panel`, `item-list`, `end`, `badge`, `links`, `row`, `login-form`, `field`, `invite-link`, `criteria`, `score-option`, `table-scroll`(설명은 CSS 주석).
  - 밝은 화면 하나만 둔다(`color-scheme: light`). 데모는 프로젝터로 하고, 어두운 모드까지 두면 색을 두 벌 관리해야 한다.
  - 한국어 줄 바꿈은 `word-break: keep-all`. 없으면 좁은 화면 표에서 "이서연"이 "이서/연"으로 끊겼다.
  - 동료평가 6개 항목은 720px 이상에서 2칸 grid로 놓아 화면 길이를 반으로 줄였다. 고른 점수 줄은 `:has(input:checked)`로 색을 칠했다. 확인 표는 좁은 화면에서 표만 가로로 밀리게 `table-scroll`로 감쌌다.
  - 오류 메시지는 모두 `<p className="error">`, 성공은 `success`, 고정 문구와 "고칠 수 없습니다" 안내는 `notice`(노란 상자)로 통일했다.
  - 화면 위 이동 링크는 "← 상위 화면" 한 줄로 맨 위에 두고, 맨 아래의 "과제 목록으로", "조 화면으로", "돌아가기"는 뺐다. 불러오기 실패 화면의 "과제 목록으로"는 남겼다.
  - inline style 3곳(`style={{ display: ... }}`)을 class로 바꿨다. `EvaluationReview`의 `border={1} cellPadding={4}`도 CSS로 옮겼다.
  - 화면 문구가 바뀐 곳에 맞게 `docs/demo-script.md`를 고쳤다(예: "김민준 (조장) · 기획" → "김민준 조장" + "기획" 꼬리표).
- 고친 파일: `src/index.css`, pages 8개 전부, components 10개(`AssignmentList`, `TeamSummaryList`, `TeamMemberList`, `RoleAssignForm`, `InviteLinkPanel`, `DeliverableUploadForm`, `DeliverableList`, `EvaluationNotice`, `EvaluationTargetForm`, `EvaluationReview`), `docs/demo-script.md`. API, 가짜 API, 동작은 바꾸지 않았다.
- 확인:
  - `npm run build`, `npm run lint` 통과. 백엔드는 이번에 고치지 않았다.
  - 개발 서버로 화면 8개를 브라우저에서 열어 봤다: 로그인(틀린 비밀번호 오류 포함), 과제 목록, 과제 만들기(조 2개), 과제 상세, 조 화면(조장: 역할명 칸·초대 링크 발급 전/후, 교수), 합류(없는 코드 오류), 산출물(올리기 상자·목록), 동료평가(입력·확인 표·제출 완료).
  - 휴대폰 폭(375px)에서 같은 화면을 열어 가로로 넘치는 화면이 없는 것을 확인했다(`scrollWidth`와 `clientWidth`가 같음).
  - `docker compose up -d --build frontend` 후 Docker 화면에도 반영된 것을 확인했다.
- 읽을 때 볼 곳:
  - `index.css` 맨 위 주석: 변수 → 태그 → class 순서. 새 화면은 태그만 써도 같은 모양이 되고, 필요할 때만 아래 class를 골라 붙인다.
  - React에서는 `class`가 아니라 `className`이라고 쓴다(`class`는 JavaScript 예약어).
  - `.item-list li`의 `display: flex`와 `.end`의 `margin-left: auto`: 다운로드 버튼과 역할명 칸을 줄 오른쪽 끝으로 미는 방법이다.
  - `InviteLinkPanel`의 `className={inviteLink ? 'secondary' : undefined}`: 조건에 따라 class를 붙이거나 안 붙이는 방법이다. `undefined`이면 React가 class 속성을 아예 쓰지 않는다.
  - 개발 서버(Vite)는 저장할 때마다 화면을 바꾸지만(HMR), 저장 도중 JSX 문법 오류가 잠깐 나면 CSS가 옛 내용으로 남을 수 있다. 화면이 이상하면 F5로 새로 고친다.
- 내가 채운 가정:
  - 색(파랑 `#2453c7` 강조, 노랑 안내, 빨강 오류, 초록 완료), 글꼴(맑은 고딕 우선), 본문 16px, 기둥 폭 880px.
  - 로그인 화면의 부제 "조별과제 과정기록 & 평가지원 서비스", 과제 만들기 링크의 "+", 산출물 목록 제목 "올린 산출물 (최신순)", 올리기 상자 제목 "산출물 올리기".
  - 조장 표시를 "(조장)"에서 회색 "조장"으로, 역할명을 꼬리표로 바꾼 것.

### GitHub Primer 디자인·라이트/다크 (화면 디자인 다시 하기)
- 결정:
  - 관리자 결정(2026.10.26): 바로 위 "화면 디자인"의 색과 모양을 GitHub 디자인(Primer)으로 바꾼다. 강조색은 GitHub 그대로(파랑 링크, 초록 주요 버튼), 테마는 라이트·다크 둘 다, 처음에는 운영체제 설정을 따르고 위쪽 막대의 버튼으로 바꾼다. 박스로 나누는 배치는 그대로 둔다. 위 항목의 "밝은 화면 하나만 둔다"는 이 결정으로 바뀐다.
  - 색은 GitHub Primer의 값(`#0969da` 링크, `#1f883d` 주요 버튼, `#d1d9e0` 테두리, 다크는 `#0d1117` 바탕 등)을 `index.css` 맨 위 변수로 옮겼다. 라이트 변수는 `:root`, 다크 변수는 `:root[data-theme='dark']`와 `@media (prefers-color-scheme: dark)` 두 곳에 같은 값으로 둔다. CSS에는 "운영체제가 다크이고, 사용자가 라이트를 고르지 않았을 때"를 한 규칙으로 쓰는 방법이 없어서다.
  - 테마 기억은 `localStorage`. 로그인 token(sessionStorage, 탭마다 따로)과 달리 테마는 모든 탭이 같아야 해서다. 함수는 `src/theme.js` 한 곳에 모았다.
  - 버튼: 태그 `button`이 초록 주요 버튼, `.secondary`가 GitHub 회색 버튼. 파일 칸의 "파일 선택" 버튼도 `::file-selector-button`으로 회색 버튼과 같게 그렸다. 목록(`.item-list`)은 테두리 상자 하나에 줄을 선으로 나누고, 상자(`.panel`)의 첫 `h2`는 회색 머리 줄로 그린다. 역할 꼬리표(`.badge`)는 GitHub Label 모양.
  - 위쪽 막대(`AppLayout`)를 새로 만들었다. 로그인한 뒤의 모든 화면에 로고, 이름(회원 유형), 테마 버튼, 로그아웃을 둔다. React Router의 바깥 Route + `<Outlet />`으로 감쌌다. 그래서 과제 목록 화면의 "환영합니다"와 로그아웃 버튼은 빼고, 제목을 "과제 목록"으로 바꿨다.
  - 로그인 화면은 GitHub 로그인처럼 340px 칸을 가운데에 뒀다.
  - 새 의존성 `pretendard@1.3.9`(npm, 한국어 글꼴 Pretendard). 이유: GitHub 글꼴은 영문용이라 한글이 운영체제 기본 글꼴(맑은 고딕)로 나와 모양이 달라진다. 화면에 실제로 쓰인 글자만 내려받는 `dynamic-subset` CSS를 `main.jsx`에서 import 한다. 대안: (A) 시스템 글꼴 — 의존성은 없지만 Windows·macOS에서 모양이 다르다. (B) CDN `<link>` — 발표장 인터넷이 끊기면 글꼴이 빠진다. npm으로 넣으면 build 결과에 글꼴 파일이 들어가 인터넷 없이 된다.
  - 처음 넣었던 탭 강조색 변수(`--tab-selected`)는 쓰는 곳이 없어 지웠다.
- 만든·고친 파일:
  - 새로: `src/theme.js`, `src/components/ThemeToggle.jsx`, `src/pages/AppLayout.jsx`
  - 고침: `src/index.css`(다시 씀), `src/main.jsx`(글꼴 import, 첫 테마 적용), `src/App.jsx`(바깥 Route), `src/pages/HomePage.jsx`, `src/pages/LoginPage.jsx`, 불러오는 중 화면을 `<main>`으로 감싼 4개(`AssignmentDetailPage`, `TeamPage`, `DeliverablesPage`, `EvaluationPage`), `src/components/RoleAssignForm.jsx`(지정 버튼을 회색으로), `package.json`, `package-lock.json`, `docs/demo-script.md`(위쪽 막대·테마 안내, "+ 과제 만들기" 위치)
  - API, 가짜 API, 백엔드는 바꾸지 않았다.
- 확인:
  - `npm run build`, `npm run lint` 통과.
  - 개발 서버로 라이트·다크 두 테마에서 열어 봤다: 로그인, 과제 목록, 과제 만들기, 과제 상세, 조 화면(조장·조원), 합류(없는 코드), 산출물(올리기 상자·목록·다운로드 버튼), 동료평가(입력·확인 표). 테마 버튼을 누르고 새로 고쳐도 고른 테마가 남는 것, Pretendard가 실제로 쓰이는 것(`document.fonts.check`)을 확인했다.
  - 휴대폰 폭(375px)에서 동료평가 입력·확인 표가 가로로 넘치지 않는다(`scrollWidth`와 `clientWidth`가 같음).
- 읽을 때 볼 곳:
  - `index.css` 맨 위 변수 세 벌: 같은 이름(`--bg`, `--text` …)에 값만 바꿔 넣는 것이 테마의 전부다. 아래 규칙은 색 값을 직접 쓰지 않고 `var(--bg)`처럼 변수만 쓴다. 새 규칙도 그렇게 써야 다크에서 깨지지 않는다.
  - `App.jsx`의 Route 안의 Route: 바깥 `AppLayout`이 위쪽 막대를 그리고, 안쪽 화면은 `<Outlet />` 자리에 들어간다. 화면을 옮겨도 위쪽 막대는 다시 불러오지 않는다.
  - `main.jsx`의 `applySavedTheme()`이 `createRoot`보다 먼저인 이유: React가 그리기 전에 테마를 붙여야 처음에 라이트로 번쩍 보였다가 다크로 바뀌지 않는다.
  - `ThemeToggle`이 component인데 state를 갖는 이유: 서버 데이터가 아니라 버튼 하나의 화면 상태라서다. "데이터를 불러오는 것은 page" 규칙과 어긋나지 않는다.
- 내가 채운 가정:
  - 테마 버튼 글자는 "누르면 바뀔 테마"("라이트"/"다크"). 아이콘은 쓰지 않았다(이모티콘·아이콘 라이브러리를 늘리지 않으려고).
  - 글자 14px(GitHub 본문 크기), 기둥 폭 960px, 로그인 칸 340px, 로그인 화면에는 테마 버튼을 두지 않음(운영체제 설정을 따름).
  - 대본에 "프로젝터에서는 라이트가 잘 보입니다"라고 적은 것.

### 글자 크기·간격 다듬기
- 결정:
  - 글자 크기를 `index.css` 맨 위 변수 다섯 단계로 정했다: 화면 제목 26px(`--font-page`), 구역 제목 17px(`--font-section`), 본문 15px(`--font-body`), 버튼·입력 칸·보조 정보 14px(`--font-control`), 꼬리표 12px(`--font-tag`). 규칙에 px를 직접 쓰지 않고 이 변수만 쓴다.
  - 본문은 GitHub(14px)보다 한 단계 키운 15px, 줄 간격 1.6. 한국어는 받침 때문에 같은 크기에서 영어보다 획이 뭉치고, 데모는 프로젝터로 한다.
  - 버튼과 입력 칸은 14px로 둬서 높이가 32px로 같다. 역할명 칸과 "지정" 버튼처럼 한 줄에 놓아도 가지런하다.
  - Pretendard는 글자 사이를 조금 좁혔다(본문 -0.01em, 제목 -0.02 ~ -0.025em). 꼬리표와 `code`는 0.
  - 굵기: 화면 제목 700, 그 밖의 제목·`strong`·목록 제목 링크 600. 기본 `strong`(700)은 본문 사이에서 너무 진했다.
  - 숫자 폭 고정(`tabular-nums`): 평가 확인 표와 보조 정보(날짜, 크기). 점수·날짜가 위아래로 가지런해진다.
  - 간격: 화면 위 여백 32px, 화면 제목 아래 16px, 구역 제목 위 32px·아래 12px. 점수 줄은 여백을 3px 8px로 늘리고 radio를 글자 첫 줄 가운데에 맞췄다.
  - 고정 문구 상자(`ul.notice`)의 16px 크기 지정은 뺐다. 본문 크기에 굵게(`strong`)만으로 충분히 보인다.
- 고친 파일: `frontend/src/index.css`
- 확인: `npm run build`, `npm run lint` 통과. 개발 서버로 과제 목록, 조 화면, 산출물, 동료평가(입력·확인 표), 로그인을 라이트·다크와 휴대폰 폭(375px)에서 열어 가로로 넘치지 않는 것을 확인했다.
- 읽을 때 볼 곳: `:root`의 `--font-*` 다섯 줄. 글자 크기를 바꾸고 싶으면 규칙이 아니라 이 변수를 고친다.
- 내가 채운 가정: 다섯 단계의 크기 값(26/17/15/14/12px)과 글자 사이 간격 값.

### 시니어 리뷰와 리팩터링 (리뷰어 6관점 + 반박 검증)
- 결정:
  - 관리자 지시(2026.10.26): "시니어 개발자 평가자를 대거 투입하고 리팩터링". 리뷰어 6명(백엔드 보안·정합성, 백엔드 일관성, React, API 계약, 업무 규칙, 실행·문서)이 코드를 읽기만 하며 지적하고, 관점마다 반박 담당 1명이 지적을 하나씩 다시 열어 반박했다. 66건 중 20건이 반박돼 빠졌고 46건을 원인별로 묶어 고쳤다.
  - 동료평가 동시 제출: 두 요청이 "이미 제출했는지" 검사를 같이 통과하면 두 번째가 unique 제약에 걸려 500이 났다. `saveAllAndFlush`로 INSERT를 바로 보내고 `DataIntegrityViolationException`을 잡아 409 `DUPLICATE`로 바꿨다. 위 E-2 항목의 가정("동시 제출은 500, 409 처리 안 함")을 demo-scope 4절(409)에 맞춰 바꾼 것이다. 선택지는 (A) Service 한 곳에서 catch, (B) `GlobalExceptionHandler`에서 `DataIntegrityViolationException` 전체를 409로 바꾸기였다. B는 NOT NULL·길이 위반 같은 진짜 버그까지 409로 가려서 A로 했다.
  - 동료평가 목록 안의 `null` 원소(`{"evaluations":[null]}`)가 검사를 통과해 500이 났다. `List<@NotNull @Valid Item>`으로 원소마다 검사한다. 가짜 API는 이미 400이었다.
  - 같은 조 동시 합류(같은 순간 두 번)도 500이 날 수 있지만 고치지 않고 api-draft에 적었다. 화면이 합류 중 버튼을 막는다.
  - 조장 화면에서 두 조원의 역할 지정을 거의 동시에 누르면, 나중에 끝난 쪽이 옛 `members`로 목록을 덮어써 앞 결과가 화면에서 사라졌다. 지정 중에는 모든 줄의 버튼을 막는다(`assigningId !== null`). 함수형 업데이트(`setMembers(prev => ...)`)는 새 패턴이라 쓰지 않았다.
  - `ThemeToggle`의 state를 `AppLayout`(page)으로 옮겼다. 위 "GitHub Primer" 항목에 적은 예외(component가 state를 가짐)를 없애고 CLAUDE.md "state는 page" 규칙에 맞췄다.
  - 초대 코드 Service가 String 대신 `Team`을 돌려주고 Controller가 `InviteCodeResponse.from(team)`을 부른다. 다른 API와 같은 "Service는 Entity, 변환은 Controller" 모양이다. JSON은 그대로다.
  - 로그인 회원 번호는 Service 메서드의 **마지막 인자**로 통일했다(`AssignmentService.create`만 맨 앞이었다).
  - `DeliverableService`에도 `private findTeam`을 두어 조를 찾는 두 곳의 복사를 없앴다. 공통 부모 클래스는 만들지 않았다.
  - 리뷰가 낸 "업로드의 빈 파일 검사를 권한 검사 뒤로" 제안은 따르지 않았다. 파일이 없거나 20MB를 넘는 요청은 Spring이 Service 전에 400으로 막고 가짜 API도 파일 검사를 먼저 해서, 지금 순서가 더 일관된다. 이유를 주석으로 남겼다.
  - 테스트 `BackendApplicationTests`도 `footprint-test` 버킷을 쓴다. 전에는 시작 데이터 파일이 데모용 `footprint` 버킷에 빌드마다 쌓였다. 빌드가 개발 DB를 지우고 시작 데이터를 다시 넣는 것은 그대로라 README와 대본에 "데모·리허설 중에는 빌드하지 않는다"를 적었다.
  - `infra/docker-compose.yml`의 PostgreSQL·MinIO 포트를 `127.0.0.1`에만 연다. 기본 비밀번호가 저장소에 있어서 같은 와이파이의 다른 컴퓨터에 열지 않는다. compose 안의 백엔드는 service 이름으로 접속해서 영향이 없다.
  - `frontend/.env`의 `VITE_API_URL`을 `http://127.0.0.1:8080`으로 바꿨다. Docker 화면·Postman·대본이 이미 이 주소였고, `localhost:8080`(IPv6 `::1`)이 멈춘 일이 있었다(위 "한 번에 실행" 항목).
  - 가짜 API의 접근 검사 함수를 실제 `TeamAccessChecker`와 같은 이름·같은 반환으로 맞췄다: `checkAssignmentMember` 추가, `checkTeamMember`·`checkLeader`는 실패하면 `undefined`, 성공하면 조 참여 기록.
  - 리뷰가 "관리자 결정이 필요"로 올린 3건은 손대지 않았다(아래 "관리자에게 물을 것").
- 만든·고친 파일:
  - 백엔드: `dto/EvaluationSubmitRequest`, `dto/InviteCodeResponse`, `service/EvaluationService`, `service/TeamService`, `service/AssignmentService`, `service/DeliverableService`, `controller/TeamController`, `controller/AssignmentController`, `security/JwtProvider`(claim 주석), `exception/GlobalExceptionHandler`(주석), `config/WebConfig`(주석), 테스트 `BackendApplicationTests`
  - 화면: `pages/AppLayout`, `components/ThemeToggle`, `components/TeamMemberList`, `pages/TeamPage`, `pages/EvaluationPage`, `pages/DeliverablesPage`(setter 축약형을 화살표 함수로), `pages/AssignmentCreatePage`, `pages/JoinPage`(catch에서만 버튼을 푸는 이유 주석), `components/AssignmentList`("볼 수 있는 과제가 없습니다" — 교수 화면에도 맞는 문구)
  - 가짜 API: `mock/middleware.cjs`
  - 설정·문서: `infra/docker-compose.yml`, `frontend/.env`, `README.md`(Compose v2.24 이상), `backend/README.md`(bootRun 전 `cd ..\backend`, curl 다운로드 이름, 빌드가 DB를 지운다는 경고), `docs/api-draft.md`(동시 요청, invite-code 400 줄), `docs/demo-script.md`(준비 확인 주소 `/members/me`, 빌드 금지), `docs/postman/README.md`(API 16개·요청 29개)
- 확인:
  - `gradlew build`(테스트 포함), `npm run build`, `npm run lint` 통과. JAVA_HOME이 없는 경로(`jdk-21.x.x.x-hotspot`)로 잡혀 있어서 이번에는 명령에만 `jdk-21.0.12.101-hotspot`을 넣어 빌드했다.
  - 실제 백엔드: `{"evaluations":[null]}` → 400 `INVALID_INPUT`. 같은 평가를 동시에 두 번 → 200 하나와 409 하나(로그에 unique 제약 위반이 찍혀 catch 경로가 실행된 것을 확인). 초대 코드 GET·POST 응답 모양은 그대로 `{ "inviteCode": ... }`.
  - 화면: 역할 없는 조원 3명인 조에서 한 줄의 "지정"을 누르는 동안 세 버튼이 모두 막히고, 차례로 지정한 역할 3개가 모두 남는다. 테마 버튼은 누를 때마다 `data-theme`과 글자가 바뀐다.
  - 가짜 API(복사한 db로 실행): 교수는 조 2개, 학생은 자기 조만, 어느 조에도 없는 학생은 과제 상세 403, 조원이 초대 코드를 보면 403, 다른 조 조원 목록 403.
- 읽을 때 볼 곳:
  - `EvaluationService.submit` 끝의 try/catch: 앞의 "이미 제출했는지" 검사만으로는 거의 동시에 온 두 요청을 못 막는다. 마지막 방어선은 DB의 unique 제약이고, 그 오류를 409로 바꾸는 곳이다. 로그에 Hibernate의 ERROR 한 줄이 찍히는 것은 정상이다.
  - `List<@NotNull @Valid Item>`: 꺾쇠 안의 어노테이션은 목록 자체가 아니라 원소 하나하나에 붙는다.
  - `TeamPage`의 `assigningId`: "무엇을 지정 중인가"(번호)를 기억하지만 버튼 잠금은 "지정 중인가"(`!== null`)로만 본다.
  - `AppLayout`이 `ThemeToggle`에 `theme`과 `onToggle`을 넘기는 모양: 다른 component(`RoleAssignForm`의 `value`/`onSubmit`)와 같은 "값과 할 일을 props로" 패턴이다.
- 관리자에게 물을 것(문서 충돌, 손대지 않음):
  - handler 이름: 학습 저장소 `decisions.md`의 규칙("handle + 예외 이름에서 Exception 뺀 것")과 그 예시(`handleNotValid`)가 서로 다르다. 데모는 규칙 문자 그대로(`handleMethodArgumentNotValid`)다. 위 2026.10.25 항목의 `handleNotValid`는 바꾸기 전 이름이다.
  - `docs/api-draft.md` 3행·CLAUDE.md 앞부분의 "관리자 확인 전에는 Controller를 만들지 않는다"와 2026.10.25 결정("api-draft에 적고 바로 만든다")이 어긋난다. `확인` 표시가 붙은 제목은 3개뿐이다.
  - JWT의 `memberType` claim: 넣지만 읽는 곳이 없다(권한 검사는 DB의 회원 유형). 위 2026.10.25 항목의 "token에는 회원 번호만"과도 다르다. 이번에는 "참고용" 주석만 달았다.
- 내가 채운 가정:
  - 리뷰 지적 중 취향 문제(들여쓰기·wildcard import 일괄 정리, 4개 화면의 effect cleanup, N+1, 로그인 시각 차이로 이메일 존재가 드러나는 것)는 고치지 않았다.
  - `saveAll` 대신 `saveAllAndFlush`: 지금 id 생성 방식(IDENTITY)이면 `saveAll`도 INSERT를 바로 보내지만, 그 사실에 기대지 않으려고 골랐다.
  - 시험하면서 개발 DB에 과제 "Refactor check"(1조에 김민준·이서연·박도윤)와 1조 평가를 만들었다. 서버를 다시 켜면 시작 데이터로 돌아간다.

### 문서 충돌 3건 정리 (관리자 결정)
- 결정(2026.10.26 관리자 결정, 위 "시니어 리뷰" 항목의 "관리자에게 물을 것" 3건):
  - handler 이름은 지금처럼 규칙을 문자 그대로 따른다(`handleMethodArgumentNotValid`). 이유: 학습 저장소 `decisions.md`에서 규칙("handle + 예외 이름에서 Exception 뺀 것")과 예시(`handleNotValid`)가 어긋나는데, 예시를 따르면 줄이는 방법을 사람마다 다르게 정하게 된다. 규칙 문자 그대로면 예외 이름만 보고 handler 이름이 하나로 정해져서 조원이 새 handler를 만들 때 고민할 것이 없다. 예외는 `handleException` 하나다(규칙대로면 `handle`만 남는다).
  - `docs/api-draft.md` 3행을 2026.10.25 결정에 맞게 고쳤다: "먼저 적고, 확인을 기다리지 않고 바로 만든다". 제목의 "확인 2026.10.25" 표시 3개는 지우지 않고, 이 결정 전에 확인을 받은 API라는 뜻으로 3행에 설명을 붙였다.
  - JWT의 `memberType` claim은 남긴다. 권한 검사는 지금처럼 DB의 회원 유형으로 하고, claim은 token을 열어 볼 때의 참고용이다(`JwtProvider` 주석 그대로). 위 2026.10.25 항목의 "token에는 회원 번호만"은 이 결정으로 바뀐 것으로 본다.
  - 프로젝트 `CLAUDE.md`의 "확인 전에는 그 API의 Controller를 만들지 않는다" 문장도 같은 결정에 맞게 "먼저 쓰고, 확인을 기다리지 않고 바로 만든다"로 고쳤다(관리자 추가 지시).
- 고친 파일: `docs/api-draft.md`(3행), `CLAUDE.md`(31행), `docs/worklog.md`
- 확인: 문서만 고쳐서 빌드는 다시 돌리지 않았다.

### 구글 로그인 spike (미정 1 확인)
- 결정:
  - 관리자 지시(2026.10.26): 미정 1(12.11 로그인 방식)을 정하기 전에, 학교 계정으로 구글 로그인이 되는지부터 확인한다. 걱정한 위험은 학교 Workspace가 외부 앱 로그인을 막아 두었을 가능성이었다.
  - 방식: 저장소 밖 임시 HTML 한 장(Google Identity Services 버튼)을 `http://localhost:5173`에 띄우고, 받은 ID token의 payload를 브라우저 안에서만 풀어 보았다. 서명 검증은 하지 않았다(실제 구현에서 서버가 한다). spike 파일은 저장소에 넣지 않았다.
  - Google Cloud 설정(관리자가 직접): 개인 계정의 새 프로젝트, 동의 화면 대상 "외부(External)", 게시 상태 "테스트 중"(테스트 사용자에 학교 계정 추가), 웹 애플리케이션 OAuth client, 승인된 JavaScript 원본 `http://localhost`, `http://localhost:5173`. 리디렉션 URI와 client secret은 쓰지 않는다.
- 결과: 관리자의 학교 계정으로 로그인 성공. `hd`=`g.yju.ac.kr`, `email_verified`=true, `iss`=`https://accounts.google.com`, `aud`가 Client ID와 일치. 학교 정책에 막히지 않는다.
- 아직 모르는 것:
  - 교수 계정의 이메일 도메인(`g.yju.ac.kr`이 아니면 `hd` 검사로 교수가 막힌다).
  - "테스트 중" 상태는 테스트 사용자로 넣은 계정만 로그인된다. 발표에 쓸 구글 계정 4개 이상과 조원 계정을 넣거나 "프로덕션"으로 게시해야 한다. 기본 범위(openid, email, profile)만 쓰면 심사가 필요 없는지는 확인하지 않았다.
  - 구글 token에는 교수·학생 구분이 없다. 회원 유형을 정하는 방법이 따로 필요하다.
- 고친 파일: `docs/worklog.md`
- 내가 채운 가정: spike 페이지에 `hd` 제한을 걸지 않았다(어느 도메인 계정이 들어오는지 보려고). Client ID는 비밀값이 아니지만 구현 단위에서 설정값으로 넣기 전까지 저장소에 적지 않는다.

### M-1-1·M-2-1 구글 로그인 (미정 1 확정)
- 결정:
  - 관리자 결정(2026.10.26): 12.11 기본 로그인은 구글(학교 계정). 교수는 설정값의 교수 이메일 목록으로 판별. 이메일·비밀번호 로그인은 개발·백업용으로 남긴다. demo-scope 9절 미정 1과 3절 M-1 줄에 적었다.
  - 방식: 화면의 구글 버튼(Google Identity Services)이 ID token을 받아 `POST /auth/google`로 보내고, 서버가 검증한 뒤 **지금과 같은 우리 JWT**를 준다. 로그인 뒤의 JWT filter, Service, 접근 검사는 그대로다. 선택지는 (A) 이 방식, (B) Spring Security `oauth2Login`(서버 redirect)이었다. B는 기본이 session이라 JWT와 섞으려면 성공 handler에서 token을 화면 주소로 넘겨야 하고 동작이 설정 뒤에 숨어서 A로 했다.
  - 새 의존성: `com.google.api-client:google-api-client:2.9.1`(2026.09 배포). `GoogleIdTokenVerifier`가 서명(구글 공개키), 만료, 발급자, `aud`를 확인한다. 대안은 `spring-boot-starter-oauth2-resource-server`의 `NimbusJwtDecoder`+구글 JWKS였다. 검증 규칙을 validator 설정으로 조립해야 해서 읽기 어려워 고르지 않았다.
  - 책임 나누기: `security/GoogleTokenVerifier`는 "구글이 확인한 사람인가"(서명, `email_verified`)만, `AuthService`는 우리 규칙(허용 도메인 `hd`, 회원 찾기·만들기, 교수 판별)을 맡는다.
  - 응답: 학교 도메인이 아니면 403 `FORBIDDEN`(구글이 확인한 사람이지만 권한이 없음), 그 밖의 검증 실패는 401로 묶는다, 구글 공개키를 못 받으면 500.
  - 회원: 이메일로 찾고 없으면 만든다(M-1-1 회원 등록을 대신함). `Member.password`는 nullable(구글 회원은 null). 회원 유형은 처음 만들 때만 정한다. 구글 `sub`는 저장하지 않는다.
  - 화면: 구글 script는 npm 패키지 없이 `src/google.js`가 한 번만 붙인다. 비밀번호 form은 `<details>`로 접었다(state 없음).
  - Client ID는 비밀값이 아니라서 `application.yml`, `frontend/.env`(`VITE_GOOGLE_CLIENT_ID`), `frontend/Dockerfile` 기본값에 넣었다. 교수 이메일은 실제 사람의 이메일이라 저장소에 넣지 않고 `PROFESSOR_EMAILS`로 받는다.
- 만든 파일: `security/GoogleTokenVerifier`, `dto/GoogleLoginRequest`, `frontend/src/google.js`, `frontend/src/components/GoogleLoginButton.jsx`
- 고친 파일: `build.gradle`, `application.yml`, `entity/Member`, `service/AuthService`, `controller/AuthController`, `mock/middleware.cjs`, `src/api/auth.js`, `src/pages/LoginPage.jsx`, `src/index.css`, `frontend/.env`, `frontend/Dockerfile`, `infra/.env.example`, `backend/README.md`("구글 로그인" 절), `docs/api-draft.md`, `docs/demo-scope.md`, `docs/demo-script.md`(시작 계정은 펼쳐서 입력)
- 확인:
  - `gradlew build`(테스트 포함), `npm run build`, `npm run lint` 통과.
  - 실제 백엔드: 빈 credential 400, 깨진 token 401, 서명을 꾸민 token 401(구글 공개키를 받아 비교), 시작 계정 비밀번호 로그인 200.
  - 관리자 Chrome에서 학교 계정으로 구글 로그인 성공: 과제 목록("볼 수 있는 과제가 없습니다")과 위쪽 막대에 이름·학생. DB에 `g.yju.ac.kr` 회원 1행이 비밀번호 null, STUDENT로 저장됨.
  - 가짜 API(db 복사본, `PROFESSOR_EMAILS` 지정): 학교 계정 → 학생으로 저장, 다시 로그인하면 같은 회원, 목록의 이메일 → PROFESSOR(대소문자 무시), 개인 Gmail 403, `email_verified` false 401, 구글 회원의 비밀번호 로그인 401.
  - 화면: StrictMode에서도 버튼 iframe과 script가 하나씩. 앱 안의 브라우저 창은 팝업을 막아서 구글 계정 선택 창이 열리지 않는다(일반 Chrome은 된다).
- 읽을 때 볼 곳:
  - `GoogleTokenVerifier`의 `parse`와 `isValid`를 나눈 이유: 둘 다 `IOException`을 던지는데, `parse`의 것은 token 모양이 깨진 것(401)이고 `isValid`의 것은 구글 공개키를 못 받은 것(500)이다.
  - `AuthService` 생성자의 `@Value(...) List<String>`: 쉼표로 구분한 값을 Spring이 목록으로 바꿔 준다. 빈 값이면 빈 목록이다.
  - `GoogleLoginButton`의 `useEffectEvent`: 구글 callback은 처음 한 번만 등록하지만 부를 때는 최신 props를 쓴다. `ignore`는 StrictMode에서 effect가 두 번 돌아도 버튼을 한 번만 그리게 한다.
  - `useRef`: 구글 script가 버튼을 그려 넣을 실제 DOM 칸을 잡는다.
- 내가 채운 가정:
  - 오류 문구는 구글 버튼 칸 하나에만 보인다(비밀번호 로그인 실패도 그 칸).
  - 같은 계정이 처음 로그인을 거의 동시에 두 번 하면 unique 제약으로 500이 날 수 있다. 구글 버튼은 callback을 한 번만 부르므로 고치지 않았다.
  - Postman 모음에는 구글 로그인 요청을 넣지 않았다(ID token을 Postman에서 받을 수 없음).
  - 데모 대본은 시작 계정 그대로 두고, 구글로 데모할 때의 준비(테스트 사용자 4명, 교수 이메일, 같은 브라우저에 4개 계정)만 적었다. 어느 쪽으로 데모할지는 관리자가 정한다.

### GitHub public 저장소로 공개 (관리자 결정)
- 결정:
  - 관리자 결정(2026.10.26): 비공개로 둘 이유가 없어 `Pyrite9/footprint-demo`를 public으로 만든다. CLAUDE.md "반드시 먼저 말할 것"의 "공개 문서에 쓰지 않는다"를 지우고, 학습 저장소 쪽 금지만 남겼다. 대신 "비밀값과 실제 사람의 이메일·이름은 commit 하지 않는다"를 적었다.
- 고친 파일: `CLAUDE.md`
- 확인(push 전 보안 검토):
  - 추적 파일: 비밀값은 개발용 기본값(`change-me`, `change-me-please`, `footprint-demo-dev-secret-key-change-me-0000`)뿐이다. Google OAuth Client ID는 비밀값이 아니다(화면 번들에도 들어감). client secret은 쓰지 않는다.
  - 교수 이메일 `PROFESSOR_EMAILS`는 빈 값이다. 실제 값은 gitignore된 `infra/.env`에만 있다.
  - commit 이력 전체(31개): `@g.yju.ac.kr` 주소, GitHub·Google·AWS token 형식, 개인 키, 실제 JWT가 없다. `.env` 계열로 commit된 적이 있는 것은 `frontend/.env`, `infra/.env.example`뿐이고 모든 버전이 개발용 값이다.
  - 포트는 모두 `127.0.0.1`에만 연다(같은 네트워크의 다른 컴퓨터가 DB·MinIO에 붙지 못함). CORS는 `http://localhost:5173`만 받는다.
  - commit 작성자 이메일은 공개된다. 같은 이메일이 관리자의 다른 public 저장소 commit에 이미 있어서 새로 드러나는 것은 아니다.
- 읽을 때 볼 곳: 이 저장소를 접속 가능한 주소에 배포할 때는 `JWT_SECRET`, `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`를 환경 변수로 반드시 바꾼다. 기본값이 공개돼 있어서, 그대로 두면 누구나 token을 위조할 수 있다.
- 내가 채운 가정: 시작 데이터의 이름(김민준, 이서연 등)은 흔한 이름으로 지은 가짜 이름으로 보고 그대로 두었다. 조원의 실제 이름과 겹치면 바꾼다.

## 2026.10.28

### 노트북(Arch Linux) 작업 준비
- 결정:
  - `backend/gradlew`가 git에 실행 권한 없이(`100644`) 저장돼 있어서 Linux·macOS에서 clone 하면 `./gradlew`가 `Permission denied`로 실패한다. Windows에서 commit 해서 생긴 문제다. `git update-index --chmod=+x`로 `100755`로 바꿨다. 내용은 그대로다.
  - CLAUDE.md "실행 환경"에 노트북(Arch Linux)에서는 bash 기준으로 안내한다는 줄을 더했다. 조원이 읽는 README 안내는 Windows·macOS 기준 그대로 둔다.
- 고친 파일: `backend/gradlew`(파일 모드만), `CLAUDE.md`
- 확인: `git ls-files -s backend/gradlew`가 `100755`. Linux 점검: `gradlew`는 `.gitattributes`로 LF 고정, `frontend/src` import 경로는 대소문자까지 실제 파일과 같음, `package-lock.json`에 Linux용 rolldown 모듈 있음.
- 읽을 때 볼 곳: Windows에서는 파일 모드가 바뀐 게 눈에 보이지 않는다. `git diff`에 `old mode 100644 / new mode 100755`로만 나온다.
- 내가 채운 가정: 노트북은 x86_64로 보았다.

### 노트북 이식 가이드 올리기
- 만든 파일: `docs/laptop-setup.md`(노트북 Claude Code에게 넘기는 설치·설정 지시서)
- 결정: 노트북에서 바로 받을 수 있게 저장소에 올렸다. 저장소 맨 위 대신 `docs/`에 둔다. 관리자 git 이메일은 public 저장소라 적지 않고 "관리자에게 묻는다"로 바꿨다.
- 읽을 때 볼 곳: 전역 CLAUDE.md·메모리 내용이 그대로 들어 있다. 데스크톱에서 그 둘을 바꾸면 이 문서도 함께 고친다.

### 새로 clone 한 폴더에서 처음부터 끝까지 실행 (3단계, Arch Linux 노트북)
- 결정: GitHub `main`(`4f5be17`)을 새 폴더에 clone 하고, `infra/.env` 없이 확인했다. 시작 전에 `docker compose down -v`로 volume을 지우고 우리 이미지(`footprint-demo-backend`, `footprint-demo-frontend`)도 지워서 처음 실행과 같게 했다. DB는 켤 때마다 새로 만들고 시작 데이터도 다시 넣으므로 잃는 데이터는 없다.
- 고친 파일: `docs/worklog.md`
- 확인:
  - `docker compose up -d --build`: 이미지 빌드부터 네 컨테이너 시작까지 1분 30초. 백엔드는 시작 후 수 초 안에 401 JSON으로 응답했다.
  - 데모 흐름 API 63개 항목 통과(curl 스크립트, 저장소에 넣지 않음): 교수 과제 생성(학생 403, 조장 중복 400) → 조장 초대 코드 발급·재발급(조원 아님·교수 403) → 합류(이전 코드 404, 교수 403, 같은 조 재합류 200, 다른 조 409, 없는 코드 404) → 역할 지정(조원 403, 공백 trim, 두 번째 409) → 업로드(역할 없음·교수 403, 빈 파일·20MB 초과·파일 없음 400) → 목록 최신순·업로드 당시 역할명 복사, 교수 다운로드 내용 일치, 한글 파일 이름 RFC 5987, 다른 조 403, 시작 데이터 파일 다운로드 → 동료평가(빠짐·자기 자신·점수 6 400, 교수 403, 제출 200, 두 번째 409, 응답에 점수 합계·평균 없음) → 오류 모양(404 `NOT_FOUND`, 숫자 아닌 id·깨진 JSON 400, 없는 주소 404).
  - 제출 시각이 한국 시간으로 저장됨(`TZ: Asia/Seoul`).
  - 화면: `/`와 `/join/abc` 200(nginx `try_files`), 번들에 `http://127.0.0.1:8080`과 구글 Client ID가 들어감. CORS preflight는 `localhost:5173` 허용, `127.0.0.1:5173` 403.
  - 같은 clone에서 `./gradlew build`(JDK 21, 테스트 3개) 성공. Node 24.22.1(`.nvmrc`)에서 `npm ci`, `npm run build`, `npm run lint` 통과.
- 읽을 때 볼 곳:
  - `npm ci`가 취약점 4건을 알린다. `npm audit --omit=dev`는 0건이라 모두 개발용 패키지다. 화면 이미지(nginx)에는 들어가지 않는다.
  - `./gradlew build`의 테스트는 실행 중인 docker DB를 처음 상태로 되돌린다. 데모 직전에는 돌리지 않는다(데모 대본에도 있음).
  - 브라우저 확인(관리자가 노트북 브라우저에서 데모 대본 장면 1~8): 초대 링크 → 로그인 뒤 합류 화면으로 돌아옴, 한글 파일 이름 그대로 다운로드, 동료평가 미입력 경고와 고정 문구 2줄 확인, 어색한 화면 없음. 같은 시간대 백엔드 로그에 ERROR·예외 없음. 장면 5에서 `화면 설계서.txt`를 탭 4(박도윤)가 아니라 탭 3(이서연)에서 올려 "프론트엔드 1번째"가 나오지 않았다. 기능 문제가 아니라 누른 탭의 차이다.
  - 관리자 의견: 교수가 동료평가 결과를 보는 기능이 없다. demo-scope 8절 "만들지 않는 것"(교수 열람)과 4절(1단계는 저장까지)에 따라 12.12 이후다. 만들지 않았다.
- 내가 채운 가정: 노트북의 nvm 기본값이 Node 26(system)이라 그대로 두면 26으로 돈다. 팀 기준대로 `frontend`에서 `nvm use`로 24를 쓰는 것으로 보았다.

### 구조 보드 정리 (Whiteboard, 노트북)
- 결정:
  - 데모 전체 구조를 그림으로 보는 보드 "발자국 구조 뼈대"를 노트북 Whiteboard 앱에 둔다. 섹션: 무엇·왜, 실행 구성, 데모 흐름과 화면 지도, 인증 흐름, 데이터 구조, 요청 하나의 길(산출물 업로드 예).
  - 관리자 요청(2026.10.28): 본문 문장을 개조식으로 바꿨다. 날짜 언급(12.11 데모, 11.16 끼워 넣기)을 빼고 "구조 뼈대"로 적었다. 제목도 "발자국 시연본 전체 구조"에서 "발자국 구조 뼈대"로 바꿨다.
- 고친 파일: `docs/worklog.md`. 보드는 Whiteboard 앱에 저장되고 저장소에는 들어가지 않는다.
- 읽을 때 볼 곳:
  - 보드의 코드 링크는 노트북 ext4 clone의 commit을 가리킨다. 코드가 바뀌면 새 보드를 만들지 않고 보드 대상 commit을 옮긴 뒤 본문을 고친다.
  - `/mnt/work`(NTFS)를 대상으로 하면 백엔드 Java 코드가 보드에 표시되지 않는다. 그래서 ext4 clone을 대상으로 둔다.
- 내가 채운 가정: 다이어그램 노드 설명, 화살표 이름, 테이블 필드 설명은 이미 짧은 구절이라 개조식 변경에서 뺐다.

## 2026.10.29

### 멘토에게 저장소 링크를 보여주기 전 정리
- 결정:
  - 멘토는 저장소를 clone 하거나 실행하지 않고 **GitHub 링크만 본다**(관리자 결정 2026.10.29). 그래서 GitHub 페이지에서 바로 보이는 것(README, LICENSE)만 손봤다. 실행 리허설은 하지 않았다.
  - README 맨 위 소개 다음에 "지금 상태", "기술 스택", "폴더 구조"를 넣었다. 실행 안내보다 앞이다. 선택지는 (가) 실행 안내를 그대로 맨 위에 두고 상태를 아래에 붙이기, (나) 상태·구조를 위로 올리기였다. (나)를 골랐다. 저장소를 처음 여는 사람이 제일 먼저 찾는 것은 "이게 뭔데 어디까지 됐나"이고, 조원도 실행보다 범위를 먼저 보는 게 낫다고 봤다.
  - "지금 상태"에 **일부러 만들지 않은 것** 목록을 적었다. 교수의 평가 결과 열람 화면이 없는 것을 빠뜨린 기능이 아니라 2단계로 미룬 결정으로 읽게 하려는 것이다.
  - 라이선스는 **MIT**로 정했다. 선택지는 (가) 라이선스 없음, (나) MIT, (다) CC BY-NC였다. public 저장소에 라이선스가 없으면 법적으로는 아무 권리도 주지 않은 상태가 되어 조원이 코드를 복사해 쓰는 근거조차 없다. 11.16부터 조원 코드가 이 저장소에 들어오므로 그 전에 박아 두는 게 맞다고 봤다. 저작권자는 실제 이름 대신 `Pyrite9 and footprint-demo contributors`로 적었다(public 저장소).
  - 데스크톱의 `main`이 `origin/main`보다 3 commit(worklog만) 뒤처져 있었다. `git pull --ff-only`로 맞췄다.
- 만든 파일: `LICENSE`
- 고친 파일: `README.md`
- 확인(공개 저장소 점검):
  - 추적 파일에 실제 사람의 이메일 0건. 시작 데이터는 전부 `@example.com`이다.
  - 비밀값은 개발용 기본값뿐(`change-me`, `change-me-please`, `footprint-demo-dev-secret-key-change-me-0000`). Google OAuth Client ID는 비밀값이 아니고 client secret은 쓰지 않는다. `PROFESSOR_EMAILS` 기본값은 비어 있다.
  - README에 적은 버전을 파일에서 다시 확인했다: `build.gradle`(Java 21, Boot 3.5.16, jjwt 0.13.0, awssdk s3 2.55.14, google-api-client 2.9.1), `gradle-wrapper.properties`(8.14.3), `package.json`(React 19, Vite 8, React Router 7.18.4, json-server 0.17.4), `infra/docker-compose.yml`(postgres:17).
  - 빌드는 다시 돌리지 않았다. 코드를 고치지 않았고 바꾼 것은 `README.md`와 `LICENSE`뿐이다.
- 읽을 때 볼 곳:
  - README "문서" 목록에 `docs/laptop-setup.md`와 `CLAUDE.md`를 더했다. 전에는 저장소에 있는데 목록에 없어서 문서끼리 어긋나 있었다.
  - README "지금 상태"의 표는 `docs/demo-scope.md` 3절(1단계 기능 8개)과 8절(만들지 않는 것)에서 가져온 것이다. 범위가 바뀌면 두 곳을 같이 고친다.
- 내가 채운 가정:
  - GitHub 저장소의 About(설명·토픽)은 `gh` 권한이 필요해서 손대지 않았다. 관리자가 직접 넣는다.
  - 화면 스크린샷은 README에 넣지 않았다. 멘토가 실행하지 않는다면 효과가 크지만, 캡처는 관리자 판단이 필요해서 묻는 것으로 남겼다.

### README 문체를 개조식으로 (관리자 지시)
- 결정:
  - 관리자 지시(2026.10.29)로 맨 위 `README.md`의 문체를 "~합니다"에서 개조식(명사·"~함"·"~것" 종결)으로 바꿨다.
  - 범위는 맨 위 `README.md` 하나로 잡았다. 선택지는 (가) 새로 쓴 "지금 상태·스택·구조"만 개조식, (나) 맨 위 README 전체, (다) `backend/README.md`·`frontend/README.md`·`docs/`까지 전부였다. (나)를 골랐다. 한 문서 안에서 설명부만 개조식이고 실행 절차만 "~합니다"면 문체가 갈려 어설퍼 보인다. (다)는 하위 README가 조원이 한 줄씩 따라 하는 절차문이라 관리자에게 묻는 것으로 남겼다.
  - 긴 문단은 불릿으로 쪼갰다. "영리한 코드보다 따라 읽을 수 있는 코드", "비밀값", "데모라 매번 초기화함"이 그렇게 바뀐 것이다.
  - 평가 화면에 고정하는 문구("업로드 횟수는 기여도가 아닙니다", "점수는 시스템이 계산하지 않습니다")는 `docs/demo-scope.md` 4절의 문장 그대로여야 해서 개조식으로 바꾸지 않았다.
- 고친 파일: `README.md`
- 확인: `grep`으로 "니다"가 남은 줄이 위의 고정 문구 한 줄뿐인 것을 봤다.
- 읽을 때 볼 곳: 하위 `backend/README.md`, `frontend/README.md`는 "~합니다" 체로 남아 있다. 문체를 저장소 전체로 통일할지는 아직 결정하지 않았다.

### 하위 README도 개조식으로 (관리자 지시)
- 결정:
  - 관리자 지시(2026.10.29)로 `backend/README.md`, `frontend/README.md`도 맨 위 README와 같은 개조식으로 바꿨다. 종결은 `~함`, `~됨`, `~씀`, `~할 것`으로 맞췄다.
  - 절차 번호 목록의 제목은 "데이터베이스와 MinIO 켜기"처럼 명사형으로, 그 아래 설명은 불릿으로 내렸다. 전에는 번호 아래에 문단이 이어져 있어서 한 항목이 어디까지인지 눈에 덜 띄었다.
  - `docs/`는 바꾸지 않았다. `demo-script.md`는 데모 중에 소리 내어 읽는 대본이라 "~합니다"가 맞고, `worklog.md`는 이미 "~했다" 평서체다. 바꾸려면 관리자가 따로 지시한다.
- 고친 파일: `backend/README.md`, `frontend/README.md`
- 확인:
  - `grep`으로 "니다"가 남은 줄을 셌다. `frontend/README.md` 한 줄만 남았고 화면에 실제로 보이는 문구("김민준 (학생)님, 환영합니다.")라서 그대로 뒀다.
  - 코드 블록, 명령, 표(시작 계정 11줄, 시작 과제·조 10줄)는 한 글자도 바꾸지 않았다.
- 읽을 때 볼 곳: 이제 저장소의 README 3개가 같은 문체다. 조원이 새 문서를 추가할 때도 개조식으로 맞춘다.

### docs 문체 점검과 demo-script 개조식 (관리자 지시)
- 결정:
  - 관리자 지시(2026.10.29)로 `docs/`도 문체를 봤다. 실제로 바꾼 것은 `docs/demo-script.md` 하나다.
  - `demo-script.md`에서 **진행자용 지시만** 개조식으로 바꿨다: 1절 준비(전날·시작 5분 전), 2절 계정 안내, 3절 머리말, 4절 막혔을 때의 "할 것" 칸.
  - **대사는 바꾸지 않았다.** "말할 것:" 뒤 문장과 5절 "질문이 나오면"의 답변은 발표자가 청중에게 소리 내어 말하는 말이다. 개조식으로 바꾸면 읽을 수 없다. 3절 머리말과 5절 머리에 "소리 내어 말하는 문장이라 ~합니다 체로 둠"을 적어, 나중에 문체를 통일하려는 사람이 이 줄만 보고 멈추게 했다.
  - 표의 "화면에 나오는 것" 칸도 그대로 뒀다. 화면에 실제로 나오는 문구를 옮긴 것이다.
  - 2절 구글 데모 준비는 한 문단이던 것을 불릿 4개로 쪼갰다. 준비물이 4개인데 문단에 붙어 있어 빠뜨리기 쉬웠다.
  - `api-draft.md`(3줄), `demo-scope.md`(2줄), `worklog.md`(36줄)는 바꾸지 않았다. `grep`으로 "니다"가 걸린 줄을 전부 봤는데 모두 오류 메시지·화면 문구 인용("없는 주소입니다", "볼 수 있는 과제가 없습니다")이고, 문장 본체는 이미 "~한다"·"~이다" 평서체였다. 인용을 고치면 코드와 어긋난다.
- 고친 파일: `docs/demo-script.md`
- 확인: `grep`으로 "니다"가 남은 줄이 모두 "말할 것" 대사, 표의 화면 문구, 5절 답변인 것을 확인했다.
- 읽을 때 볼 곳: 저장소 문체는 이제 세 갈래다. README 3개와 대본의 지시부는 개조식, 규칙·명세 문서(`demo-scope.md`, `api-draft.md`)와 `worklog.md`는 "~한다" 평서체, 대본의 대사와 화면 문구는 "~합니다". 갈린 게 아니라 읽는 상황이 달라서 나눈 것이다.

### 발견: demo-script 5절의 구글 로그인 답변이 낡음
- 아직 고치지 않았다. 관리자에게 보고만 한다.
- `docs/demo-script.md` 5절 마지막 답변이 "지금은 시작 계정으로 시연합니다. 학교 구글 계정 로그인은 정해지는 대로 반영합니다"다. 구글 로그인은 2026.10.26에 확정돼 이미 구현됐고, 같은 문서 2절에는 구글 데모 준비 절차가 적혀 있다. 그 답변 줄만 확정 전 상태로 남아 있다.
- 문체 작업과 별개인 내용 수정이라 손대지 않았다. 12.11에 어느 쪽으로 데모할지(구글/시작 계정)가 답변 문장을 정하므로 관리자 결정이 필요하다.

### 용어 "데모"로 통일, CLAUDE.md에 외부 독자용 머리말 (관리자 지시)
- 결정:
  - 관리자 지시(2026.10.29)로 "시연본"을 모두 **"데모"** 로 바꿨다. 추적 파일 11개 16군데다. 이 worklog의 과거 기록도 같이 바꿨다. 용어 이름이므로 기록만 옛 이름으로 남기면 나중에 찾을 때 두 이름을 다 알아야 한다.
  - "시연"(시연 흐름, 시연 범위, 시연 대본, 12.11 시연)은 그대로 뒀다. 지시는 "시연본 → 데모"였고, "시연"은 12.11에 발표하는 행위를 가리키는 다른 말이다. 이것도 바꿀지는 관리자가 정한다.
  - 치환 뒤 조사가 어긋난 4군데를 고쳤다("데모을" → "데모를", "데모이라" → "데모라", "데모은" → "데모는"). "시연본"은 받침이 있고 "데모"는 없어서 생긴 것이다.
  - CLAUDE.md에 **"이 파일은 무엇인가"** 절을 맨 앞에 넣었다. 규칙 본문은 한 줄도 바꾸지 않았다. 지금의 작동을 그대로 두라는 지시여서, 규칙을 옮기거나 지우지 않고 설명만 더했다.
    - 넣은 설명: 이 파일이 AI 코딩 도구용 설정이라 명령형 반말인 것, "관리자"가 저장소 소유자를 가리키는 역할 이름인 것, `../footprint-study`가 비공개 학습 저장소이고 없어도 빌드·실행이 되는 것, 규칙에 붙은 날짜의 뜻, "묻지 않고 진행"이 사람의 검토를 건너뛴다는 뜻이 아닌 것.
    - 선택지는 (가) 개인적인 줄을 gitignore된 로컬 설정으로 옮기기, (나) 설명 머리말만 더하기였다. (가)는 조원이 clone 했을 때 규칙이 사라져 작동이 달라지므로 (나)로 했다.
  - "금지" 절 머리에 한 줄을 더했다. 기술의 좋고 나쁨을 가린 목록이 아니라 초심자가 읽을 코드에서 추상 계층을 줄이려는 선택이라는 것. 외부에서 "왜 QueryDSL을 금지하나"로 읽힐 자리였다.
- 고친 파일: `CLAUDE.md`, `README.md`, `backend/README.md`, `frontend/README.md`, `docs/api-draft.md`, `docs/postman/README.md`, `docs/postman/footprint-demo.postman_collection.json`, `docs/postman/sample-upload.txt`, `docs/worklog.md`, `.claude/skills/rule-check/SKILL.md`, `.claude/skills/next-step/SKILL.md`
- 확인:
  - `git grep "시연본"`이 0건(이 worklog의 용어 변경 기록과 옛 이름 인용은 빼고).
  - 조사 검사(`데모(을|은|이라|으로|과|이다|이고|이며)`)가 0건.
  - Postman 모음 JSON을 `node -e "require(...)"`로 읽어 파싱되는 것과 `info.name`이 "발자국 데모 API"인 것을 확인했다.
  - 빌드는 돌리지 않았다. `git diff --name-only`로 `.java`, `.js`, `.jsx`, `.yml`, `.gradle` 파일 변경이 0건인 것을 확인했다. 바뀐 것은 문서와 Postman 모음뿐이다.
- 읽을 때 볼 곳: `.claude/skills/`의 rule-check, next-step 설명문에도 "데모"가 들어 있다. 이 저장소에서 `/rule-check`를 부를 때 쓰는 검사 기준이다.
- 내가 채운 가정:
  - `CLAUDE.md`의 "실행 환경" 절("관리자 컴퓨터는 Windows다", Arch Linux 노트북)은 그대로 뒀다. 개인 환경 정보지만 비밀값이 아니고, 지우면 명령 예시를 어느 쪽으로 쓸지 판단하는 근거가 없어져 작동이 달라진다.
  - 서비스 이름 "발자국"은 그대로 둔다. 바뀐 것은 "이 저장소가 무엇인가"를 가리키는 말뿐이다.

### "시연" 표현 제거, 범위·대본 문서 삭제, TODO.md 신설 (관리자 지시)
- 결정:
  - 관리자 지시(2026.10.29)로 **"시연"이라는 말을 모두 "데모"로** 바꿨다. 추적 파일 18개다. 코드에도 있었다(`DataInitializer`, `AuthService`, 테스트 2개의 주석, 시작 과제 제목 "발자국 시연 과제", 시작 산출물 본문, `frontend/db.json`, `frontend/Dockerfile`, `mock/files/seed-1.txt`).
  - 조사가 어긋난 7군데를 고쳤다("데모은" → "데모는", "데모을" → "데모를"). "시연장 인터넷"은 "발표장 인터넷"으로 바꿨다.
  - 이 worklog의 **용어 변경 이력 항목에서는 옛 이름을 되살렸다.** "시연본을 데모로 바꿨다"가 "데모본을 데모로 바꿨다"가 되면 기록이 뜻을 잃는다.
  - 관리자 지시로 `docs/demo-scope.md`(122줄)와 `docs/demo-script.md`(164줄)를 **삭제했다.** "코드와 코드 설명만 남긴다"는 지시다. git 이력에는 남아 있어 되돌릴 수 있다.
  - 삭제로 잃는 내용을 두 곳으로 옮겼다. 그냥 지우면 CLAUDE.md의 규칙이 가리킬 대상을 잃어 작동이 달라진다.
    - **`TODO.md`(새로)** — 미룬 단위 업무(T-2-2, R-1-3, E-2-2), 2단계 기능 8개, 만들지 않기로 한 것(점수 계산·LLM), 아직 정하지 않은 것 3개, 배포 전 반드시 할 것 5개, 알려진 한계 7개, 조원과 일할 때. 알려진 한계는 이 worklog와 `api-draft.md`에 흩어져 있던 것을 모은 것이다.
    - **`CLAUDE.md`에 "업무 규칙" 절** — 서비스 원칙, 기록 시각, 접근 범위, 회원·과제·조·역할·산출물·동료평가 규칙 11줄. demo-scope 4절에서 코드가 지켜야 하는 것만 남겼다.
  - CLAUDE.md에서 고친 것: 기준 문서 목록(2순위를 "이 파일의 업무 규칙과 금지"로), `@docs/demo-scope.md` import 제거, 구조 설명의 `docs/` 목록, "금지"와 "묻지 않고 진행" 절의 참조를 `TODO.md`로.
  - 동료평가 6개 항목 문장은 잃지 않았다. `frontend/src/constants/peerEvaluation.js`에 이미 그대로 들어 있다. CLAUDE.md 업무 규칙에 "그 문장은 고치지 않는다"를 적었다.
- 만든 파일: `TODO.md`
- 지운 파일: `docs/demo-scope.md`, `docs/demo-script.md`
- 고친 파일: `CLAUDE.md`, `README.md`, `backend/README.md`, `frontend/README.md`, `frontend/Dockerfile`, `frontend/db.json`, `frontend/mock/files/seed-1.txt`, `docs/api-draft.md`, `docs/postman/README.md`, `docs/postman/footprint-demo.postman_collection.json`, `docs/worklog.md`, `.claude/skills/rule-check/SKILL.md`, `.claude/skills/next-step/SKILL.md`, `backend/src/main/java/.../config/DataInitializer.java`, `.../service/AuthService.java`, `.../test/.../BackendApplicationTests.java`, `.../test/.../StorageServiceTests.java`
- 확인:
  - `git grep "시연"`이 0건(이 worklog의 용어 변경 기록과 옛 이름 인용은 빼고). 조사 검사도 0건.
  - 깨진 참조 검사: worklog를 뺀 모든 파일에서 `demo-scope`·`demo-script` 참조가 0건. `api-draft.md`의 "demo-scope N절" 인용 6군데는 괄호 안을 결정 날짜나 `CLAUDE.md "업무 규칙"`으로 바꿨다.
  - 빌드: `backend`에서 `./gradlew build` BUILD SUCCESSFUL(테스트 포함, docker의 PostgreSQL·MinIO 켠 상태). `frontend`에서 `npm run build`, `npm run lint` 통과.
- 읽을 때 볼 곳:
  - 이 worklog의 **과거 기록에는 `demo-scope` 참조가 그대로 남아 있다.** 그때는 있던 문서라서 일부러 고치지 않았다. 2026.10.29 이후로 그 파일은 없다. 규칙을 찾을 때는 `CLAUDE.md` "업무 규칙"과 `TODO.md`를 본다.
  - 시작 과제 제목이 "발자국 데모 과제"로 바뀌었다. `DataInitializer`와 `frontend/db.json`, `docs/api-draft.md` 예시가 같은 값이어야 한다.
- 내가 채운 가정:
  - "코드와 코드 설명만 남긴다"에서 `docs/api-draft.md`(API 명세)와 `docs/worklog.md`(결정 기록), `docs/postman/`은 코드 설명으로 보아 남겼다. `docs/laptop-setup.md`도 남겼다.
  - 12.11 발표 대본이 사라졌다. 발표 준비가 필요하면 다시 만들어야 한다. git 이력(`git show 9188187:docs/demo-script.md`)에서 꺼낼 수 있다.

### 구조 그림 docs/architecture.md 신설 (관리자 지시)
- 결정:
  - 관리자 지시(2026.10.29)로 전체 구조·JWT 흐름·프론트엔드 구조·백엔드 구조 네 장을 **GitHub에서 바로 보이게** 만들었다.
  - **Mermaid를 골랐다.** 선택지는 (1) Whiteboard 앱에 보드로 만들고 Mermaid로 옮기기, (2) 바로 Mermaid. Whiteboard 앱에는 내보내기 기능이 없고(MCP 도구 목록, 앱의 작성 지침 전문, 웹 검색 세 군데에서 확인), 보드 데이터는 `.git/dev-fast/`에 있어 저장소 파일이 아니다. 거치면 두 벌을 관리하게 되고 어긋났을 때 어느 쪽이 맞는지 알 수 없다. 관리자가 (2)를 골랐다.
  - 그림을 다섯 개 넣었다. 네 번째(백엔드 구조) 아래에 데이터 구조 `erDiagram`을 하나 더 뒀다. 엔티티 6개와 연결, unique 제약을 코드 없이 한눈에 보려는 것이다.
  - 그림 속 글자는 **실제 파일·클래스 이름과 같게** 맞췄다. 그림에서 본 이름으로 파일을 찾을 수 있어야 한다는 기준이다.
  - 예시를 하나씩 정했다. 프론트엔드는 조 화면(`/teams/:teamId`), 백엔드는 산출물 업로드. 모든 화면·API를 그리면 그림이 읽히지 않는다.
- 만든 파일: `docs/architecture.md`
- 고친 파일: `README.md`(폴더 구조의 `docs/` 줄, 문서 목록에 추가), `TODO.md`(조원과 일할 때), `CLAUDE.md`(구조 설명의 `docs/` 목록), `docs/worklog.md`
- 확인:
  - Mermaid 5개를 `mermaid@11.4.1`의 `parse`로 검사해 모두 OK. 브라우저에서 실제로 그려 보고 다섯 장 모두 읽히는지 눈으로 봤다.
  - 코드 파일은 건드리지 않았다. 빌드는 돌리지 않았다.
- 읽을 때 볼 곳:
  - GitHub는 Markdown 안의 ```mermaid 블록을 스스로 그려 준다. 그림 파일(png, svg)을 따로 두지 않는다.
  - **코드를 고치면 이 문서도 같은 작업 안에서 고친다.** 그림이 코드와 어긋나면 처음 읽는 사람을 잘못 안내한다. 문서 첫머리에 적어 뒀다.
- 내가 채운 가정:
  - 그림 3에서 `constants/peerEvaluation.js`를 처음에 넣었다가 뺐다. 예시로 둔 조 화면은 그 파일을 쓰지 않아 선이 거짓이 된다. 대신 글머리 줄로 설명했다.
  - 그림 1에 가짜 API(json-server 3001)를 점선으로 넣었다. 실제 데모 흐름은 아니지만 조원이 Java 없이 화면만 만들 때 쓰는 갈래라서 구조에 포함했다.

## 2026.10.30

### 문서 검수와 오류 정리 (관리자 지시)
- 결정:
  - 관리자 지시(2026.10.30)로 에이전트 5개가 나눠서 검수함. 나눈 축은 API 명세 ↔ 백엔드·가짜 API, 구조 그림 ↔ 코드, worklog 전체, 실행·설정 문서와 문서 사이 교차 확인, 초심자 조원 시점임. 에이전트가 낸 핵심 주장은 코드에서 다시 확인한 뒤 고침
  - 관리자 선택(2026.10.30): 이번에는 **문서 오류만** 고침. 코드 동작 문제와 개선 제안(새 절 추가, 문서 분리)은 하지 않음
  - `architecture.md`의 JWT 그림은 화살표 몇 개를 고쳐서는 맞출 수 없어서 "2-1 로그인하고 token 받기", "2-2 그 다음 요청마다" 두 장으로 나눔. 그림은 이제 여섯 장임. 장수를 적은 곳(`architecture.md`, `README.md`, `TODO.md`)은 숫자를 빼고 "구조 그림"으로만 적음. 그림을 더할 때마다 세 곳이 어긋나지 않게 하려는 것임
  - 시퀀스 그림의 메시지에서 큰따옴표를 뺌. 시퀀스 그림은 따옴표를 글자 그대로 그려 냄(이전 그림도 그랬음)
  - 이 worklog의 과거 기록은 바꾸지 않는 것이 원칙임. 다만 2026.10.29 일괄 치환("시연" → "데모")이 망가뜨린 줄은 옛 문구로 되돌림(443, 690, 772, 837, 843, 863행). 치환 전 문구는 `git show 357de8d~1:docs/worklog.md`, `git show 9188187~1:docs/worklog.md`로 확인함. 771행의 Whiteboard 보드 섹션 이름은 보드를 볼 수 없어서 그대로 둠
- 고친 파일:
  - `docs/architecture.md`
    - 그림 1: 데모 포트(5173 → 컨테이너 nginx 80), "서버가 구글에 token을 보냄" → "구글 공개키 받기", 깨진 문장, `bootRun` 백엔드가 모든 주소에 열리는 것
    - 그림 2: `GoogleLoginButton`·`AuthController`·`GoogleTokenVerifier`·`TeamPage`·`client.js`를 넣고, 로그인 실패 갈래를 추가
    - 그림 3: `TeamMemberList → RoleAssignForm`, `api/auth.js`, `RequireLogin → token.js`
    - 그림 4: 쓰지 않는 `DuplicateException`을 빼고 `TeamRepository`, `TeamMemberRepository`를 넣음. 예외 화살표, dto 화살표 방향
    - ERD: 관계 이름, nullable 표기, 열 이름 표기 설명, "두 번 합류는 409" → 같은 조 재합류는 200
  - `docs/api-draft.md`: 제목 날짜 표시의 뜻, "미정 6" 참조 삭제, 가짜 API token, 구글 로그인의 500·이름 대체·대소문자, 없는 회원 token에 가짜 API는 401, 업로드 400 조건, 다운로드 header 실제 모양, 평가 400 조건
  - `backend/.../controller/DeliverableController.java`: Content-Disposition 주석(주석만, 동작 같음)
  - `CLAUDE.md`: 사실 오류만 고침. `docs/` 목록, 조원 목록 예시, 전용 예외 5개, 없는 파일 `mock/routes.json` 삭제, "미정 항목" → `TODO.md` "아직 정하지 않은 것"
  - `README.md`: 파일 수 삭제, 폴더 트리에 `docker-compose.yml`·`TODO.md`, MinIO 계정 출처, 개발 → 한 번에 실행으로 바꾸는 순서, 문서 목록 설명
  - `TODO.md`: `isLeader` → `leader`, 테스트 3개, 그림 장수
  - `backend/README.md`: 백엔드 주소 10곳을 `127.0.0.1:8080`으로 바꾸고 이유를 적음. `.env` 복사는 선택이라는 것, MinIO 계정 출처
  - `frontend/README.md`: 없어진 "환영합니다" 문구 → 상단 바 "김민준 (학생)", 본문 "과제 목록"
  - `docs/postman/README.md`와 모음: API 17개 중 16개(구글 로그인을 뺀 이유), 시험 파일 정리, 폴더 3·5 설명
  - `docs/laptop-setup.md`: 날짜가 지난 지시서라는 머리말, `.env` 키 목록 비교 명령
  - `docs/worklog.md`: 치환 사고 6곳, 851·875행 확인 문장, 883행의 실패하는 명령(`HEAD~1` → `9188187`), 맨 위 템플릿
- 확인:
  - Mermaid 6장: `mermaid@11.4.1`로 `parse`와 실제 그리기 모두 성공. 브라우저에서 시퀀스 그림의 참여자·메시지 글자를 DOM으로 읽어 따옴표가 없는 것을 확인함. 확인용 HTML은 `frontend/public`에 잠깐 두었다가 지움
  - 다운로드 header의 실제 모양: 검수 에이전트가 spring-web 6.2.19 원본 `ContentDisposition.toString()`을 읽고, 같은 jar로 출력을 찍어 확인함
  - `backend`에서 `./gradlew compileJava` 성공. 바꾼 Java는 주석 한 줄이라 `gradlew build`(테스트, docker 필요)는 돌리지 않음
  - `frontend`에서 `npm run build`, `npm run lint` 통과. Postman 모음 JSON 파싱 성공. `git diff --check` 문제 없음
  - 낡은 표현 grep(`네 장`, `routes.json`, `미정 6`, `환영합니다`, `HEAD~1:docs`, backend README의 `localhost:8080`): worklog 과거 기록 말고는 0건
- 읽을 때 볼 곳:
  - `architecture.md` 2-1 그림 아래 둘째 줄: 로그인 실패가 `/login`으로 튕기지 않는 이유(`removeToken()`을 먼저 부름). `client.js`의 401 처리는 "token을 보냈을 때"만 동작함
  - `architecture.md` ERD 아래 "합류" 줄: 같은 과제 한 조만 허용하는 규칙은 unique 제약이 아니라 Service 검사임
- 고치지 않은 것(관리자 결정 또는 코드 작업 필요):
  - 코드 동작: 조원이 혼자인 조는 동료평가를 제출할 수 없음(`@NotEmpty`와 빈 대상 집합). 다른 두 조의 초대 코드로 동시에 합류하면 두 조에 모두 들어갈 수 있음. 실제 백엔드는 점수 4.5를 4로 받음(가짜 API는 400)
  - 관리자 환경: 데스크톱 `infra/.env`에 `PROFESSOR_EMAILS` 키가 없음. 데스크톱 `JAVA_HOME`이 없는 경로를 가리킴(이번에는 명령 하나에만 실제 경로를 넣음)
  - 규칙 충돌: `.claude/skills/next-step`, `rule-check`는 "기다린다·먼저 보고한다"인데 CLAUDE.md는 "묻지 않고 진행"임. CLAUDE.md의 `XxxCreateRequest` 이름 규칙과 실제 DTO 이름(`LoginRequest`, `TeamJoinRequest` 등)도 다름. 규칙 파일이라 손대지 않음
- 내가 채운 가정:
  - CLAUDE.md는 규칙 파일이지만 "코드와 다른 사실"(없는 파일, 예외 목록, 응답 예시, 폴더 목록)은 오류 정리 범위로 보고 고침. 규칙의 뜻을 바꾸는 것(위 skill 충돌, DTO 이름 규칙)은 범위 밖으로 봄
  - `DeliverableController`의 주석 고침은 코드가 아니라 문서 오류로 봄

### 규칙 충돌 2건 정리 (관리자 지시)
- 결정(관리자 지시 2026.10.30 "규칙 충돌 정리", 고른 방향은 내 추천안):
  - skill과 CLAUDE.md: `next-step`은 관리자의 "진행"을 기다리고 미정 항목에서 멈췄고, `rule-check`는 "고치지 말고 먼저 보고"였음. CLAUDE.md "묻지 않고 추천안으로 진행"(2026.10.25 관리자 결정)과 "작업 방식"의 "걸린 것은 고친다"와 어긋났음. 지금까지는 데스크톱 메모리가 이 충돌을 덮고 있어서, 저장소만 clone 한 곳(노트북, 조원)에서는 동작이 달라질 수 있었음
    - 선택지: (A) skill을 CLAUDE.md에 맞춤 (B) CLAUDE.md를 skill에 맞춰 다시 묻는 방식으로 돌림. 2026.10.25 결정이 더 최근이고 기준 문서 순위도 CLAUDE.md가 위라서 (A)로 함
    - `next-step`: 계획을 보여 준 뒤 바로 시작함. 미정 항목은 선택지와 비용을 적고 추천안으로 진행하며 worklog "결정" 줄에 남김. 파일 8개를 넘으면 둘로 나눠 앞의 것부터 함
    - `rule-check`: 부른 사람에 따라 나눔. 단위 업무 직후의 자가 검사는 보고하고 바로 고침. 관리자가 `/rule-check`로 직접 부르면 보고만 하고 "고쳐"를 기다림. 관리자가 직접 부른 것은 "검사해 달라"는 뜻이라 고치는 것까지 하면 요청보다 많이 하는 것으로 봄
    - `read-check`는 관리자와 묻고 답하는 것이 목적이라 그대로 둠
  - DTO 이름 규칙: CLAUDE.md는 `XxxCreateRequest` 하나만 적었지만 실제 요청 DTO는 `TeamJoinRequest`, `RoleAssignRequest`, `EvaluationSubmitRequest`, `LoginRequest`임. 선택지는 (A) 규칙을 실제 이름에 맞춰 "`<대상><동작>Request`"로 넓힘 (B) DTO 이름을 모두 `XxxCreateRequest` 모양으로 바꿈. (B)는 "만들기"가 아닌 요청(합류, 지정, 제출, 로그인)에 Create를 붙이게 되어 이름이 거짓이 됨. (A)로 함
- 고친 파일: `.claude/skills/next-step/SKILL.md`, `.claude/skills/rule-check/SKILL.md`, `CLAUDE.md`(DTO 이름 규칙 한 줄), `docs/worklog.md`
- 확인: 문서·skill만 고쳐서 빌드는 돌리지 않음. `git grep "진행\"을 기다린다"`가 0건
- 읽을 때 볼 곳: 데스크톱 메모리 `autonomous-workflow.md`의 "How to apply"가 하던 일을 이제 skill 본문이 함. 메모리는 지우지 않음(같은 내용이라 해가 없음)
- 내가 채운 가정: `next-step` 3번의 "`../footprint-study/docs/api.md`가 있으면 먼저 읽는다"와 CLAUDE.md 기준 문서 순위(api-draft가 api.md보다 위)도 어긋나지만, 학습 저장소에 `api.md`가 아직 없어서 이번에는 손대지 않음

### 단위 1: 혼자인 조의 동료평가 안내 (관리자 지시 "코드 문제 단위업무로 수정")
- 결정: 조에 자신만 있으면 `POST /teams/{teamId}/evaluations`가 400 `INVALID_INPUT` "평가할 조원이 없습니다. 조에 자신만 있습니다"를 돌려줌. 상태는 `submitted:false` 그대로 둠
  - 문제: 이전에는 빈 목록이면 "입력값이 올바르지 않습니다", 자기 id를 보내면 "자신을 뺀 조원 전원을 한 번씩 평가해야 합니다"가 나왔음. 둘 다 이유를 알려 주지 않았고 api-draft에도 이 경우가 없었음
  - 선택지: (A) 400과 이유 문구 (B) 빈 제출을 "제출 완료"로 기록 (C) 그대로 둠. (B)는 아무것도 저장하지 않은 제출을 만들고 Evaluation 행 없이 상태를 표시할 방법이 따로 필요해서 Entity가 바뀜. (A)로 함
  - 화면은 이미 `EvaluationPage.jsx`에서 대상이 0명이면 폼 대신 "평가할 조원이 없습니다."를 보여 주고 있었음. 화면 코드는 고치지 않음. 처음 검수 때 "혼자인 조는 영영 제출할 수 없다"고 한 것은 과장이었음
- 고친 파일: `backend/.../service/EvaluationService.java`(대상이 0명이면 400), `frontend/mock/middleware.cjs`(같은 검사와 같은 문구), `docs/api-draft.md`(400 줄과 서버 규칙 한 줄), `.claude/launch.json`(가짜 API를 preview로 띄우는 `mock` 설정 추가)
- 확인:
  - 실제 백엔드(Docker 전체 스택): 혼자인 조에서 자기 id → 400 새 문구, 빈 목록 → 400 "입력값이 올바르지 않습니다"(`@NotEmpty`가 먼저 막음), 상태 `submitted:false`. 보통 조에서 한 명 빠뜨림 → 기존 문구 그대로
  - 가짜 API: 같은 요청에 같은 결과
  - 화면: 혼자인 조의 평가 화면에 "평가할 조원이 없습니다."가 보임
  - `gradlew build`(테스트 포함), `npm run build`, `npm run lint` 통과. rule-check 자가 검사 걸린 것 없음
- 읽을 때 볼 곳: 빈 목록은 Service까지 오지 않음. `EvaluationSubmitRequest`의 `@NotEmpty`가 Controller 앞에서 막아서 일반 400 문구가 나감. 새 문구는 자기 id처럼 "무언가를 보냈는데 대상이 0명"일 때만 나감
- 내가 채운 가정: 문구 "평가할 조원이 없습니다. 조에 자신만 있습니다"는 내가 정함

### 단위 2: 같은 과제 두 조에 동시 합류 막기 (관리자 지시 "코드 문제 단위업무로 수정")
- 결정: `team_member`에 `assignment_id`를 두고 unique 제약을 `(team_id, member_id)`에서 `(assignment_id, member_id)`로 바꿈. 합류 저장은 `saveAndFlush` + `DataIntegrityViolationException` → 409 `DUPLICATE`. 동료평가 제출과 같은 모양임
  - 문제: "한 학생은 한 과제에서 한 조"를 Service만 확인했음. 같은 학생이 1조와 2조 링크로 거의 동시에 합류하면 두 요청이 모두 검사를 통과해 두 조에 들어갈 수 있었음. 이전 unique `(team_id, member_id)`는 같은 조에 두 번 들어가는 것만 막았고, 그때는 500이 났음(TODO "알려진 한계"에 적혀 있던 것)
  - 선택지: (A) `team_member`에 과제를 복사해 두고 unique (B) 합류할 때 회원 행을 잠금(`@Lock(PESSIMISTIC_WRITE)`)해서 요청을 줄 세움 (C) 그대로 두고 한계로 적어 둠. (B)는 초심자에게 잠금과 트랜잭션 격리를 설명해야 하고, 잠금을 빼먹은 다른 저장 경로(과제 생성의 조장 저장)는 막지 못함. (A)는 열 하나가 중복되지만 생성자가 `team.getAssignment()`로 채워서 어긋날 수 없고, 평가와 같은 "Service 검사 + DB unique" 패턴이라 읽기 쉬움. (A)로 함
  - 동시 요청의 두 번째가 같은 조였는지 다른 조였는지는 다시 조회할 수 없음(Postgres는 제약 위반 뒤 그 트랜잭션의 쿼리를 받지 않음). 그래서 문구를 "이미 이 과제의 조에 있습니다"로 넓혔고, 같은 조로 동시에 두 번 보낸 경우도 200이 아니라 409임
- 고친 파일: `backend/.../entity/TeamMember.java`(assignment 연관과 unique 제약), `backend/.../service/TeamService.java`(join 저장), `docs/api-draft.md`(합류 409 줄과 서버 규칙), `docs/architecture.md`(ERD와 설명), `TODO.md`("동시 합류 요청" 한계 삭제), `docs/worklog.md`
- 가짜 API: 고치지 않음. 응답에 `assignment_id`가 나가지 않고, json-server는 요청을 하나씩 처리해서 동시성 문제가 없음
- 확인:
  - `gradlew build`(테스트 포함) 통과. 백엔드 컨테이너를 다시 빌드한 뒤 Postgres `\d team_member`에 `UNIQUE (assignment_id, member_id)`가 있음
  - node로 학생 8명이 두 조에 동시에 합류(라운드 5번, 요청 80개): 200 40개, Service 검사의 409 10개, unique 제약의 409 30개. 500 없음. 라운드마다 두 조의 조원 합이 정확히 8명
  - curl 순차 요청: 같은 조 재합류 200, 다른 조 409 "이미 이 과제의 다른 조에 있습니다". 이전과 같음
  - 프론트엔드 코드는 고치지 않아서 `npm run build`·`lint`는 돌리지 않음. rule-check 자가 검사 걸린 것 없음
- 읽을 때 볼 곳: `TeamMember` 생성자의 `this.assignment = team.getAssignment();` 한 줄이 이 단위의 핵심임. 호출하는 쪽(`AssignmentService`, `DataInitializer`, `TeamService`)은 바뀌지 않음. `ddl-auto: create`라 스키마는 다시 켤 때 새로 만들어짐
- 내가 채운 가정: 동시 요청의 409 문구 "이미 이 과제의 조에 있습니다"는 내가 정함

### 단위 3: 정수가 아닌 점수 거부 (관리자 지시 "코드 문제 단위업무로 수정")
- 결정: `application.yml`에서 Jackson의 자동 변환 두 가지를 끔. `spring.jackson.deserialization.accept-float-as-int: false`, `spring.jackson.mapper.allow-coercion-of-scalars: false`
  - 문제: 실제 백엔드는 점수 `4.5`를 받아 4로 잘라 저장했고(200), `"5"`, `5.0`, `1e0`도 숫자 5·1로 받아들였음. 가짜 API는 `Number.isInteger`로 검사해서 `4.5`, `"5"`를 400으로 막았음. 같은 요청에 결과가 달랐음
  - 선택지: (A) 설정 두 줄로 서버 전체에 적용 (B) 점수 필드를 `JsonNode`로 받아 직접 검사 (C) 점수 필드마다 커스텀 deserializer. (B)·(C)는 DTO가 복잡해지고 다른 숫자 필드(`targetTeamMemberId`)는 그대로 남음. (A)는 숨은 동작이 설정 파일 한 곳에 보이고, 의존성도 늘지 않음. (A)로 함
  - 영향 범위: 모든 요청 body. 날짜(`"2026-11-02"`)는 원래 문자열로 받는 타입이라 영향이 없음. path·query 값은 Jackson을 거치지 않아서 영향이 없음
- 고친 파일: `backend/src/main/resources/application.yml`, `docs/api-draft.md`("공통"에 타입 규칙 한 줄, 평가 제출 400 줄), `docs/worklog.md`
- 가짜 API: 고치지 않음. 이미 `Number.isInteger`와 `typeof === 'number'`로 검사하고 있었음. 남은 차이 두 가지는 api-draft에 적음
  - `5.0`, `1e0`: JavaScript가 JSON을 읽으면 `5`, `1`과 같은 값이 되어 가짜 API는 받음. 실제는 400임. 화면은 정수만 보냄
  - 400 메시지: 타입이 틀리면 실제는 "요청 body의 형식이 올바르지 않습니다", 가짜 API는 "입력값이 올바르지 않습니다"임. `code`는 둘 다 `INVALID_INPUT`임. 이 차이는 이 단위 전부터 있었음(`"abc"`)
- 확인:
  - `gradlew build`(테스트 포함) 통과. 컨테이너를 다시 빌드한 뒤 점수에 `4.5`, `"5"`, `true`, `5.0`, `1e0`, `"abc"` → 모두 400 "요청 body의 형식이 올바르지 않습니다". `0`, `6` → 400 "입력값이 올바르지 않습니다". `5` → 200. `targetTeamMemberId: "7"` → 400
  - 다른 흐름: 과제 생성(날짜 문자열), 초대 코드 발급, 합류가 그대로 200·409
  - 프론트엔드 코드는 고치지 않아서 `npm run build`·`lint`는 돌리지 않음. rule-check 자가 검사 걸린 것 없음
- 읽을 때 볼 곳: 이 설정은 코드에 보이지 않는 동작을 바꿈. "Integer 필드인데 왜 `"5"`가 400이지?"라고 물으면 `application.yml`의 `jackson` 부분이 답임. 이 400을 만드는 곳은 `GlobalExceptionHandler`의 `HttpMessageNotReadableException` 처리임
- 내가 채운 가정: 없음

### 사람용 문서 문체를 개조식으로 (관리자 지시 "전체 문구 어투를 개조식으로")
- 결정:
  - 범위는 관리자가 고름(2026.10.30): 사람용 문서만. `TODO.md`, `docs/api-draft.md`, `docs/laptop-setup.md`, `docs/postman/README.md`, 이 worklog의 2026.10.30 항목
  - 그대로 둔 것: `CLAUDE.md`·`.claude/skills`(도구 설정, 명령형 반말), 2026.10.29까지의 worklog 기록, 코드 주석(CLAUDE.md "짧은 완결 문장" 규칙), 큰따옴표·backtick 안 인용(오류 메시지·화면 문구·식별자), code block
  - 어미: ~한다→~함, ~이다→~임, ~있다→~있음, ~된다→~됨. 줄 끝 마침표 없앰. 한 줄에 문장이 여럿이면 사이의 ". "는 남김. `docs/postman/README.md`는 ~합니다 체였어서 같은 방식으로 바꿈
  - 문장을 쪼개거나 합치지 않음. 줄 수는 파일마다 그대로임
- 고친 파일: `TODO.md`, `docs/api-draft.md`, `docs/laptop-setup.md`, `docs/postman/README.md`, `docs/worklog.md`
- 확인:
  - 파일 5개 모두 바꾸기 전(HEAD)과 비교해서 code block, backtick 안, 큰따옴표 안, 숫자, 링크가 똑같음(스크립트로 비교). 다른 것은 일부러 바꾼 `laptop-setup.md` 제목 2줄("새로 만든다" → "새로 만듦")뿐
  - `grep -E '(다|요)\.?\s*$'`로 남은 평서체를 찾음. 남은 줄은 모두 code block 안(`laptop-setup.md`의 붙여 넣을 템플릿)이거나 인용임
  - 문서만 바꿔서 빌드는 돌리지 않음
- 읽을 때 볼 곳:
  - 문체는 이제 두 갈래임. 사람용 문서는 개조식, 도구 설정(`CLAUDE.md`, skill)은 명령형 반말. 코드 주석과 화면·오류 문구는 ~합니다 체
  - 문체 규칙이 `CLAUDE.md`에 없음. 앞으로 새로 쓰는 문서가 다시 평서체로 돌아갈 수 있음
- 내가 채운 가정:
  - `TODO.md` "알려진 한계"의 굵은 이름 4개도 개조식으로 바꿈("시작 파일이 쌓인다" → "쌓임" 등). 다른 문서에서 이 이름을 인용한 곳은 없음
  - `TODO.md` "이것부터다"는 "이것부터임"이 어색해서 "이것부터 해야 함"으로 바꿈. 어미만 바꾸지 않은 유일한 곳
