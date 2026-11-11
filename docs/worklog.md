# 작업 기록

Claude Code가 단위 업무 하나를 끝낼 때마다 오늘 날짜 아래에 적음. /read-check 결과도 여기에 적음

<!--
## 2026.10.25

### <단위 업무 ID> <이름>
- 결정: (선택지·이유. 관리자 결정이면 날짜)
- 만든 파일:
- 고친 파일:
- 확인: (빌드·lint·실제로 눌러 본 것)
- 읽을 때 볼 곳:
- 내가 채운 가정:
해당 없는 칸은 지우지 말고 "없음"으로 적음

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
  - 회원: 이메일로 찾고 없으면 만든다(M-1-1 회원 등록을 대신함). `Member.password`는 nullable(구글 회원은 null). 회원 유형은 처음 만들 때만 정한다(2026.11.12 X-1에서 바뀜: 구글 로그인할 때마다 목록의 학생을 교수로 올림). 구글 `sub`는 저장하지 않는다.
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

### CLAUDE.md에 문서 문체 규칙 추가 (관리자 지시 "사람용 문서는 개조식 추가")
- 결정:
  - `CLAUDE.md`에 "문서 문체 (2026.10.30 관리자 결정)" 절을 "주석" 절 앞에 새로 둠. 사람용 문서는 개조식, 어미 ~함·~임·~있음·~됨, 줄 끝 마침표 없음
  - 예외도 같은 절에 적음: `CLAUDE.md`·skill(명령형 반말), 코드 주석(완결 문장), 화면 문구·오류 메시지(~합니다), 인용, 2026.10.29까지의 worklog 기록. 같은 날 문체를 바꿀 때 정한 범위와 같음
- 고친 파일: `CLAUDE.md`, `docs/worklog.md`(맨 위 안내 1줄과 템플릿의 "없음" 안내 1줄도 개조식으로)
- 확인: 문서만 바꿔서 빌드는 돌리지 않음
- 읽을 때 볼 곳: skill 본문은 그대로 명령형 반말임. skill이 worklog에 적으라고 할 때의 문체는 이 규칙을 따름
- 내가 채운 가정:
  - 규칙을 둔 위치("주석" 절 바로 앞)와 예외 목록은 내가 정함
  - worklog 맨 위 안내와 템플릿 줄은 "지난 기록"이 아니라 앞으로 쓸 양식이라서 개조식으로 바꿈

### ERD에 열마다 비고 추가 (관리자 지시 "각 값들이 무슨 용도인지 비고란")
- 결정:
  - Mermaid `erDiagram`의 열 설명 칸(큰따옴표 부분)을 비고로 씀. 그림에 표의 네 번째 칸으로 그려지므로 따로 표를 두지 않음
  - 6개 엔티티의 모든 열에 용도를 적음. 내용은 Entity 주석에서 가져옴. 이미 있던 비고("구글 회원은 null" 등)는 지우지 않고 앞뒤에 덧붙임
  - 평가 점수 열 6개의 비고는 `frontend/src/constants/peerEvaluation.js`의 `name`과 같게 적음
- 고친 파일: `docs/architecture.md`(ERD 블록, 아래 설명에 "오른쪽 칸은 비고" 1줄)
- 확인: Mermaid 11.4.1로 그려 봄. 문법 오류 없이 그려지고 비고 칸이 보임. 문서만 바꿔서 빌드는 돌리지 않음
- 읽을 때 볼 곳: Entity 필드를 바꾸면 ERD 비고도 같은 작업에서 고칠 것. 비고는 Entity 주석을 줄인 것이라 둘이 어긋나기 쉬움
- 내가 채운 가정:
  - `id`가 자동 번호라는 설명은 `@GeneratedValue(strategy = IDENTITY)`를 보고 ERD 아래 설명에 한 번만 적음
  - `comment`의 "최대 2000자"는 `@Column(length = 2000)`과 `EvaluationSubmitRequest`의 `@Size(max = 2000)`을 보고 적음
  - `endDate`는 화면(`AssignmentCreatePage.jsx`)과 `api-draft.md`가 쓰는 "종료일"로 맞춤

## 2026.11.04

### 범위를 "최대한 완성"으로 변경 (관리자 지시 "완성 일정을 고려하지 않고 최대한 완성")
- 결정:
  - 범위 기준을 일정에서 기능도기술서 v1.3 전체(기능 15개, 단위 업무 29개)로 바꿈. 코드와 대조하니 13개가 됐고 16개가 남음
  - 남은 단위의 규칙을 기술서에서 `TODO.md` 1절로 옮김. 저장소 밖 문서라 저장소만 봐도 알 수 있게 함
  - 구현 순서는 A-3-1을 맨 앞에 둠. 선택지: 기술서 번호 순(단계 검사를 나중에 모든 API에 덧붙여야 함), 개요서 순(같음), 단계 전환 먼저(새 API가 처음부터 단계 검사를 씀). 마지막을 고름
  - 규칙은 그 단위를 만드는 commit부터 적용함. 바로 적용하면 "코드가 어긋나면 코드가 틀린 것"에 따라 지금 코드가 틀린 것이 되고 rule-check가 매번 걸림
  - 기술서에 없는 기능은 제안으로만 적음(`TODO.md` 2절). 관리자가 고른 것만 구현 순서로 옮김
- 고친 파일: `TODO.md`, `CLAUDE.md`, `README.md`, `docs/api-draft.md`, `.claude/skills/rule-check/SKILL.md`, `.claude/skills/next-step/SKILL.md`
- 확인: 문서만 바꿔서 빌드는 돌리지 않음. 구현 순서의 단위 수(16개)와 API 표(19개)를 기술서와 대조함
- 읽을 때 볼 곳:
  - 이전 `TODO.md`와 기술서 v1.3이 어긋난 곳 2개를 기술서 쪽으로 맞춤. R-1-3은 "정해야 함"이었으나 기술서에 "파일 본문만 지움"으로 정해져 있음. T-2-2는 "엔티티는 그대로"였으나 기술서에 "이력 보관"이 있어 `RoleHistory`가 필요함
  - 기술서의 교수 판별("개발팀이 유형을 바꿈")은 2026.10.26 관리자 결정(교수 이메일 목록)과 다름. 뒤의 결정을 따르고 빈틈은 X-1로 적음
  - R-1-2는 "됨"으로 셌지만 기술서의 "역할별 최신순"과 달리 화면이 전체 최신순임. 1절 순서 3에 같이 넣음
  - 진척 시각화(R-4)와 금지 목록의 진척률은 충돌하지 않음. 기술서의 R-4는 업로드 시점만 표시함
  - 코드 주석과 `backend/README.md`, `docs/api-draft.md`의 "12.11 데모의 기본 로그인" 문구는 사실이 그대로라 고치지 않음
- 내가 채운 가정:
  - "명세"를 이 저장소의 문서로 봄. 기능도기술서·개요서 hwpx(저장소 밖)의 단계 표시는 고치지 않음
  - 12.11, 12.15, 11.16 날짜는 지우지 않고 범위 기준에서만 뺌
  - "데모"라는 말과 저장소 이름은 그대로 둠
  - "단위 업무 하나씩", "파일 8개가 넘으면 나눔", "그날 읽을 수 있는 양"은 일정과 무관한 기준으로 보고 유지함
  - 단계 위반 응답을 400 `INVALID_INPUT`으로 함. 새 `code`는 관리자에게 먼저 물어야 해서 만들지 않음
  - 엔티티 4개, API 주소 19개, 추가 기능 제안 전체는 추천안임

### 추가 기능 필수·권장을 완성 범위에 넣음 (관리자 선택 "필수·권장 넣어")
- 결정:
  - 필수 7개(O-1~O-5, X-1, X-2)와 권장 8개(X-3~X-7, O-6~O-8)를 `TODO.md` 1절로 옮김. 선택 3개(X-8~X-10)와 "명세 규칙을 바꿔야 하는 것"은 2절에 남김
  - 구현 순서를 21줄로 다시 짬. O-5·O-6을 맨 앞에 둠. 선택지: A-3-1 먼저(이후 단위 규칙이 테스트 없이 쌓임), 테스트 먼저(뒤 단위가 규칙 테스트를 같이 넣음). 뒤를 고름
  - O-1·O-2는 엔티티가 모두 나온 뒤에 둠. Flyway를 먼저 넣으면 엔티티를 바꿀 때마다 migration을 써야 해서 초심자가 읽을 양만 늘어남
  - X-7은 `ApiErrorResponse` 모양을 두고 `message`에 틀린 칸을 적음. 선택지: 필드 추가(규칙 변경, 화면이 칸별로 표시 가능), message에 적기(규칙 유지). 뒤를 고름. 3절 미정 항목 하나가 정해짐
  - X-1은 관리 화면 없이 구글 로그인 때 교수 목록을 다시 보고 올림. 선택지: 관리 API(권한 설계 필요), DB 직접 수정(운영 절차 필요), 로그인 때 재확인(코드 몇 줄). 마지막을 고름
- 고친 파일: `TODO.md`, `CLAUDE.md`, `README.md`, `docs/api-draft.md`
- 확인: 문서만 바꿔서 빌드는 돌리지 않음
- 읽을 때 볼 곳:
  - X-3은 "등록한 과제는 고칠 수 없음"과 맞닿음. 과제가 아니라 조 참여의 `leader`만 바꾸므로 규칙을 어기지 않는다고 봄
  - X-1을 만들면 2026.10.26 결정 "처음 만들 때만 정함"이 바뀜. 관리자가 X-1을 고른 것으로 보고 그 단위에서 고침
- 내가 채운 가정:
  - 각 추가 기능의 세부 규칙과 API 주소(`TODO.md` 1절 "추가 기능의 규칙")는 전부 추천안임
  - 개인정보 고지 문구와 보관 기간은 추천안으로 정하지 않고 3절로 보냄. 학교·담당 교수 확인이 필요한 일임

### O-5 테스트 분리와 보강
- 결정:
  - 테스트 데이터베이스를 같은 PostgreSQL의 `footprint_test` 스키마로 분리함. 선택지: H2(Docker 없이 돌지만 PostgreSQL과 동작이 달라 unique 제약·타입 차이를 놓침), Testcontainers(깨끗하지만 새 의존성과 컨테이너 기동 시간), 별도 데이터베이스(이미 있는 volume에는 init 스크립트가 돌지 않아 조원마다 준비 작업이 생김), 전용 스키마(의존성·준비 작업 없음). 마지막을 고름
  - 새 의존성 없음. `spring-boot-starter-test`에 MockMvc, JsonPath가 들어 있음
  - 규칙 테스트는 시작 데이터로 실제 요청을 보내는 통합 테스트로 함. 로그인도 `POST /auth/login`을 거침. 필터, 권한 검사, 오류 응답이 모두 지나가게 하려는 것임
  - 테스트 클래스에 `@Transactional`을 붙여 끝나면 되돌림. 선택지: 테스트마다 데이터 새로 넣기(느리고 길어짐), 롤백(짧음). 뒤를 고름
  - 공통 부모 클래스 금지 규칙에 맞춰 도우미 `TestApi`를 상속 없이 필드로 씀
- 만든 파일: `backend/src/test/resources/application-test.yml`, `TestApi`, `AccessRuleTests`, `TeamRuleTests`, `DeliverableRuleTests`, `EvaluationRuleTests`
- 고친 파일: `BackendApplicationTests`, `StorageServiceTests`, `backend/README.md`, `README.md`, `TODO.md`, `CLAUDE.md`
- 확인:
  - `gradlew build` 성공. 테스트 3개 → 33개, 실패 0
  - 개발 데이터: `public.member`에 표시용 행을 넣고 빌드한 뒤에도 남아 있음을 확인하고 지움. `public`과 `footprint_test`에 테이블이 6개씩 있음
  - 테스트가 규칙을 실제로 잡는지: `checkTeamAccess`와 평가 대상 검사를 일부러 깨면 3개가 실패함. 되돌린 뒤 다시 통과함
  - 화면과 가짜 API는 바꾸지 않아 `npm run build`, `lint`는 돌리지 않음
  - rule-check: 테스트 폴더에서 금지 패턴 없음. `@Autowired`는 테스트 생성자 주입에만 있음
- 읽을 때 볼 곳:
  - `application-test.yml`의 `default_schema`, `create_namespaces`. 이 두 줄이 분리의 전부임
  - `@Transactional` 테스트는 요청과 테스트가 한 트랜잭션임. 실제 서버처럼 요청마다 commit 되지 않으므로 동시 요청이나 commit 시점 오류는 잡지 못함
  - `BackendApplicationTests`, `StorageServiceTests`의 `@AutoConfigureMockMvc`는 쓰려는 게 아니라 설정을 맞추려는 것임. 설정이 다르면 서버가 두 번 올라가 시작 데이터가 다시 들어감
  - A-3-1을 만들면 `EvaluationRuleTests`의 제출 테스트가 평가 단계 전제로 바뀜
- 내가 채운 가정:
  - 지금 규칙 중 무엇을 테스트할지는 내가 고름. CLAUDE.md "업무 규칙"의 접근 범위, 합류, 역할, 업로드, 평가 규칙을 기준으로 함
  - 20MB 제한과 동시 요청은 뺌(`TODO.md` 5절)

### O-6 CI
- 결정:
  - GitHub Actions를 씀. 백엔드와 화면을 job 2개로 나눔. 한쪽이 실패해도 다른 쪽 결과를 볼 수 있음
  - PostgreSQL, MinIO는 `infra`의 `docker compose up -d --wait`로 켬. 선택지: Actions의 `services`(이미지와 비밀번호를 파일 두 곳에 적어야 하고, MinIO에 필요한 `server /data` 명령을 넘길 수 없음), compose(내 컴퓨터와 같은 파일). 뒤를 고름
  - 실행 조건은 main push와 PR임. 모든 push로 두면 PR 브랜치에서 같은 검사가 두 번 돎
- 만든 파일: `.github/workflows/ci.yml`
- 고친 파일: `TODO.md`, `README.md`
- 확인:
  - **실제 실행은 확인하지 못함.** push 전에는 GitHub에서 돌지 않음
  - YAML 문법과 job 구조만 파서로 확인함. 같은 명령(`docker compose up -d --wait`, `gradlew build`, `npm run build`, `npm run lint`)은 내 컴퓨터에서 돌림. `npm ci`는 돌리지 않음
- 읽을 때 볼 곳:
  - `permissions: contents: read`는 이 workflow가 저장소를 고치지 못하게 함
  - Linux에서 돌아서 `backend/gradlew`의 실행 권한(`100755`)이 필요함. 지금은 맞음
- 내가 채운 가정:
  - action 버전은 major(`@v4`)로 고정함. 공개 저장소에서 쓰는 GitHub 공식 action이라 commit hash까지 고정하지 않음

### A-3-1 평가 시작
- 결정:
  - 단계 검사를 `service/AssignmentPhaseChecker`로 모음. 선택지: `TeamAccessChecker`에 더하기(접근 범위와 업무 규칙이 섞임), Service마다 if 쓰기(문구가 흩어짐), 전용 클래스. 마지막을 고름
  - 검사 순서는 권한 → 단계임. 권한 없는 사람에게 과제 단계를 알려 줄 이유가 없음
  - 이미 평가 단계인데 다시 시작하면 400 `INVALID_INPUT`임. 선택지: 409 `DUPLICATE`(이미 있는 것을 또 만드는 요청에 씀), 200(조용히 성공하면 시작 시각이 언제인지 헷갈림), 400(`TODO.md`의 "단계에 맞지 않는 요청"). 마지막을 고름
  - 이미 그 조의 조원이 합류를 다시 누르면 평가 단계에서도 200임. 아무것도 바꾸지 않는 요청이라 막을 이유가 없고 링크를 두 번 눌러도 결과가 같다는 지금 규칙을 지킴
  - `GET /teams/{teamId}` 응답에 `phase`를 더함. 조·산출물·평가 화면이 과제 응답을 따로 부르지 않고 단계를 알 수 있음
  - 시작 데이터에 평가 단계 과제를 더함(`demo-evaluation-1`, 조원 3명, 산출물 1건). 기록 단계 과제 쪽 데이터는 그대로임
- 만든 파일: `entity/AssignmentPhase`, `service/AssignmentPhaseChecker`, `PhaseRuleTests`, `frontend/src/constants/assignmentPhases.js`, `frontend/src/components/StartEvaluationPanel.jsx`, `frontend/mock/files/seed-3.txt`
- 고친 파일:
  - 백엔드: `entity/Assignment`, `dto/AssignmentResponse`, `dto/AssignmentDetailResponse`, `dto/TeamResponse`, `service/AssignmentService`, `service/TeamService`, `service/DeliverableService`, `service/EvaluationService`, `controller/AssignmentController`, `config/DataInitializer`, `EvaluationRuleTests`
  - 가짜 API: `frontend/mock/middleware.cjs`, `frontend/db.json`
  - 화면: `api/assignments.js`, `components/AssignmentList.jsx`, `pages/AssignmentDetailPage.jsx`, `pages/TeamPage.jsx`, `pages/DeliverablesPage.jsx`, `pages/EvaluationPage.jsx`
  - 문서: `docs/api-draft.md`, `docs/architecture.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`, `frontend/README.md`
- 확인:
  - `gradlew build` 성공. 테스트 41개, 실패 0. `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 34개, 확인 62개, 실패 0
  - 실제 백엔드와 가짜 API의 과제·조 응답 필드 이름이 같음을 비교함
  - 브라우저에서 실제 백엔드로 확인: 교수의 평가 시작 → 단계 꼬리표와 시각 표시, 패널 사라짐. 조장의 기록 단계 조 화면(초대 패널 있음), 평가 단계 조 화면(안내만), 평가 단계 산출물 화면(올리기 없음, 다운로드 있음), 기록 단계 평가 화면(안내만), 평가 단계 평가 화면(입력 폼)
  - 평가 시작 전 확인 창은 눌러 보지 못함. 시험할 때 `window.confirm`을 바꿔 문구만 확인함
  - 화면 확인에서 평가 시작 시각이 마이크로초까지 나오는 것을 찾아 고침. 다른 기록 시각처럼 Entity에서 초까지 자르고 테스트를 더함
  - rule-check: 금지 패턴 없음. 점수 계산 없음. 새 public 메서드에 주석 있음. `.claude/launch.json`에 실제 백엔드 항목을 더함(빌드한 jar를 `java -jar`로 켬)
- 읽을 때 볼 곳:
  - `Assignment.startEvaluation`은 단계와 시각만 바꿈. 누가 불러도 되는지와 이미 시작했는지는 `AssignmentService.startEvaluation`이 봄
  - `TeamService.join`의 단계 검사 위치. 이미 조원인 경우를 돌려보낸 뒤에 있어야 평가 단계의 재합류가 200임
  - 평가 시작과 업로드가 같은 순간에 오면 먼저 읽은 단계로 처리됨. 잠금을 두지 않음. 교수가 시작하는 순간에 올라온 파일 한 건이 기록으로 남는 정도라 단순함을 고름
  - 같은 순간의 평가 시작 두 요청은 나중 요청의 시각이 남음. 화면은 시작 중 버튼을 막음
- 내가 채운 가정:
  - 평가 시작 전 확인은 브라우저 기본 확인 창(`window.confirm`)으로 함. 문구도 내가 씀
  - 화면에는 단계 꼬리표와 평가 시작 시각을 보임. 조·산출물·평가 화면의 안내 문구도 내가 씀
  - 평가 단계에서 조장의 초대 패널은 숨김. 서버의 초대 코드 조회는 열람이라 그대로 됨
  - 평가 단계 시작 데이터의 과제 이름과 기간, 역할, 파일 내용은 내가 정함
  - 가짜 API의 평가 시작 시각 시작값은 고정값이고 실제 백엔드는 서버를 켠 시각임

### T-2-2 역할 변경과 이력 보관
- 결정:
  - 변경은 `PUT .../role`로 따로 둠. `POST .../role`(첫 지정)의 409는 그대로임. `TODO.md`에 적어 둔 추천안임
  - 역할이 없는 조원에게 `PUT`하면 400 `INVALID_INPUT`임. 선택지: 그대로 지정해 주기(첫 지정이 두 길이 되고 이전 역할이 없는 이력을 어떻게 할지 정해야 함), 404(조원은 있음). 첫 지정은 POST 하나로 둠
  - 지금과 같은 역할로 바꾸면 400 `INVALID_INPUT`임. 선택지: 200으로 이력을 안 남김(조장이 바꾼 줄 알게 됨), 이력을 남김(바뀌지 않은 일이 기록으로 쌓임). 비교는 앞뒤 공백을 지운 값으로 함
  - `RoleHistory`는 조 참여, 이전 역할명, 바뀐 시각만 가짐(`TODO.md` 추천안 그대로). 새 역할명은 저장하지 않음. 최신 줄의 다음 역할은 조원의 지금 역할이고, 나머지는 바로 뒤에 바뀐 줄의 `previousRoleName`이라 다시 알 수 있음
  - 이력 응답은 `{ id, previousRoleName, changedAt }`이고 최근 것부터임. 산출물 목록과 같은 순서임
  - 이력 조회 권한은 조 기록과 같게 `checkTeamAccess`임. 평가 단계에서도 열람은 됨
  - 역할을 바꾸면 `roleAssignedAt`도 새 시각이 됨. `TeamMember.assignRole`을 그대로 씀. 메서드를 둘로 나눠도 하는 일이 같음
  - 화면은 조장에게 줄마다 입력 칸을 항상 보임. 역할이 없으면 "지정", 있으면 "변경". 선택지: 변경 버튼을 눌러야 칸이 열림(줄마다 열림 state가 더 필요함). 이력은 한 번에 한 명만 아래 패널로 보임
- 만든 파일: `entity/RoleHistory`, `repository/RoleHistoryRepository`, `dto/RoleChangeRequest`, `dto/RoleHistoryResponse`, `RoleChangeRuleTests`, `frontend/src/components/RoleHistoryPanel.jsx`
- 고친 파일:
  - 백엔드: `service/TeamService`, `controller/TeamController`, `entity/TeamMember`, 테스트 `TestApi`, `PhaseRuleTests`
  - 가짜 API: `frontend/mock/middleware.cjs`, `frontend/db.json`
  - 화면: `api/teams.js`, `pages/TeamPage.jsx`, `components/TeamMemberList.jsx`, `components/RoleAssignForm.jsx`, `components/StartEvaluationPanel.jsx`, `index.css`
  - 문서: `docs/api-draft.md`, `docs/architecture.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`
- 고친 기존 문제:
  - `.end`가 `.row`와 함께 붙으면 오른쪽으로 밀리지 않았음. `.row`가 CSS에서 뒤에 있고 `margin`을 정하기 때문임. `.row.end`를 더함. 역할 지정 form도 같은 문제였음
  - 조 화면의 오류 문구가 초대 패널 아래에 있어 입력 칸에서 멀었음. 조원 목록 바로 아래로 옮김
  - `TODO.md`의 "13개는 됐고 16개가 남음"은 A-3-1에서 고치지 못한 값이었음. README의 14개와 어긋났음. 이번에 둘 다 15개로 맞춤
  - Postman 안내의 `seed-1.txt`, `seed-2.txt`는 A-3-1에서 `seed-3.txt`가 생긴 뒤 틀린 글이었음. `seed-`로 시작하는 파일로 고침
- 확인:
  - `gradlew build` 성공. 테스트 49개, 실패 0(`RoleChangeRuleTests` 8개 새로). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 39개, 확인 73개, 실패 0
  - 실제 백엔드와 가짜 API의 `PUT`, 이력 응답 필드와 오류 메시지가 같음을 비교함
  - 브라우저에서 실제 백엔드로 확인: 조장의 역할 변경 → 조원 목록과 열린 이력 패널이 바로 바뀜. 같은 역할은 오류 문구가 보임. 역할을 바꾼 이서연의 시작 산출물은 "백엔드 1번째"로 남음. 조원은 이력만 보임. 교수는 평가 단계 조에서 이력 버튼만 보이고 안내 문구가 보임. 375px 폭에서 가로 스크롤 없음
  - rule-check: 금지 패턴 없음. 점수 계산 없음. 새 클래스와 public 메서드에 주석 있음. 값을 바꾸는 `changeRole`에 `@Transactional` 있음
- 읽을 때 볼 곳:
  - `TeamService.changeRole`의 순서. 이력을 먼저 저장해야 이전 역할명이 남음. 이력과 역할 변경은 한 트랜잭션이라 둘 다 되거나 둘 다 안 됨
  - `findRoleHistories`에는 `@Transactional`이 없음. 값을 바꾸지 않기 때문임
  - `TeamPage`의 `handleSaveRole`은 역할 유무로 `assignRole`과 `changeRole` 중 하나를 고름. 함수도 값이라 변수에 담을 수 있음
  - 저장 중에는 버튼과 입력 칸을 막음. 막지 않으면 `.then` 안의 옛 `members`, `roleNameInputs`가 그 사이의 변경을 덮어씀
  - 같은 조원을 두 요청이 같은 순간에 바꾸면 둘 다 같은 이전 역할을 이력에 남길 수 있음. 잠금을 두지 않음. 조장 한 명이 하는 일이고 화면은 저장 중 버튼을 막음
- 내가 채운 가정:
  - 조장은 자기 역할도 바꿀 수 있음. 첫 지정이 그랬기 때문임
  - 오류 메시지 "아직 역할이 지정되지 않은 조원입니다", "지금과 같은 역할입니다"와 화면 문구("이력", "변경", "역할이 바뀐 적이 없습니다", "…까지")는 내가 씀
  - 이력 버튼은 역할이 있는 조원에게만 보임. 역할이 없으면 바뀐 적도 없음
  - 시작 데이터와 `frontend/db.json`에 역할 이력을 넣지 않음. 처음에는 모두 "바뀐 적이 없습니다"임

### 프론트 시안 검토 결정
- 결정:
  - `GET /teams/{teamId}/records`를 조원에게도 엶. 권한은 "조원, 교수"이고 조원은 자기 조만 봄. 이유: 산출물 목록만 봐도 누가 많이 올렸는지 이미 보임. 선택지: 교수 전용 유지(조원끼리 활동량 비교가 덜 쉬워지지만 산출물 목록으로 어차피 비교됨)
  - 조원 화면에도 조원별 보기를 둠. V-1 규칙과 API 하나로 같은 화면을 씀
  - 왼쪽 과제 레일은 유지함
  - 완료확인의 뜻을 설명하는 문구는 넣지 않음
  - 메뉴 이름 "진척"은 유지함. 비율 없이 업로드 시점만 보여 주므로 진척률 금지에 걸리지 않음
- 고친 파일: `TODO.md`, `docs/api-draft.md`, `README.md`. V-1-1은 아직 만들지 않아 코드 변경은 없음
- 확인: 문서만 고침. 코드 빌드는 돌리지 않음
- 읽을 때 볼 곳:
  - 기능도기술서 v1.3의 V-1은 교수 기능임. `TODO.md` 1절이 기술서보다 이기므로 이 절을 고침
  - 근거 패널 API는 "조원(평가 단계), 교수"로 그대로임. 다만 조원은 기록 단계에서도 전체 기록으로 같은 기록을 볼 수 있음
- 내가 채운 가정:
  - 전체 기록에 평가 제출과 임시 저장을 넣지 않음. 조원끼리 서로의 평가를 볼 수 없다는 규칙 때문임
  - 조원용 메뉴 이름은 "전체 기록"임

### X-3 조장 교체
- 결정:
  - 주소는 `PUT /teams/{teamId}/leader`이고 body는 `{ teamMemberId }`임(`api-draft.md` 추천안). 응답은 새 조장 한 줄임. 선택지: 조원 목록 전체(두 줄이 바뀌어서 맞지만 역할 API와 모양이 달라짐). 화면은 `leader`를 그 번호로 다시 계산함
  - 교체 요청은 조 행을 잠그고(`TeamRepository.findWithLockById`, `PESSIMISTIC_WRITE`) 처리함. 다른 동시 요청은 잠그지 않았지만 여기는 결과가 다름. 조장이 둘이 되면 그 조의 학생은 과제 상세가 500이 되고, 조장 교체도 500이라 화면에서 고칠 길이 없음. 선택지: 잠금 없음(아래 "확인"에서 첫 라운드에 재현됨), DB 부분 unique 인덱스(`WHERE leader`. JPA 어노테이션으로 못 적어서 O-1 Flyway 전에는 둘 곳이 없음). O-1 때 인덱스를 더할지 다시 볼 수 있음
  - 이미 조장인 조원을 고르면 400 `INVALID_INPUT`임. 역할 변경의 "같은 역할"과 같은 이유임
  - 검사 순서는 교수 → 단계 → 대상임. 권한 → 단계 순서는 그대로임
  - 교수 검사는 `TeamAccessChecker`에 새 메서드를 만들지 않고 `isProfessorOf`로 Service에서 함. `AssignmentService.startEvaluation`과 같은 모양이고 메시지가 달라서임
  - 이력 응답은 `{ id, previousLeaderName, newLeaderName, changedAt }`임. 목록 응답은 연결된 데이터를 펼치는 규칙을 따름
  - 초대 코드는 교체해도 그대로 둠. 새 조장이 필요하면 다시 발급함. 조원에게 이미 보낸 링크가 갑자기 끊기지 않게 함
  - 화면은 조 화면의 조원 줄에 "새 조장으로" 버튼을 둠. 교수가 조원을 보는 곳이 여기뿐임. 조장 교체 이력은 한 번이라도 바뀐 조에서만 패널로 보임
- 만든 파일: `entity/LeaderHistory`, `repository/LeaderHistoryRepository`, `dto/LeaderChangeRequest`, `dto/LeaderHistoryResponse`, `LeaderChangeRuleTests`, `frontend/src/components/LeaderHistoryPanel.jsx`
- 고친 파일:
  - 백엔드: `service/TeamService`, `service/TeamAccessChecker`(주석), `controller/TeamController`, `entity/TeamMember`, `repository/TeamRepository`, 테스트 `PhaseRuleTests`
  - 가짜 API: `frontend/mock/middleware.cjs`, `frontend/db.json`
  - 화면: `api/teams.js`, `pages/TeamPage.jsx`, `components/TeamMemberList.jsx`, `components/StartEvaluationPanel.jsx`
  - 문서: `docs/api-draft.md`, `docs/architecture.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`
- 고친 기존 문제:
  - README "아직 만들지 않은 것"의 추가 기능 15개는 O-5, O-6을 끝낸 뒤에도 그대로였고 테스트, CI까지 적혀 있었음. O-5, O-6, X-3을 뺀 12개로 고침
- 확인:
  - `gradlew build` 성공. 테스트 58개, 실패 0(`LeaderChangeRuleTests` 9개 새로). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 46개, 확인 88개, 실패 0
  - 실제 백엔드와 가짜 API의 `PUT`, 이력 응답 필드와 오류 메시지 6가지가 같음을 비교함
  - 동시 요청 시험(서로 다른 조원으로 바꾸는 `PUT` 두 개를 동시에 보냄, 저장소에 넣지 않은 임시 스크립트): 잠금이 있으면 40라운드 모두 조장 한 명. `@Lock`을 잠시 뺀 jar에서는 첫 라운드에 조장 둘이 됨. 그때 그 조 학생의 과제 상세 500, 교수의 과제 상세에 같은 조 두 줄, 조장 교체 500. 시험 뒤 `@Lock`을 되돌리고 다시 빌드해 40라운드를 또 확인함. 이 시험은 `gradlew build`에 들어 있지 않음. 규칙 테스트는 `@Transactional`이라 동시 요청을 만들 수 없음
  - 브라우저에서 실제 백엔드로 확인: 교수가 교체 → 조장 표시와 이력 패널이 바로 바뀜. 새 조장에게 역할 form과 초대 패널이 보임. 이전 조장은 이력 버튼만 보임. 평가 단계 조의 교수에게는 교체 버튼이 없고 안내 문구가 바뀜. 375px 폭에서 가로 스크롤 없음
  - 교체 전 확인 창은 눌러 보지 못함. 시험할 때 `window.confirm`을 바꿔 문구만 확인함
  - rule-check: 금지 패턴 없음. 점수 계산 없음. 새 클래스와 public 메서드에 주석 있음. 값을 바꾸는 `changeLeader`에 `@Transactional` 있음
- 읽을 때 볼 곳:
  - `TeamService.changeLeader`의 첫 줄. `findTeam`이 아니라 `findWithLockById`인 이유. `@Lock`은 트랜잭션 안에서만 동작하고, 커밋할 때 풀림
  - `findWithLockById`는 이름에서 "WithLock"을 Spring Data가 무시함. `find`와 `By` 사이 글자는 조건이 아님
  - `LeaderHistory`가 `TeamMember`를 두 번 가리켜서 `@JoinColumn` 이름을 따로 정함
  - `TeamPage.handleChangeLeader`의 `members.map`. 응답이 한 줄이라도 이전 조장의 표시까지 함께 바뀜
  - 조장이 조 화면을 열어 둔 채 교체되면 그 화면에는 조장 버튼이 남음. 누르면 서버가 403으로 막고 오류 문구가 보임. 다시 열면 사라짐
- 내가 채운 가정:
  - 역할이 없는 조원도 조장이 될 수 있음. 규칙에 역할 조건이 없음
  - 같은 사람으로 되돌리는 교체도 막지 않음. 되돌린 것도 이력에 남음
  - 교체 전 확인은 `window.confirm`으로 함. 문구와 "새 조장으로", "조장 교체 이력", 오류 메시지는 내가 씀
  - 시작 데이터와 `frontend/db.json`에 조장 교체 이력을 넣지 않음

### R-1-3 산출물 삭제, O-7 주인 없는 파일 정리

- 결정:
  - `DELETE /deliverables/{deliverableId}`를 둠. 응답은 200과 삭제 시각이 담긴 산출물임. 선택지: 204. 행이 남고 화면이 그 줄을 바로 바꿔야 해서 200으로 함
  - 검사 순서는 없음 404 → 올린 조원 아니면 403 → 평가 단계 400 → 이미 삭제 400임. 다른 조 학생에게 삭제 여부가 보이지 않게 권한을 먼저 봄
  - 삭제된 산출물의 다운로드는 404임. 권한이 없으면 403이 먼저임
  - 응답에 `uploaderMemberId`를 더함. 화면이 내 산출물에만 삭제 버튼을 보여 주는 데 씀. 이름은 겹칠 수 있어서 쓰지 않음
  - 삭제는 "데이터베이스 반영(flush) → 파일 삭제 → 커밋" 순서임. 선택지: 커밋 뒤 파일 삭제. 파일 삭제가 실패하면 삭제됨으로 표시됐는데 파일이 남음. 지금 순서는 파일 삭제가 실패하면 행도 되돌아감
  - O-7은 `saveAndFlush`의 오류를 `catch`해서 파일을 지움. 선택지: `TransactionSynchronization`으로 커밋 실패까지 처리. 초심자가 따라 읽기 어려운 숨은 동작이 늘어서 쓰지 않음. 커밋 단계 실패는 `TODO.md` 5절 "알려진 한계"에 남김
  - 역할 묶음 순서는 가나다순임. 선택지: 많이 올린 순, 최근 순. 역할끼리 비교하는 순위처럼 보이고 위치가 흔들려서 쓰지 않음. 묶음 안은 최신순임
  - 순번은 삭제된 산출물도 셈(규칙: 순번 유지)
  - O-7은 가짜 API에 넣지 않음. 가짜 API에서는 저장 실패가 나지 않음
- 만든 파일: `DeliverableDeleteRuleTests`, `DeliverableOrphanFileTests`
- 고친 파일:
  - 백엔드: `entity/Deliverable`, `dto/DeliverableResponse`, `service/DeliverableService`, `service/StorageService`, `service/TeamAccessChecker`, `controller/DeliverableController`, 테스트 `PhaseRuleTests`
  - 가짜 API: `frontend/mock/middleware.cjs`, `frontend/db.json`
  - 화면: `api/deliverables.js`, `pages/DeliverablesPage.jsx`, `components/DeliverableList.jsx`, `components/StartEvaluationPanel.jsx`, `index.css`(`h3`)
  - 문서: `docs/api-draft.md`, `docs/architecture.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`, `frontend/README.md`
- 고친 기존 문제:
  - `frontend/README.md`가 시작 파일을 `seed-1.txt`, `seed-2.txt`만 적고 있었음. `seed-3.txt`도 있어서 고침
- 확인:
  - `gradlew clean build` 성공. 테스트 67개, 실패 0(`DeliverableDeleteRuleTests` 7개, `DeliverableOrphanFileTests` 2개 새로, `PhaseRuleTests`의 기존 테스트 2개에 삭제 확인을 더함). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 52개, 확인 101개, 실패 0
  - 실제 백엔드와 가짜 API의 업로드, 삭제, 다시 삭제, 삭제 후 다운로드, 없는 번호, 잘못된 번호 응답과 오류 메시지가 같음을 비교함
  - 브라우저에서 실제 백엔드로 확인: 조원에게 내 산출물에만 삭제 버튼이 보임. 취소하면 그대로이고, 확인하면 그 줄이 "삭제됨"과 삭제 시각으로 바뀌고 다운로드 버튼이 사라짐. 교수에게는 삭제 버튼이 없고 삭제된 줄은 보임. 평가 단계 조에서는 삭제 버튼이 없고 안내 문구가 보임. 폭 375px에서 가로 스크롤 없음
  - 삭제 확인 창은 눌러 보지 못함. 시험할 때 `window.confirm`을 바꿔 문구와 취소, 확인 동작을 확인함
  - 파일 삭제가 실패할 때 행이 되돌아가는지는 시험하지 않음. 테스트 트랜잭션 안에서는 커밋과 롤백을 재현할 수 없음
  - rule-check: 금지 패턴 없음. 점수 계산 없음. 새 public 메서드에 주석 있음. 값을 바꾸는 `delete`에 `@Transactional` 있음. 수정 필요 없음
- 읽을 때 볼 곳:
  - `DeliverableService.upload`의 `saveAndFlush`. `save`만 쓰면 INSERT가 커밋 때까지 미뤄질 수 있어서 `catch`가 오류를 받지 못함
  - `DeliverableService.delete`의 `flush`. 파일은 되돌릴 수 없어서 데이터베이스 오류를 파일 삭제 전에 먼저 드러냄
  - `deleteOrphanFile`의 `addSuppressed`. 지우기까지 실패해도 사용자에게 나가는 오류는 원래 오류임
  - `DeliverableOrphanFileTests`의 `mock`, `spy`. 이 프로젝트에서 처음 씀. 실제 요청으로는 저장 실패를 만들 수 없어서 씀. Mockito는 `spring-boot-starter-test`에 들어 있어 새 의존성이 아님
  - `DeliverableDeleteRuleTests`가 시작 산출물을 지우지 않는 이유. MinIO 삭제는 테스트가 끝나도 되돌아가지 않음
  - `DeliverableList`의 `sequenceOf`. 삭제된 줄을 빼지 않고 세어야 순번이 끊기지 않음
- 내가 채운 가정:
  - 다시 삭제하면 400 `INVALID_INPUT`, 삭제된 산출물 다운로드는 404 `NOT_FOUND`임
  - 파일 삭제 뒤에도 행의 `storageKey` 값은 남김. 지워도 쓸 곳이 없고, 기록을 고치지 않는 쪽을 고름
  - 같은 산출물을 동시에 삭제하는 두 요청은 잠그지 않음. 올린 사람 한 명의 일이고 화면이 버튼을 막음
  - 삭제 확인은 `window.confirm`으로 함. 확인 문구, 안내 문구, 오류 메시지, 화면 제목 "올린 산출물 (역할별 최신순)"은 내가 씀
  - 시작 데이터와 `frontend/db.json`에 삭제된 산출물을 넣지 않음

### R-2-1 기여 항목 등록, R-2-2 기여 항목 조회

- 문서 충돌: `docs/api-draft.md`의 추가 예정 표는 `GET /teams/{teamId}/contributions`를 "조원"만으로 적음. `CLAUDE.md` 접근 범위는 "조원 또는 과제를 만든 교수"이고, R-3-3과 V-1, E-1도 교수에게 열려 있음. 기준 문서 순서대로 교수도 보게 하고 api-draft를 고침. 시작 전에 관리자에게 알림
- 결정:
  - R-2 한 줄이 파일 8개를 넘어서 `TODO.md` 규칙대로 commit을 셋으로 나눔. 등록과 조회는 서로 없으면 확인이 안 돼서 한 commit임
  - 상태 열을 두지 않음. `canceledAt`, `reportedAt`이 있는지로 정함. 선택지: `status` 열과 응답 필드. 시각과 어긋날 수 있고 `deletedAt`의 패턴과 달라져서 쓰지 않음. 화면의 상태 이름은 `ContributionList.statusOf`가 계산함
  - 담당은 요청에서 조 참여 번호(`teamMemberId`)로 받음. 조장 교체와 같음. 응답은 회원 번호와 이름으로 펼침
  - 항목명은 `@Size(max = 255)`임. 역할명과 같음
  - 화면은 산출물처럼 따로 둠(`/teams/{teamId}/contributions`). 조 화면에 넣으면 조원, 역할, 이력과 섞여 길어짐
  - 시작 데이터에 기여 항목 2건을 넣음. 1조 1건, 평가 단계 조 1건. 평가 단계 화면은 항목을 새로 등록할 수 없어서 시작 데이터가 있어야 볼 수 있음
- 만든 파일:
  - 백엔드: `entity/Contribution`, `repository/ContributionRepository`, `service/ContributionService`, `controller/ContributionController`, `dto/ContributionCreateRequest`, `dto/ContributionResponse`, `ContributionRuleTests`
  - 화면: `api/contributions.js`, `pages/ContributionsPage.jsx`, `components/ContributionCreateForm.jsx`, `components/ContributionList.jsx`
- 고친 파일:
  - 백엔드: `config/DataInitializer`
  - 가짜 API: `frontend/mock/middleware.cjs`, `frontend/db.json`
  - 화면: `App.jsx`, `pages/TeamPage.jsx`, `components/StartEvaluationPanel.jsx`, `index.css`(`select`)
  - 문서: `docs/api-draft.md`, `docs/architecture.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`
- 고친 기존 문제:
  - `docs/api-draft.md` "이미 있는 API의 응답이 바뀌는 곳"에 R-1-3 때 끝난 `deletedAt` 줄이 남아 있어서 지움
- 확인:
  - `gradlew build` 성공. 테스트 74개, 실패 0(`ContributionRuleTests` 7개 새로). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 59개, 확인 116개, 실패 0
  - 실제 백엔드와 가짜 API에 같은 요청 15개를 보내 응답과 오류 메시지가 같음을 비교함(저장소에 넣지 않은 임시 스크립트)
  - 브라우저에서 실제 백엔드로 확인: 조 화면 링크로 들어감. 항목명, 담당이 빠지면 문구가 나옴. 등록하면 목록 맨 위에 "보고 전"으로 나오고 칸이 비워짐
  - 화면 확인에서 내 실수를 찾아 고침: `api/contributions.js`가 body를 `JSON.stringify`로 바꿨는데 `client.js`도 다시 바꿔서 400이 남. Newman과 비교 스크립트는 `client.js`를 거치지 않아 잡지 못함
  - rule-check: 금지 패턴 없음. 점수 계산 없음. 새 클래스와 public 메서드에 주석 있음. 값을 바꾸는 `create`에 `@Transactional` 있음. 수정 필요 없음
- 읽을 때 볼 곳:
  - `Contribution`에 상태 열이 없는 이유(클래스 주석)와 `ContributionList.statusOf`
  - `ContributionService.create`의 `filter`. 번호가 있어도 다른 조의 조원이면 404임
  - `ContributionsPage`의 `assigneeId`. select의 값은 글자라서 보낼 때 `Number`로 바꿈
- 내가 채운 가정:
  - 담당이 이 조의 조원이 아니면 404임. 조장 교체와 같음
  - 시작 기여 항목의 이름(`회의록 정리`, `자료 취합`)과 담당
  - 화면 문구, "기여 항목 (최근 등록순)" 제목, 등록 form의 안내 문구는 내가 씀

### R-2-3 기여 항목 완료 보고

- 결정:
  - `POST /contributions/{contributionId}/report`를 둠. `docs/api-draft.md` 예정 표의 주소 그대로임
  - 검사 순서는 입력 400 → 없음 404 → 담당 아니면 403 → 평가 단계 400 → 취소됨 400 → 이미 보고 409임
  - 다시 보고하면 409 `DUPLICATE`임. 선택지: 400. "역할 다시 지정", "동료평가 다시 제출"이 409라서 같은 일은 같은 code로 둠
  - 항목 행을 잠금(`findWithLockById`). 선택지: 잠그지 않음. 겹치는 두 요청이 모두 보고 전으로 읽으면 뒤의 메모가 앞의 메모를 덮어써 "보고한 뒤 고칠 수 없음"이 깨짐. 조장 교체와 같은 패턴임
  - 메모는 2000자까지임. 동료평가 의견과 같음
  - 보고 form은 그 줄 안에 열림. 선택지: 별도 화면. 줄이 몇 개 안 되고 입력 칸 하나뿐임
  - 보고 전 확인 창을 두지 않음. form을 여는 단계와 "고칠 수 없습니다" 문구가 이미 있음. 삭제와 달리 잃는 것이 없음
- 만든 파일: `dto/ContributionReportRequest`, `components/ContributionReportForm.jsx`
- 고친 파일:
  - 백엔드: `entity/Contribution`, `repository/ContributionRepository`, `service/ContributionService`, `service/TeamAccessChecker`, `controller/ContributionController`, `config/DataInitializer`, 테스트 `ContributionRuleTests`
  - 가짜 API: `frontend/mock/middleware.cjs`, `frontend/db.json`
  - 화면: `api/contributions.js`, `pages/ContributionsPage.jsx`, `components/ContributionList.jsx`, `components/StartEvaluationPanel.jsx`, `index.css`(`.item-full`)
  - 문서: `docs/api-draft.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`
- 확인:
  - `gradlew build` 성공. 테스트 79개, 실패 0(`ContributionRuleTests`에 5개 더함). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 65개, 확인 129개, 실패 0
  - 실제 백엔드와 가짜 API에 같은 요청 26개를 보내 응답과 오류 메시지가 같음을 비교함(저장소에 넣지 않은 임시 스크립트)
  - 겹치는 요청 시험: 같은 항목에 보고 두 개를 동시에 보내기를 20번 함. 매번 하나만 200이고 나머지는 409임(저장소에 넣지 않은 임시 스크립트). 잠금을 뺀 코드와 비교하지는 않음
  - 브라우저에서 실제 백엔드로 확인: 담당 조원(박도윤)에게 "완료 보고" 버튼이 보이고, 조장이 아니라서 등록 form은 없음. 메모가 비면 문구가 나옴. 보고하면 그 줄이 "보고됨"과 보고 시각으로 바뀌고 메모 줄바꿈이 그대로 보임
  - rule-check: 금지 패턴 없음. 점수 계산 없음. 새 클래스와 public 메서드에 주석 있음. 값을 바꾸는 `report`에 `@Transactional` 있음. 수정 필요 없음
- 읽을 때 볼 곳:
  - `ContributionService.report`에 `save`가 없는 이유. `@Transactional` 안에서 찾은 Entity는 값만 바꿔도 커밋할 때 저장됨
  - `ContributionService.report`의 첫 줄과 `findWithLockById`. `TeamService.changeLeader`와 같은 잠금임
  - `DataInitializer`의 `reportedContribution`. 평가 단계 과제에는 API로 보고할 수 없어서 저장 전에 `report`를 부름
  - `ContributionList`의 `reportTargetId`. 어느 줄의 form을 열지 page가 정함
- 내가 채운 가정:
  - 취소된 항목에 보고하면 400 `INVALID_INPUT`임
  - 시작 데이터의 평가 단계 항목을 "보고됨"으로 둠. 메모 문구는 내가 씀
  - "완료 보고", "보고", "닫기", 메모 안내 문구와 오류 메시지는 내가 씀

### R-2-4 기여 항목 취소

- 결정:
  - `POST /contributions/{contributionId}/cancel`을 둠. `docs/api-draft.md` 예정 표의 주소 그대로임. body는 없음. 선택지: `DELETE`. 산출물 삭제는 파일 본문이 실제로 지워지지만 여기는 상태만 바뀌어서 `POST`로 둠
  - 검사 순서는 없음 404 → 조장 아니면 403 → 평가 단계 400 → 이미 취소 400 → 이미 보고 400임
  - 다시 취소하면 400임. 산출물을 다시 삭제할 때와 같음. 보고만 "두 번 제출"과 같은 일이라 409임
  - 완료 보고와 같은 행 잠금을 씀. 보고와 취소가 겹쳐도 둘 다 성공하지 않음
  - 취소 전 확인은 `window.confirm`으로 함. 되돌릴 수 없는 동작임
  - 조장이 자기 항목의 보고 form을 연 채 취소하면 form도 닫음
  - 등록, 보고, 취소 요청 중에는 `busy`로 버튼을 모두 막음. `.then` 안의 옛 `contributions`가 다른 결과를 덮어쓰지 않게 함
- 고친 파일:
  - 백엔드: `entity/Contribution`, `service/ContributionService`, `controller/ContributionController`, 테스트 `ContributionRuleTests`
  - 가짜 API: `frontend/mock/middleware.cjs`
  - 화면: `api/contributions.js`, `pages/ContributionsPage.jsx`, `components/ContributionList.jsx`, `components/ContributionCreateForm.jsx`, `components/StartEvaluationPanel.jsx`
  - 문서: `docs/api-draft.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`
- 확인:
  - `gradlew clean build` 성공. 테스트 85개, 실패 0(`ContributionRuleTests`에 6개 더함). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 73개, 확인 145개, 실패 0
  - 실제 백엔드와 가짜 API에 같은 요청 38개를 보내 응답과 오류 메시지가 같음을 비교함(저장소에 넣지 않은 임시 스크립트)
  - 겹치는 요청 시험: 같은 항목에 보고와 취소를 동시에 보내기를 20번 함. 매번 하나만 200이고 나머지는 400임. 보고 두 개도 다시 20번 해서 매번 하나만 200임(저장소에 넣지 않은 임시 스크립트). 잠금을 뺀 코드와 비교하지는 않음
  - 브라우저에서 실제 백엔드로 확인: 조장에게 보고 전 항목마다 취소 버튼이 보임. 확인 창에서 물러나면 그대로이고, 확인하면 "취소됨"과 취소 시각으로 바뀌고 버튼이 사라지며 열어 둔 보고 form도 닫힘. 교수에게는 form도 버튼도 없고 취소된 줄은 보임. 평가 단계 조에서는 조장에게도 form과 버튼이 없고 안내 문구와 보고 메모가 보임. 폭 375px에서 가로 스크롤 없음
  - 취소 확인 창은 눌러 보지 못함. 시험할 때 `window.confirm`을 바꿔 문구와 양쪽 동작을 확인함
  - 서버를 막 켠 직후에는 포트가 열려 있어도 시작 데이터가 아직 없어 로그인이 실패할 수 있음. 앱 문제는 아니고 시험 순서 문제였음
  - rule-check: 금지 패턴 없음. 점수 계산 없음. 새 public 메서드에 주석 있음. 값을 바꾸는 `cancel`에 `@Transactional` 있음. 수정 필요 없음
- 읽을 때 볼 곳:
  - `ContributionService.cancel`의 검사 순서. 이미 취소를 이미 보고보다 먼저 봐도 둘은 동시에 생기지 않음(잠금)
  - `ContributionsPage`의 `busy`. 계산되는 값이라 state로 두지 않음
  - `ContributionsPage.handleCancel`의 `reportTargetId === updated.id`
  - `ContributionRuleTests.newLeaderCancelsAfterChange`. 권한을 조회할 때의 조장으로 봄
- 내가 채운 가정:
  - 보고한 항목을 취소하면 400, 이미 취소한 항목을 취소하면 400임
  - 등록한 조장이 아니라 지금 조장이 취소함
  - 확인 창 문구, "취소" 버튼, 오류 메시지, 등록 form의 "보고 전이면 취소하고 다시 등록합니다"는 내가 씀

### R-3-1, R-3-3 완료확인 등록과 이력

- 결정:
  - 등록과 이력을 한 commit으로 묶음. 이력이 보여야 확인이 저장됐는지 화면에서 알 수 있음
  - `Confirmation` 엔티티를 새로 둠. 대상 열을 `deliverable`, `contribution` 둘로 나눔. `TODO.md`의 추천안대로이고 외래 키 제약이 유지됨. 선택지: 대상 종류와 번호를 한 열씩 두기. 제약을 걸 수 없음
  - 둘 중 하나만 채우는 것은 생성자 둘로 지킴. 선택지: 데이터베이스 check 제약. `ddl-auto`로 만들려면 `@Check`가 필요해 숨은 동작이 늘어남
  - 한 조원의 유효한 확인 하나는 대상 행 잠금(`findWithLockById`)과 검사로 지킴. 선택지: 부분 unique 인덱스. `ddl-auto`로 만들 수 없어서 O-1에서 더함. `TODO.md` "알려진 한계"에 적음
  - 검사 순서는 없음 404 → 조원 아니면 403 → 자기 기록 403 → 평가 단계 400 → 삭제·취소·보고 전 400 → 이미 확인 409임
  - 자기 기록은 403임. `docs/api-draft.md` 표의 "올린 조원을 뺀 조원"이 권한 조건이라서 400으로 두지 않음
  - 이력을 줄마다 미리 불러오지 않고 "완료확인 이력" 버튼으로 엶. 목록 응답을 바꾸지 않고 API 5개로 끝남. 건수가 목록에 드러나지 않음. 선택지: 목록 응답에 확인을 넣기. 줄이 늘수록 응답이 커지고 숫자처럼 비교하게 됨
  - 이력 state는 두 화면이 각각 가짐. 같은 코드 약 30줄이 두 page에 있음. "state는 page가 갖는다" 규칙을 지키고 조원이 따라 읽게 둠. 선택지: custom hook. 초심자에게 추상 계층이 하나 늘어남
  - 확인 전 확인 창은 두지 않음. 확인은 취소할 수 있음(R-3-2)
  - 산출물 삭제에는 잠금을 더하지 않음. 삭제가 flush로 행을 잠그므로 겹치는 확인은 삭제를 기다린 뒤 삭제 시각을 봄. 확인이 먼저 끝나면 "확인 뒤 삭제"와 결과가 같음
- 만든 파일:
  - 백엔드: `entity/Confirmation`, `repository/ConfirmationRepository`, `dto/ConfirmationResponse`, `service/ConfirmationService`, `controller/ConfirmationController`, 테스트 `ConfirmationRuleTests`
  - 화면: `api/confirmations.js`, `components/ConfirmationPanel.jsx`
- 고친 파일:
  - 백엔드: `repository/DeliverableRepository`, `config/DataInitializer`
  - 가짜 API: `frontend/mock/middleware.cjs`, `frontend/db.json`
  - 화면: `pages/DeliverablesPage.jsx`, `pages/ContributionsPage.jsx`, `components/DeliverableList.jsx`, `components/ContributionList.jsx`, `components/StartEvaluationPanel.jsx`
  - 문서: `docs/api-draft.md`, `docs/architecture.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`
- 확인:
  - `gradlew clean build` 성공. 테스트 94개, 실패 0(`ConfirmationRuleTests` 9개). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 88개, 확인 180개, 실패 0
  - 실제 백엔드와 가짜 API에 같은 요청 47개를 보내 응답과 오류 메시지가 같음을 비교함(저장소에 넣지 않은 임시 스크립트)
  - 겹치는 요청 시험: 같은 조원이 같은 항목에 확인 두 개를 동시에 보내기를 20번 함. 매번 하나만 200이고 나머지는 409이며 유효한 확인은 하나임. 항목 쪽 잠금을 잠시 뺀 코드로 같은 시험을 하면 20번 모두 둘 다 200이고 유효한 확인이 둘 생김. 산출물 쪽은 R-3-2 확인 때 시험함
  - 브라우저에서 실제 백엔드로 확인: 조원이 산출물과 보고된 항목의 이력을 열어 확인하면 맨 위에 더해지고 버튼이 사라짐. 올린 조원과 담당 조원, 교수에게는 완료확인 버튼이 없음. 다른 줄을 열면 이력이 바뀌고 패널은 하나만 열림. 삭제된 산출물은 이력만 보임. 취소된 항목과 보고 전 항목에는 이력 버튼이 없음. 평가 단계 조는 안내 문구와 시작 이력만 보임. 폭 375px에서 가로 스크롤 없음
  - `json-server --watch`는 `db.json`이 바뀌면 열린 연결을 끊음. 임시 비교 스크립트가 이 때문에 `ECONNRESET`으로 멈춰서 끊긴 요청만 다시 보내게 함. 앱 문제는 아님
  - rule-check: 금지 패턴 없음. 점수 계산, 건수 표시 없음. 새 클래스와 public 메서드에 주석 있음. 값을 바꾸는 `confirmDeliverable`, `confirmContribution`에 `@Transactional` 있음. 수정 필요 없음
- 읽을 때 볼 곳:
  - `ConfirmationService.confirmDeliverable`의 잠금. 확인 행이 아니라 대상 행을 잠그는 이유는 아직 확인 행이 없을 수 있기 때문임
  - `Confirmation`의 생성자 둘. 같은 이름이고 인자 종류로 고름(오버로딩)
  - `ConfirmationResponse.from`의 null 검사. 대상 열 중 하나는 항상 null임
  - `ConfirmationRepository`의 긴 메서드 이름. `CanceledAtIsNull`이 "유효한 확인"을 뜻함
  - `ConfirmationPanel`의 `myConfirmation`. 버튼을 보일지 이력에서 계산해 state로 두지 않음
  - `ContributionList`의 `showConfirmations`. 열린 줄에서는 버튼을 숨김
- 내가 채운 가정:
  - 응답에 `deliverableId`, `contributionId`를 둘 다 둠. 값이 없는 쪽은 null임
  - 이력은 최근 것부터임
  - 삭제된 산출물은 이력을 볼 수 있음. 취소된 항목에 확인할 수 없음은 `TODO.md`의 "보고된 기여 항목"에서 따름
  - 시작 데이터로 평가 단계 조에 완료확인 2건을 넣음
  - 화면 문구("완료확인 이력", "완료확인", "아직 완료확인이 없습니다.")와 오류 메시지는 내가 씀

### R-3-2 완료확인 취소

- 결정:
  - `POST /confirmations/{confirmationId}/cancel`을 둠. `docs/api-draft.md` 예정 표의 주소 그대로임. body는 없음
  - 검사 순서는 없음 404 → 확인한 조원 아니면 403 → 평가 단계 400 → 이미 취소 400임
  - 다시 취소하면 400임. 기여 항목을 다시 취소할 때와 같음
  - 다시 확인은 취소한 행을 되살리지 않고 새 행을 만듦. `TODO.md`의 "새 기록으로 남음"대로임
  - 삭제된 산출물의 확인도 취소할 수 있음. 선택지: 막기. 내가 남긴 기록을 거두는 일이고 새로 확인하는 일이 아님. `TODO.md`는 "새로 완료확인할 수 없음"만 막음
  - 취소 전 확인 창은 두지 않음. 잃는 것이 없고 다시 확인할 수 있음. 기여 항목 취소와 산출물 삭제는 되돌릴 수 없어서 확인 창이 있음
  - 취소 뒤 평가 단계가 되면 다시 확인할 수 없음. 단계 규칙대로이고 화면에서 미리 막지 않음
- 고친 파일:
  - 백엔드: `entity/Confirmation`, `repository/ConfirmationRepository`, `service/ConfirmationService`, `service/TeamAccessChecker`, `controller/ConfirmationController`, 테스트 `ConfirmationRuleTests`
  - 가짜 API: `frontend/mock/middleware.cjs`
  - 화면: `api/confirmations.js`, `components/ConfirmationPanel.jsx`, `components/DeliverableList.jsx`, `components/ContributionList.jsx`, `pages/DeliverablesPage.jsx`, `pages/ContributionsPage.jsx`
  - 문서: `docs/api-draft.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`
- 확인:
  - `gradlew clean build` 성공. 테스트 100개, 실패 0(`ConfirmationRuleTests`에 6개 더함). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 95개, 확인 197개, 실패 0
  - 실제 백엔드와 가짜 API에 같은 요청 66개를 보내 응답과 오류 메시지가 같음을 비교함(저장소에 넣지 않은 임시 스크립트)
  - 겹치는 요청 시험(저장소에 넣지 않은 임시 스크립트): 같은 확인에 취소 두 개를 동시에 보내기 20번, 매번 하나만 200이고 나머지는 400임. 같은 산출물에 확인 두 개를 동시에 보내기 20번, 항목에 보내기 20번, 매번 하나만 200이고 유효한 확인은 하나임. 취소 쪽은 잠금을 뺀 코드와 비교하지 않음
  - 브라우저에서 실제 백엔드로 확인: 내 유효한 확인 줄에만 "확인 취소"가 보임. 취소하면 그 줄이 "취소됨"과 취소 시각으로 바뀌고 "완료확인" 버튼이 다시 보임. 다시 확인하면 새 줄이 맨 위에 생기고 취소한 줄은 남음. 기여 항목 화면은 취소와 버튼이 다시 보이는 것까지 눌러 봄. 다시 확인은 규칙 테스트로 봄. 삭제된 산출물은 확인 취소만 되고 다시 확인 버튼은 없음. 보고 전 항목에는 이력 버튼이 없음. 평가 단계에서는 자기 확인에도 취소 버튼이 없음. 교수에게는 버튼 없이 취소된 줄까지 보임. 폭 375px에서 가로 스크롤 없음. 서버 오류 로그 없음
  - rule-check: 금지 패턴 없음. 점수 계산, 건수 표시 없음. 새 public 메서드에 주석 있음. 값을 바꾸는 `cancel`에 `@Transactional` 있음. 수정 필요 없음
- 읽을 때 볼 곳:
  - `ConfirmationService.cancel`에 `save`가 없는 이유. `@Transactional` 안에서 찾은 Entity는 값만 바꿔도 커밋할 때 저장됨
  - `ConfirmationPanel`의 `confirmation === myConfirmation`. 같은 배열에서 찾은 줄이라 객체 비교로 내 유효한 줄을 고름
  - `DeliverableList`의 `canCancel={canConfirm}`. 줄마다 좁히기 전의 값이라 삭제된 산출물에서도 취소됨
  - `ConfirmationRuleTests.confirmerCancelsAndConfirmsAgainAsNewRecord`. 번호가 다른 두 행이 남는지 봄
- 내가 채운 가정:
  - 다시 취소하면 400임
  - 삭제된 산출물의 확인도 취소할 수 있음
  - 취소 응답은 200으로 확인을 돌려줌
  - "확인 취소" 버튼과 오류 메시지는 내가 씀

### E-2-2 평가 임시 저장

- 결정:
  - `GET`, `PUT /teams/{teamId}/evaluations/draft`를 둠. `docs/api-draft.md` 예정 표의 주소 그대로임
  - `EvaluationDraft`를 새로 둠. `TODO.md`의 추천안대로이고 제출본 `Evaluation`의 점수는 NOT NULL로 유지함. 선택지: `Evaluation`에 "임시" 표시를 더하고 점수를 null 허용으로 바꾸기. 제출본 검사가 코드에만 남고 교수 조회에서 임시 행을 빼는 조건이 계속 따라다녀서 고르지 않음
  - PUT은 보낸 내용으로 이전 임시 저장 전체를 바꿈. 선택지: 조원별로 보낸 것만 고치기. 화면이 늘 전체를 보내고 "마지막 내용으로 바뀜"이 그대로 보여서 전체 교체로 함. 빈 목록은 모두 지움
  - 저장은 평가 단계에서 제출 전까지만 됨. 기록 단계 400, 제출 뒤 409임. 불러오기는 단계 검사 없이 빈 목록을 돌려줌. 평가 화면이 기록 단계에도 같은 요청을 보내기 때문임
  - 대상은 자신을 뺀 같은 조 조원만임. 자기 자신, 다른 조, 중복은 400임
  - 응답에는 임시 저장 번호(`id`)를 두지 않음. 저장할 때마다 바뀌는 값이고 화면은 대상 조원 번호로 찾음
  - 저장과 제출은 조 행을 잠가 한 번에 하나씩 처리함(`findWithLockById`). 잠금이 없으면 겹친 저장이 unique 제약에 걸려 500이 남. 제출과 겹친 저장은 제출 뒤에 임시 저장을 남길 수 있음. 선택지: 평가자의 조 참여 행 잠금. 조 행은 이미 잠그는 메서드가 있고 조 안의 저장이 잠깐 기다릴 뿐이라 조 행으로 함
  - 제출의 unique 오류 처리(409)는 잠금을 더해도 마지막 장치로 둠
  - 제출에 성공하면 같은 트랜잭션에서 내 임시 저장만 지움
  - 화면은 임시 저장 버튼과 저장 시각만 더함. 고치고 저장하지 않은 채 나갈 때의 경고와 자동 저장은 `TODO.md`에 없어서 만들지 않음
  - 시작 데이터에 임시 저장을 넣지 않음
- 만든 파일:
  - 백엔드: `entity/EvaluationDraft`, `repository/EvaluationDraftRepository`, `dto/EvaluationDraftSaveRequest`, `dto/EvaluationDraftResponse`, 테스트 `EvaluationDraftRuleTests`
- 고친 파일:
  - 백엔드: `service/EvaluationService`, `controller/EvaluationController`
  - 가짜 API: `frontend/db.json`, `frontend/mock/middleware.cjs`. 정수가 아닌 점수의 오류 문구가 실제와 달라서 제출 쪽도 함께 맞춤(`hasNotIntegerScore`)
  - 화면: `api/evaluations.js`, `pages/EvaluationPage.jsx`
  - 문서: `docs/api-draft.md`, `docs/architecture.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`, `frontend/README.md`
- 확인:
  - `gradlew clean build` 성공. 테스트 108개, 실패 0(`EvaluationDraftRuleTests` 8개 더함). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 107개, 확인 224개, 실패 0
  - 실제 백엔드와 가짜 API에 같은 요청 40개를 보내 응답과 오류 메시지가 같음을 비교함(저장소에 넣지 않은 임시 스크립트)
  - 겹치는 요청 시험(저장소에 넣지 않은 임시 스크립트): 같은 조원의 저장 두 개를 동시에 보내기 30번, 모두 200이고 어느 한 요청의 내용만 남음. 세 조원의 저장을 동시에 보내기 30번, 모두 200임. 제출과 저장 두 개를 동시에 보내기 1번, 임시 저장이 남지 않음. 제출과 겹치는 시험은 제출이 한 번뿐이라 1번만 함
  - 잠금을 뺀 코드와 비교함: 같은 조원의 저장 두 개에서 30번 중 21번 한쪽이 500임. 제출과 겹친 시험에서도 저장 하나가 500임
  - 브라우저에서 실제 백엔드로 확인: 점수 3개와 의견만 넣고 임시 저장하면 저장 시각이 나옴. 다시 열면 고른 점수, 의견, 저장 시각이 그대로임. 나머지를 채워 확인 화면을 거쳐 제출하면 "제출했습니다" 화면이 나오고 임시 저장은 빈 목록이며 다시 저장하면 409임. 교수의 불러오기는 403임. 아무것도 고르지 않은 저장도 됨. 기록 단계 화면은 오류 없이 안내만 나옴. 폭 375px에서 가로 스크롤 없음. 서버 오류 로그 없음
  - 브라우저 확인 중 다른 세션이 같은 폴더의 `frontend/src`를 바꾸고 `EvaluationPage.jsx`를 지웠다가 복구함. 복구한 파일은 diff로 내 패치와 대조했고, 내용이 바뀐 파일이 `api/evaluations.js`, `pages/EvaluationPage.jsx` 둘뿐임을 확인한 뒤 브라우저 확인을 처음부터 다시 함
  - rule-check: 금지 패턴 없음. 점수 계산 없음. 새 public 메서드에 주석 있음. 값을 바꾸는 `saveDrafts`에 `@Transactional` 있음. 시각은 서버가 정함. 수정 필요 없음
- 읽을 때 볼 곳:
  - `EvaluationService.saveDrafts`의 `deleteAllInBatch`. JPA는 모아 둔 SQL을 보낼 때 INSERT를 DELETE보다 먼저 보내서, 그냥 지우면 새 행이 unique 제약에 걸림
  - `EvaluationDraftSaveRequest.Item`과 `EvaluationSubmitRequest.Item`의 차이. 점수에 `@NotNull`이 없고, `@Min`, `@Max`는 null이면 통과함
  - `EvaluationDraft`의 점수가 `int`가 아니라 `Integer`인 이유. `int`는 null을 담지 못함
  - `EvaluationPage`의 `createInputs`. 서버의 빈 의견 null을 빈 글자로 바꿈. textarea의 value는 글자여야 하고 확인 화면도 `comment.trim()`을 씀
- 내가 채운 가정:
  - PUT은 전체 교체이고 빈 목록은 모두 지움
  - 불러오기는 기록 단계에도 200이고 빈 목록임
  - 제출 뒤 저장은 409임
  - 응답에 임시 저장 번호가 없음
  - 저장과 제출에 조 행 잠금을 씀
  - 임시 저장 버튼 위치, 안내 문구, 오류 메시지 "평가 대상이 아닌 조원입니다"는 내가 씀

### 프론트 화면 구조 개편 (관리자 지시 "프론트 구조를 실제 퍼블리싱되는 웹사이트 급으로", 시안 합의안 반영)
- 결정:
  - 틀을 상단 바에서 과제 레일, 왼쪽 메뉴, 상단 경로, 본문, 오른쪽 상세로 바꿈. 선택지: 상단 바 유지(메뉴가 늘면 한 줄에 안 들어감), 메뉴만 추가(과제 전환이 내 과제 화면을 거쳐야 함). 시안 토론 뒤 관리자가 레일 유지를 고름
  - 목록에서 줄을 누르면 화면을 옮기지 않고 오른쪽 상세를 염. 폭이 1260px 이하이면 상세를 오른쪽에 겹쳐 열고, 820px 이하(폰)이면 목록 대신 상세만 보여 줌. 나란히 둘 수 없을 때 상세가 목록 아래로 떨어지면 줄을 눌러도 안 열린 것처럼 보여서 겹침으로 바꿈
  - 라이트 모드도 색 차이가 있는 라운드 박스로 구역을 나눔(관리자 지시)
  - `/teams/:teamId`를 조 홈으로 바꾸고 조원 화면을 `/teams/:teamId/members`로 나눔. 조 홈은 내가 할 수 있는 일과 최근 산출물만 보여 줌
  - `window.confirm`을 화면 안 확인 상자 `ConfirmBox`로 바꿈. 초심자가 같은 부품을 따라 쓰도록 삭제, 취소, 평가 시작, 조장 교체, 합류에 모두 씀
  - 메뉴 이름 "진척"은 유지하되 API가 없어서 이번에는 메뉴를 넣지 않음. 완료확인 설명 문구는 넣지 않음(관리자 결정)
  - API가 없는 근거 패널(E-1-1), 전체 기록(V-1-1), 평가 결과와 CSV(V-2, X-5), 진척(R-4-1), 알림(X-6), 합류 미리보기와 복사 버튼(X-4)은 넣지 않음. 시안에만 있음
  - API와 가짜 API는 바꾸지 않음. `api/client.js`는 로그인 만료 때 가는 주소만 `/login?expired=1`로 바꿈
  - 새 의존성 없음
- 만든 파일:
  - pages: `TeamMembersPage`
  - components: `AssignmentRail`, `ConfirmBox`, `ContributionDetail`, `DeliverableDetail`, `DeliverableTimeList`, `DetailPanel`, `EvaluationTargets`, `Icon`, `LoginBrand`, `LogoMark`, `MeBox`, `MemberDetail`, `PageError`, `PageLoading`, `RecentDeliverableList`, `SideHead`, `SideNav`, `TeamTodoList`, `TopBar`
- 고친 파일: `frontend/src/`의 `App.jsx`, `index.css`, `format.js`, `api/client.js`, pages 전부, 나머지 components, `docs/architecture.md`, `frontend/README.md`
- 지운 파일: `components/StartEvaluationPanel.jsx`(`ConfirmBox`로 대체)
- 확인:
  - `npm run build`, `npm run lint` 통과
  - 가짜 API로 브라우저 확인. 학생, 조장, 교수 계정으로 업로드, 삭제, 항목 등록, 완료 보고, 완료확인과 취소, 임시 저장 뒤 다시 열기, 전원 제출, 조장 교체, 평가 시작, 로그인 만료를 눌러 봄
  - 다크, 라이트, 375px, 1024px, 1400px 폭을 봄. 375px에서 모든 화면의 가로 넘침이 없음
  - 실제 백엔드로는 돌리지 않음. API 호출 코드를 바꾸지 않아서 가짜 API 확인으로 갈음함
- 읽을 때 볼 곳:
  - `pages/AppLayout.jsx`의 `<Outlet key={location.pathname} />`: 주소가 바뀌면 안쪽 화면을 새로 만들어 이전 화면의 state가 남지 않게 함
  - `pages/DeliverablesPage.jsx`의 `location.state?.selectedId`: 조 홈에서 넘겨준 줄을 바로 염. 새로고침하면 사라져도 되는 값이라 주소에 넣지 않음
  - 상세를 불러오는 `useEffect`의 `ignore`: 줄을 빠르게 바꿀 때 늦게 온 옛 응답을 버림
  - `index.css`의 `display: contents`: 폰 폭에서 메뉴 묶음을 없는 셈 치고 항목을 아래 탭에 한 줄로 놓음
  - `pages/EvaluationPage.jsx`의 `selectedTargetId`: 고른 적이 없으면 첫 조원을 씀. 계산할 수 있는 값이라 첫 조원을 state에 넣지 않음
  - `.tr`의 칸 폭: `.cols-*`의 `min-width`는 칸 폭을 더한 값이라 칸을 바꾸면 같이 고쳐야 함
- 내가 채운 가정:
  - 내 과제 화면의 왼쪽 메뉴에 내 과제, 새 과제를 보여 줌(시안은 메뉴를 숨김)
  - 학생이 과제를 열면 자기 조 홈으로 바로 감
  - 메뉴 머리에 담당 교수와 평가 시작 시각을 보여 줌
  - 역할 색은 이름의 글자 코드로 정함. 다른 역할이 같은 색일 수 있음
  - 레일 칸 글자는 앞 두 단어의 첫 글자임. 시드 과제명이 "발자국"으로 같이 시작해서 앞 두 글자로는 구분되지 않음
  - 역할별 묶음 순서는 가나다순임. 많이 올린 순은 순위처럼 보여서 뺌
  - 산출물에 시간순 탭을 둠. 역할별이 기본임
  - 목록의 "삭제됨"에는 시각을 빼고 상세에만 둠. 칸이 좁기 때문임
  - 새 과제에 입력 내용 확인 단계를 둠. 등록한 과제는 고칠 수 없기 때문임
  - 완료 보고에 확인 상자를 둠. 보고 뒤 메모를 고칠 수 없기 때문임
  - 합류 화면은 틀 밖에 둠. 아직 조에 들어가기 전이라 메뉴가 없음
  - 교수의 조원 줄 버튼 이름은 "조장 교체"임
  - 조 홈의 할 일에서 완료확인은 뺌. 항목마다 API를 불러야 해서 요청이 많아짐
  - 입력 칩 상태(입력함, 입력 중, 미입력)는 고른 항목 수로 정함. 점수를 더하지 않음

### E-1-1 근거 패널

- 결정:
  - `GET /teams/{teamId}/members/{teamMemberId}/evidence`를 둠. `docs/api-draft.md` 예정 표의 주소 그대로임
  - 응답은 `deliverables`, `contributions`, `receivedConfirmations`, `givenConfirmations` 네 목록이고 모두 최근 것부터임. 건수 필드는 두지 않음
  - 삭제된 산출물과 취소된 기여 항목·완료확인도 빼지 않음. 기존 목록과 같게 "삭제됨", "취소됨"으로 보여 줌
  - 같은 조 조원은 평가 단계에서만 봄. 기록 단계는 400임. 과제를 만든 교수는 모든 단계에서 봄. 선택지: 교수도 평가 단계만. `TODO.md`는 "평가 단계에서"라고 적었지만 교수 쪽은 평가 결과 화면을 말함. 교수는 기록 단계에도 다른 기록을 모두 볼 수 있어 막을 이유가 없음
  - 자기 자신의 근거도 API로 볼 수 있음. 화면은 평가 대상만 고르므로 자기 기록을 열지 않음. 선택지: 자기 자신은 400. 막아서 지킬 정보가 없어 검사를 더하지 않음
  - 완료확인 한 줄에 산출물 또는 기여 항목 전체를 담음. 화면이 추가 요청 없이 바로 기록을 열기 위함임. 선택지: 기록 번호만 담고 다시 부르기. 줄마다 요청이 늘어서 고르지 않음
  - Service는 Entity 네 목록을 묶은 `TeamMemberEvidence`를 돌려주고 Controller가 DTO로 바꿈. "Entity → DTO 변환은 Controller" 규칙을 그대로 지키려는 것임
  - 기록을 눌렀을 때의 완료확인 이력과 파일 내려받기는 기존 API를 그대로 씀
  - 화면은 `DeliverableDetail`, `ContributionDetail`을 `isRecording={false}`로 다시 씀. 평가 단계라 쓰기 버튼이 나오지 않음
  - 패널은 평가 입력 중에만 띄움. 확인 화면과 제출 뒤에는 띄우지 않음. 제출 뒤에는 점수를 고칠 수 없어 볼 이유가 적음
  - 1261px 이상은 처음부터 열고 그보다 좁으면 "근거 기록 보기" 버튼으로 엶. 좁은 폭에서는 패널이 입력 form을 가림
  - 조 전체 산출물을 한 번 더 불러옴(`getDeliverables`). "백엔드 1번째" 같은 역할별 순번은 조 전체 목록이 있어야 계산됨
  - `saveBlob`을 `DeliverablesPage`에서 `src/saveBlob.js`로 옮김. 두 화면이 씀
  - 교수 화면은 만들지 않음. 교수의 평가 결과 화면이 V-2에서 생기므로 그때 `EvidencePanel`을 붙임. API와 권한은 이번에 끝냄
- 만든 파일:
  - 백엔드: `controller/EvidenceController`, `service/EvidenceService`, `service/TeamMemberEvidence`, `dto/EvidenceResponse`, `dto/EvidenceConfirmationResponse`, 테스트 `EvidenceRuleTests`
  - 화면: `api/evidence.js`, `saveBlob.js`, `components/EvidencePanel.jsx`, `components/EvidenceConfirmationList.jsx`
- 고친 파일:
  - 백엔드: `service/AssignmentPhaseChecker`, `repository/DeliverableRepository`, `repository/ContributionRepository`, `repository/ConfirmationRepository`
  - 가짜 API: `frontend/mock/middleware.cjs`
  - 화면: `pages/EvaluationPage.jsx`, `pages/DeliverablesPage.jsx`
  - 문서: `docs/api-draft.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`, `frontend/README.md`
- 확인:
  - `gradlew clean build` 성공. 테스트 115개, 실패 0(`EvidenceRuleTests` 7개 더함). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 113개, 확인 239개, 실패 0
  - 실제 백엔드와 가짜 API에 같은 요청 28개를 보내 응답과 오류 메시지가 같음을 비교함(저장소에 넣지 않은 임시 스크립트). 권한, 없는 번호, 잘못된 번호, 기록 단계, 확인 취소, 항목 취소, 산출물 삭제 뒤를 포함함
  - 브라우저에서 실제 백엔드로 확인(student01, `/teams/3/evaluation`): 폭 1400px에서 패널이 처음부터 열리고 네 구역이 나옴. 건수는 없음. 조원을 바꾸면 그 조원의 기록을 다시 불러옴. 산출물을 누르면 역할 순번, 파일 정보, 내려받기, 완료확인 이력이 나오고 내려받기는 파일 이름으로 저장을 시작함(저장 동작은 가로채서 이름만 확인함). 기여 항목을 누르면 보고 메모와 이력이 나옴. 쓰기 버튼 없음
  - 닫으면 "근거 기록 보기" 버튼이 나오고 다시 열림. 폭 1000px, 375px에서는 처음에 닫혀 있음. 375px에서 열면 form이 숨고 가로 스크롤 없음. 닫으면 form이 돌아옴. 확인 화면에서는 패널이 사라지고 "돌아가서 고치기"로 돌아오면 다시 열림. 제출 뒤에는 패널이 없고 근거 요청도 가지 않음. 콘솔 오류 없음
  - 시험 뒤 `frontend/db.json`과 `frontend/mock/files`를 되돌림. 비교 스크립트의 삭제 시험이 시작 파일 `seed-2.txt`를 지워서 git에서 복원함
  - rule-check: 금지 패턴 없음. 건수, 합계, 평균 없음(`.length`는 빈 목록 문구를 고를 때만 씀). 새 public 메서드에 주석 있음. 읽기 전용이라 `@Transactional` 없음. 접근 범위와 단계 검사가 Service에 메서드 호출로 보임. 수정 필요 없음
  - 확인 중 고친 것: 패널을 닫았다가 다시 열어 성공해도 전에 난 오류 문구가 남을 수 있어 성공할 때 지우게 함
- 읽을 때 볼 곳:
  - `EvidenceService.find`의 검사 순서. 교수가 아닐 때만 조원 검사와 단계 검사를 함
  - `EvidenceService`에서 받은 확인을 두 쿼리로 합친 뒤 다시 정렬하는 곳. 산출물의 확인과 기여 항목의 확인이 다른 연결을 타서 한 쿼리로 묶지 않음
  - `EvaluationPage`에서 `targets`, `selectedTarget`, `canInput`이 "불러오는 중" return보다 위로 올라온 이유. useEffect도 이 값을 쓰는데 Hook은 return 뒤에 둘 수 없음. 그래서 `me?.id`처럼 불러오기 전의 null을 견딤
  - 근거 useEffect의 `ignore`. 조원을 빠르게 바꿨을 때 늦은 응답이 덮어쓰지 않게 함
  - `EvidencePanel`의 `sequenceOf(teamDeliverables, ...)`. 순번은 조원별이 아니라 역할별이라 이 조원의 목록만으로는 계산되지 않음
- 내가 채운 가정:
  - 교수는 모든 단계에서 볼 수 있고 조원은 기록 단계에 400임
  - 자기 자신의 근거도 API로 볼 수 있음
  - 삭제·취소된 기록을 근거에 포함함
  - 패널은 입력 중에만 띄우고 제출 뒤에는 띄우지 않음
  - 1261px 이상에서 처음부터 열고 좁으면 버튼으로 엶
  - 남긴 완료확인 줄의 이름은 기록의 주인임
  - 구역 제목, 빈 목록 문구, 버튼 문구, 오류 메시지 "근거 패널은 평가 단계에서 볼 수 있습니다"는 내가 씀

### 로그인 히어로 문구 교체 (관리자 지시, "발자국으로 남습니다" 선택)
- 결정:
  - 제목을 "당신의 노력이 발자국으로 남습니다."로 바꿈. 원칙만 말하던 제목으로는 기록을 남겨 주는 서비스라는 점이 보이지 않기 때문임. 선택지: "한 일은 사라지지 않습니다."(발자국과 이어지지 않음), "당신의 노력, 남겨 둘게요."(화면 문구 문체에서 벗어남)
  - "알아서 기록"은 쓰지 않음. 시스템이 스스로 남기는 것은 시각, 순번, 이력이고 업로드와 완료 보고는 사람이 함
- 고친 파일: `frontend/src/components/LoginBrand.jsx`
- 확인: `npm run build`, `npm run lint` 통과. 로그인 화면을 데스크톱과 375px 폭에서 봄. 375px에서 가로 넘침 없음
- 내가 채운 가정:
  - 원칙 문장을 지우지 않고 설명 문장 끝으로 옮김
  - 설명 문장의 "점수를 계산하지 않고, 기록만 보여 줍니다"는 원칙 문장과 겹쳐서 뺌

### V-1-1 조별 기록 열람

- 결정:
  - `GET /teams/{teamId}/records`를 둠. `docs/api-draft.md` 예정 표의 주소 그대로임
  - 응답은 줄의 배열 하나임. 줄 모양은 `type`, `occurredAt`, 조원 칸, 대상 칸, `title`, 역할 칸, 기록 번호로 모든 종류가 같음. 값이 없는 칸은 null임. 선택지: 종류마다 다른 모양. 화면 분기가 늘고 가짜 API 맞추기도 어려워서 고르지 않음
  - 줄은 최근 것부터임. `TODO.md`의 "시간순"을 이렇게 읽음. 선택지: 오래된 것부터. 다른 목록이 모두 최근 것부터라서 같게 함. 시각이 같으면 평가 시작, 조장 교체, 완료확인, 기여 항목, 산출물, 역할 순이고 한 기록 안에서는 나중 단계가 위임
  - 넣는 기록은 시각을 저장하는 것 전부임: 업로드, 삭제, 기여 항목 등록·보고·취소, 완료확인·취소, 역할 지정·변경, 조장 교체, 평가 시작. 평가 제출과 임시 저장은 `TODO.md`대로 뺌. 합류와 초대 코드 발급은 시각을 저장하지 않아서 뺌
  - 삭제와 취소는 원래 줄을 지우지 않고 줄을 하나 더함
  - 기여 항목의 등록과 취소는 조장이 하지만 누가 했는지 저장하지 않음. 조장은 바뀔 수 있어서 지금 조장으로 적으면 틀릴 수 있음. 줄의 이름은 담당 조원이고 화면 문구는 "담당 기여 항목 등록"임. 선택지: `Contribution`에 등록한 조원 칸 더하기. 규칙에 없는 칸이라 더하지 않음
  - `RoleHistory`에 `previousRoleAssignedAt`을 더함. 역할을 바꾸면 `TeamMember.roleAssignedAt`이 새 값으로 바뀌어 첫 지정 시각이 사라지기 때문임. 역할 이력 API의 응답은 그대로임. 선택지: 첫 지정 줄을 빼기, `TeamMember`에 첫 지정 시각 칸 더하기. 빼면 기록이 줄고, `TeamMember` 칸은 시작 데이터까지 고쳐야 함
  - Service는 Entity 묶음 `TeamRecords`를 돌려주고 `RecordResponse.listFrom`이 줄로 펼침. "Entity → DTO 변환은 Controller" 규칙과 `TeamMemberEvidence`와 같은 방식임
  - 조원별 보기는 API를 더 부르지 않고 화면에서 고름. 그 조원이 한 일에 더해, 그 조원의 기록에 남은 확인과 그 조원에게서 넘어간 조장 교체도 보여 줌. 평가 시작 줄은 사람이 없어 시간순에만 나옴
  - 파일이나 항목 이름을 누르면 `state`로 번호를 넘겨 산출물·기여 항목 화면에서 상세를 엶. 조 홈의 최근 산출물과 같은 방식임
  - 메뉴 이름은 "전체 기록"이고 학생은 "과정 기록" 아래, 교수는 고른 조 아래에 둠
  - 페이지 나누기는 두지 않음. 한 조의 기록이 많아도 수백 줄이라고 봄. 조원을 줄마다 다시 읽는 지연 조회가 있지만 다른 목록과 같은 수준으로 둠
- 만든 파일:
  - 백엔드: `controller/RecordController`, `service/RecordService`, `service/TeamRecords`, `dto/RecordResponse`, 테스트 `RecordRuleTests`
  - 화면: `api/records.js`, `pages/RecordsPage.jsx`, `components/RecordList.jsx`, `components/RecordRow.jsx`
- 고친 파일:
  - 백엔드: `entity/RoleHistory`, `service/TeamService`, `repository/RoleHistoryRepository`, `repository/ConfirmationRepository`
  - 가짜 API: `frontend/mock/middleware.cjs`
  - 화면: `App.jsx`, `format.js`, `index.css`, `components/SideNav.jsx`, `components/Icon.jsx`
  - 문서: `docs/api-draft.md`, `docs/architecture.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`, `frontend/README.md`
- 확인:
  - `gradlew clean build` 성공. 테스트 123개, 실패 0(`RecordRuleTests` 8개 더함). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 119개, 확인 265개, 실패 0
  - 실제 백엔드와 가짜 API에 같은 조회와 쓰기를 보내 응답 24건이 같음을 비교함(저장소에 넣지 않은 임시 스크립트). 권한, 없는 조, 잘못된 번호, 쓰기 10건 뒤의 줄과 순서, 평가 시작 뒤를 포함함. 시작 데이터의 시각이 양쪽 달라서 전체는 줄을 정렬해 비교하고, 새로 쌓은 줄은 맨 앞 순서까지 비교함
  - 브라우저에서 실제 백엔드로 확인: student01이 `/teams/3/records`에서 평가 시작 줄을 맨 위로 보고, 조원별로 바꾸면 조원마다 묶임. 기여 항목 이름을 누르면 기여 항목 화면에서 상세가 열림. 교수는 `/teams/1/records`에서 메뉴가 "전체 기록"에 켜지고, 파일 이름을 누르면 산출물 화면에서 상세가 열림. 다른 조 학생(student06)은 "이 조에 접근할 권한이 없습니다" 화면임. 폭 375px에서 가로 스크롤 없음. 콘솔 오류는 다른 조 학생 시험의 403뿐임
  - 실제 요청으로 역할 변경, 기여 항목 등록·보고·취소, 완료확인과 취소, 산출물 삭제, 조장 교체를 쌓은 뒤 student02 화면에서 줄 문구를 모두 확인함. 조원별 보기에서 이서연 아래에 받은 확인이, 김민준 아래에 넘겨준 조장 교체가 나옴. 쓰기가 같은 초에 몰려서 그 줄들은 시각이 아니라 넣는 순서로 놓임
  - 시험 뒤 `frontend/db.json`과 `frontend/mock/files`를 되돌림
  - rule-check: 금지 패턴 없음. 건수, 합계, 평균 없음(`.length`는 빈 목록 문구를 고를 때만 씀). 새 public 메서드에 주석 있음. 읽기 전용이라 `@Transactional` 없음. 접근 범위 검사가 Service에 메서드 호출로 보임. 시각은 서버가 정함. 수정 필요 없음
- 읽을 때 볼 곳:
  - `RecordResponse.listFrom`의 넣는 순서와 마지막 정렬. `sort`는 시각이 같은 줄의 순서를 유지해서 넣는 순서가 곧 같은 시각의 순서가 됨
  - `RecordResponse.addRoleRows`. 지금 역할에서 시작해 이력을 최근 것부터 거슬러 올라감. 변수 `roleName`이 줄마다 "그때의 새 역할"이 됨
  - `RoleHistory.previousRoleAssignedAt`과 `TeamService.changeRole`. `assignRole`을 부르기 전에 이력을 저장해야 이전 시각이 남음
  - `ConfirmationRepository.findByConfirmerTeamIdOrderByIdDesc`. 메서드 이름이 confirmer.team.id로 읽힘
  - `RecordList`의 `key`. 줄에 고유 번호가 없어서 순서 번호를 씀. 목록 순서가 바뀌지 않아서 괜찮음
  - `RecordRuleTests`의 `ReflectionTestUtils.setField`. 시각은 서버가 정해서 요청으로 바꿀 수 없음. 테스트에서만 칸에 직접 넣음
- 내가 채운 가정:
  - 최근 것부터 놓음. 시각이 같을 때의 순서도 내가 정함
  - 합류와 초대 코드 발급은 목록에 없음
  - 기여 항목 등록과 취소의 줄 이름은 담당 조원임
  - `RoleHistory`에 `previousRoleAssignedAt`을 더함
  - 조원별 보기에 받은 확인과 넘겨준 조장 교체도 보여 줌
  - 메뉴 이름 "전체 기록", 줄 문구, 페이지 설명은 내가 씀
  - 페이지 나누기는 두지 않음

### V-2-1, V-2-2, X-5 평가 결과 열람과 CSV

- 결정:
  - `GET /teams/{teamId}/evaluations/status`, `GET /teams/{teamId}/evaluations`, `GET /teams/{teamId}/evaluations/export`를 둠. `docs/api-draft.md` 예정 표의 주소 그대로임
  - 세 API 모두 과제를 만든 교수만 부름. `TeamAccessChecker`에 `checkProfessor`를 더함. 선택지: `isProfessorOf`를 Service에서 직접 씀. 메시지가 흩어지지 않고 접근 검사가 한 클래스에 모이게 함
  - 기록 단계에도 200으로 답함. 제출이 없으니 미제출과 빈 결과임. 선택지: 400. 교수의 다른 열람이 두 단계 모두 되는 것과 같게 함
  - 평가 결과는 납작한 목록이고 제출 순임. 선택지: 대상별로 묶음. 조원 목록과 같은 "펼친 모양" 규칙을 따르고, 묶기는 화면의 `filter`로 함
  - 제출 현황은 조원 전원을 한 줄씩 돌려줌. 제출 건수는 담지 않음
  - CSV는 `dto/EvaluationCsv`에서 직접 만듦. 선택지: CSV 라이브러리. 칸 규칙이 짧아서 의존성을 더하지 않음
  - CSV 줄 끝은 CRLF이고 칸은 RFC 4180의 큰따옴표 규칙을 따름. 제출 시각은 `yyyy-MM-dd HH:mm:ss`임
  - 근거 패널의 JSX를 `components/EvidenceSidePanel`로 뺌. `EvaluationPage`와 `EvaluationResultsPage`가 같이 씀. 데이터와 effect는 규칙대로 page에 둠. 선택지: 복사. 같은 JSX를 두 벌 두지 않음
  - 화면은 대상 조원을 고르면 그 조원이 받은 평가를 평가자별로 보여 주고 옆에 그 조원의 근거 패널을 띄움. `TODO.md`의 "대상 조원의 근거 패널을 함께 띄움"을 이렇게 읽음
- 만든 파일:
  - 백엔드: `dto/EvaluationSubmissionResponse`, `dto/EvaluationResultResponse`, `dto/EvaluationCsv`, `service/TeamEvaluations`, 테스트 `EvaluationResultRuleTests`
  - 화면: `pages/EvaluationResultsPage.jsx`, `components/EvaluationSubmissionList.jsx`, `components/EvaluationResultList.jsx`, `components/EvidenceSidePanel.jsx`
- 고친 파일:
  - 백엔드: `controller/EvaluationController`, `service/EvaluationService`, `service/TeamAccessChecker`, `repository/EvaluationRepository`
  - 가짜 API: `frontend/mock/middleware.cjs`
  - 화면: `App.jsx`, `api/evaluations.js`, `components/SideNav.jsx`, `pages/EvaluationPage.jsx`
  - 문서: `docs/api-draft.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`, `frontend/README.md`
- 확인:
  - `gradlew clean build` 성공. 테스트 130개, 실패 0(`EvaluationResultRuleTests` 7개 더함). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 129개, 확인 291개, 실패 0
  - 실제 백엔드와 가짜 API에 같은 요청을 보내 응답 32건이 같음을 비교함(저장소에 넣지 않은 일회용 스크립트). 시각은 지우고 JSON은 파싱해서 비교하고, CSV는 BOM과 글자를 그대로 비교함. 권한 403, 없는 조 404, 잘못된 주소 400, 임시 저장, 제출 전후를 포함함
  - 브라우저에서 실제 백엔드로 확인: 교수가 `/teams/3/evaluation-results`에서 제출 현황, 대상 조원 바꾸기, 평가자별 점수와 의견을 봄. 합계, 평균, 순위가 나오지 않음
  - 같은 화면의 근거 패널: 1440px 폭에서 처음부터 열리고 800px 폭에서는 닫혀 있음. 조원을 바꾸면 그 조원의 근거로 바뀜. 산출물과 기여 항목 상세를 열고 돌아오고 닫았다가 다시 염. 상세에 "교수는 이력만 봅니다."가 나오고 완료확인 버튼이 없음
  - CSV 버튼: 저장 동작을 가로채 내용을 확인함. 파일 이름 `발자국 평가 단계 과제-1조-동료평가.csv`, BOM `efbbbf`, 제목 줄이 맞음. 실제 파일을 Excel로 열어 보지는 않음
  - 기록 단계 `/teams/1/evaluation-results`는 안내만 나오고 CSV 버튼과 패널이 없음. student01이 `/teams/3/evaluation-results`로 직접 가면 "과제를 만든 교수만 볼 수 있습니다"가 나옴
  - `EvaluationPage` 회귀: student01이 패널을 빼낸 뒤에도 근거 목록, 산출물과 기여 항목 상세, 돌아가기가 그대로 동작함. 안내는 "평가 단계에서는 완료확인을 남길 수 없습니다."임
  - 시험 뒤 `frontend/db.json`과 `frontend/mock/files`를 되돌림
  - rule-check: 금지 패턴 없음. 건수, 합계, 평균 없음(`.length`는 빈 목록 문구를 고를 때만 씀)
- 읽을 때 볼 곳:
  - `EvaluationCsv.textCell`. 수식 방지 `'`를 먼저 붙이고 그다음 큰따옴표로 감쌈. 순서를 바꾸면 `'`가 따옴표 밖으로 나가서 CSV가 깨짐
  - `EvaluationCsv`의 `BOM`. 글자 하나이고 UTF-8로 바꾸면 3바이트 `EF BB BF`가 됨
  - `EvaluationController.export`. JSON이 아니라 파일 내용을 보내서 `ResponseEntity<byte[]>`를 씀. `DeliverableController.download`와 같은 모양임
  - `EvaluationRepository.findByEvaluatorTeamIdOrderByIdAsc`. 메서드 이름이 evaluator.team.id로 읽힘
  - `EvaluationSubmissionResponse.from`. 한 번에 제출해서 한 조원의 시각이 모두 같으므로 처음 찾은 평가의 시각을 씀
  - `EvaluationResultsPage`의 `selectedTarget`. 고르기 전에는 첫 조원이고 `??`가 그 처리임
  - 가짜 API의 `evaluationResultMatch`. 정규식 하나로 세 주소를 나누고 두 번째 칸으로 handler를 고름
- 내가 채운 가정:
  - 수식 방지를 의견뿐 아니라 이름 칸에도 적용함. `TODO.md`는 의견만 말하지만 이름도 사람이 쓴 글자임
  - 조원은 제출한 본인과 평가받은 대상도 403임
  - 기록 단계 화면은 제출 현황을 빼고 안내만 보여 줌. 모두 미제출인 목록은 정보가 없다고 봄. API는 그대로 답함
  - CSV 버튼은 평가 단계에서만 보여 줌. 제출이 없으면 제목 줄만 받음
  - 근거 패널은 평가 단계에서만 띄움. 평가자별 보기(평가자를 고르는 화면)는 두지 않음
  - 메뉴 이름 "평가 결과", 주소 `/teams/{id}/evaluation-results`, 파일 이름 `evaluations-team-{id}.csv`와 화면의 저장 이름, 페이지 문구는 내가 씀
  - CSV 제목 줄의 항목 이름은 `EVALUATION_CRITERIA`의 이름과 같게 씀

## 2026.11.05

### R-4-1 진척 화면

- 결정:
  - 새 API를 두지 않음. 산출물 목록과 조 정보를 그대로 씀. `docs/api-draft.md`의 예정대로 `GET /teams/{teamId}`에 `startDate`, `endDate`를 더함. 합류 응답도 같은 DTO라 같이 바뀜
  - 화면은 역할마다 가로줄 하나임. 왼쪽 끝이 과제 시작일 0시, 오른쪽 끝이 종료일 23:59:59임. 선택지: 달력 표, 차트 라이브러리. 의존성 없이 CSS `left`로 충분함
  - 삭제된 파일도 점으로 남기고 마지막 업로드에도 넣음. 삭제되어도 올린 사실은 기록이라 "기록 보존" 규칙을 따름. 점은 속을 비우고 마지막 업로드에 "삭제됨"을 붙임. 선택지: 삭제된 파일 제외
  - 기간 밖 업로드는 가까운 쪽 끝에 네모로 놓음. 서버가 기간 밖 기록을 막지 않아서 생길 수 있음. 선택지: 축을 늘림, 제외. 축을 늘리면 과제 기간이 줄마다 달라 보이고, 제외하면 기록이 사라짐
  - 삭제와 기간 밖은 색이 아니라 모양으로 나눔. 색은 역할 구분에 이미 쓰고 있음
  - 점을 누르면 산출물 화면에서 그 파일을 엶. 조 홈과 전체 기록이 쓰는 `location.state.selectedId`를 그대로 씀
  - 역할 이름을 모으는 코드를 `format.js`의 `roleNamesOf`로 옮겨 `DeliverableList`와 같이 씀. 선택지: 복사. 역할 순서 규칙이 두 벌이 되면 어긋날 수 있음
  - "조장·조원용"은 메뉴와 화면에서 막음. 교수도 볼 수 있는 산출물 목록을 그리는 화면이라 서버 검사를 더하지 않음
- 만든 파일:
  - 화면: `pages/ProgressPage.jsx`, `components/ProgressTimeline.jsx`, `components/ProgressLane.jsx`
- 고친 파일:
  - 백엔드: `dto/TeamResponse`, 테스트 `TeamRuleTests`
  - 가짜 API: `frontend/mock/middleware.cjs`
  - 화면: `App.jsx`, `format.js`, `index.css`, `api/teams.js`, `components/SideNav.jsx`, `components/Icon.jsx`, `components/DeliverableList.jsx`, `components/EvaluationNotice.jsx`
  - 문서: `docs/api-draft.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`
- 확인:
  - `gradlew clean build` 성공. 테스트 131개, 실패 0(`TeamRuleTests`에 1개 더함). `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 130개, 확인 294개, 실패 0
  - 실제 백엔드와 가짜 API에 조 정보와 합류 요청을 보내 응답 7건이 같음을 비교함(저장소에 넣지 않은 일회용 스크립트). 조원, 교수, 다른 조 403, 없는 조 404를 포함함
  - 브라우저에서 실제 백엔드로 확인: student01이 `/teams/1/progress`에서 역할 5줄을 봄. 올린 파일, 삭제된 파일(속이 빈 점과 "삭제됨"), 파일 없는 역할의 문구가 나옴. 점을 누르면 산출물 화면에서 그 파일이 열림. 건수, 진척률이 나오지 않음. 콘솔 오류 없음
  - 교수가 같은 주소로 직접 가면 "진척 화면은 조원만 볼 수 있습니다"가 나오고 교수 메뉴에 없음
  - 브라우저에서 가짜 API로 확인: 기간 밖 파일이 왼쪽 끝 네모로 나옴(시작 데이터의 2026-10-25 파일). 평가 단계 조에서도 보임. 2026.11.04 22:13 업로드의 위치 7.31%가 손으로 계산한 값과 같음. 375px 폭에서 가로로 밀리지 않음
  - 시험 뒤 `frontend/db.json`과 `frontend/mock/files`를 되돌림
  - rule-check: 금지 패턴 없음. 건수, 합계, 평균, 진척률 없음(`.length`는 빈 목록 문구를 고를 때만 쓰고, 퍼센트 값은 점의 위치에만 씀)
- 읽을 때 볼 곳:
  - `format.js`의 `timelinePercentOf`. `new Date('2026-11-02')`는 UTC로 읽혀서 한국에서 9시간 밀림. 시각을 붙이면 브라우저 시간대로 읽음. 이 값은 점의 위치이고 화면에 글자로 나오지 않음
  - `ProgressLane`의 `[...deliverables].reverse()`. 뒤에 그린 요소가 위로 올라옴. 원본 배열을 바꾸지 않으려고 복사한 뒤 뒤집음
  - `ProgressLane`의 `last`. 서버가 최신순으로 줘서 첫 번째가 마지막 업로드임
  - `index.css`의 `.lane-track`과 `.lane-mark`. 부모의 `position: relative`, 자식의 `position: absolute`와 `left`, `transform`이 함께 움직임
  - `.lane-mark::after`. 점은 그대로 두고 눌러지는 자리만 넓힘
- 내가 채운 가정:
  - 삭제된 파일을 마지막 업로드에 넣음
  - 기간 밖 업로드를 양 끝 네모로 표시함
  - 축에 시작일과 종료일만 표시함. 오늘 선과 중간 눈금은 두지 않음. 오늘 선이 진척처럼 읽힐 수 있다고 봄
  - 같은 시각 근처의 점은 겹치고 최근 파일이 위에 옴. 가려진 파일은 산출물 목록에서 봄. 시작 데이터처럼 한꺼번에 올리면 점 하나로 보임
  - 역할 줄은 산출물 목록과 같은 가나다순임
  - 메뉴 이름 "진척", 주소 `/teams/{id}/progress`, 페이지 문구, 교수 안내 문구는 내가 씀

### 구조 보드 갱신과 ERD 문서 분리 (관리자 지시 "지금까지 작성된 것을 기반으로 whiteboard 갱신, ERD는 추가문서")

- 결정:
  - 이 PC(Windows) Whiteboard 앱에 "발자국 구조 뼈대" 보드를 새로 만듦. 기존 보드는 노트북 앱에만 있어 이 PC에서 고칠 수 없음. 선택지: 구글 로그인 보드를 옮김, 새 보드. 구글 로그인 보드는 그 변경의 리뷰 기록이라 그대로 둠
  - 보드 대상은 commit `8d3156b`(diff 없음). 코드 링크가 그 commit을 가리킴. 코드가 바뀌면 대상 commit을 옮기고 본문을 고침
  - 섹션: 무엇·왜, 실행 구성, 흐름과 화면 지도, 권한과 과제 단계, 인증 흐름, 데이터 구조, 요청 하나의 길, 기능별 코드 지도, 남은 일
  - "추가문서"를 저장소 문서로 읽고 ERD를 `docs/architecture.md`에서 `docs/erd.md`로 옮김. 선택지: Whiteboard 하위 보드. GitHub에서 바로 보이고 조원이 읽을 수 있어서 저장소 문서를 고름. 보드에는 링크만 두어 ERD가 두 벌이 되지 않게 함
- 만든 파일: `docs/erd.md`
- 고친 파일: `docs/architecture.md`, `README.md`, `CLAUDE.md`, `docs/worklog.md`
- 문서와 코드가 어긋나 코드에 맞춘 것:
  - `architecture.md` 업로드 그림에 `AssignmentPhaseChecker`가 빠져 있었음. 더함
  - `architecture.md`의 "저장이 실패하면 파일만 남음"은 O-7 전의 설명이었음. 저장 오류는 파일을 지우고 커밋 단계 실패 때만 남는다고 고침
  - ERD 아래 기록 시각 목록에 `evaluationStartedAt`, `previousRoleAssignedAt`, `confirmedAt`, `savedAt`가 빠져 있었음. 더함
- 확인:
  - ERD 열과 제약을 Entity 11개의 필드, `@JoinColumn`, `@UniqueConstraint`와 대조함. 어긋난 곳 없음
  - `architecture.md`와 `erd.md`의 Mermaid 6개를 mermaid 11.4.1 `parse`로 검사함. 모두 통과. GitHub 화면으로는 push 뒤에 확인됨
  - 보드 전체를 다시 읽어 링크가 가리키는 줄과 표의 내용을 코드와 대조함
  - 문서 변경뿐이라 빌드와 테스트는 돌리지 않음
- 읽을 때 볼 곳:
  - 보드는 앱에 저장되고 저장소에 들어가지 않음. 노트북 보드와 이 보드는 서로 동기화되지 않음
  - Entity 필드를 바꾸면 `docs/erd.md`를 같은 작업에서 고칠 것
- 내가 채운 가정:
  - 노트북의 옛 보드는 그대로 둠. 지우거나 맞추는 일은 노트북에서 해야 함
  - 기능별 코드 지도의 "로그인 전용 테스트 없음"은 `backend/src/test`에서 `/auth/` 호출이 `TestApi`에만 있는 것을 보고 적음

### ERD 검수 후속: 역할 저장 잠금과 문서 어긋남 (관리자 지시 "둘 다 고쳐")

- 결정:
  - `TeamService.assignRole`, `changeRole`도 `changeLeader`처럼 조 행을 잠금(`findTeamWithLock`). 세 곳이 같은 잠금을 쓰므로 조회 메서드 하나로 묶고 이유 주석을 거기에 둠
  - 이전 판단을 재현 결과로 번복함. 위 T-2-2의 "잠금을 두지 않음"과 X-3의 "다른 동시 요청은 잠그지 않았지만"은 조장 한 명이 하는 일이라 결과가 작다고 본 판단이었음. 재현해 보니 역할 저장과 조장 교체가 겹치면 조장이 0명이나 2명이 됨. 옛 줄은 고치지 않고 여기에 남김
  - 원인은 전체 열 UPDATE임. Hibernate는 바뀐 열만 보내지 않고 모든 열을 보내서, 잠금 없이 읽은 `TeamMember`의 옛 `leader` 값을 역할 저장이 되돌림
  - 선택지: `@DynamicUpdate`(바뀐 열만 UPDATE), `@Version`(낙관적 잠금). `@DynamicUpdate`는 같은 이전 역할이 이력에 두 줄 남는 문제를 못 막음. `@Version`은 충돌 응답과 재시도를 새로 정해야 함. 조 행 잠금은 이미 있는 패턴이고 둘 다 막음
  - 수정 후 겹친 두 요청은 차례로 처리됨. 조장 교체가 먼저 끝나면 역할 요청은 403임. 옛 조장은 이미 조장이 아니기 때문임
  - 응답 모양과 주소는 그대로임. 가짜 API와 Postman 모음은 고치지 않음
- 고친 파일:
  - 백엔드: `service/TeamService`
  - 문서: `README.md`(엔티티 8개를 11개로), `TODO.md`, `docs/erd.md`, `docs/api-draft.md`
  - `TODO.md`: 이미 만든 `Confirmation`, `EvaluationDraft`를 "늘어나는 데이터"에서 지우고 11개에서 12개가 된다고 고침. 끝난 "ERD 세부"를 지움. 5절에 "조장 한 명 규칙에 데이터베이스 제약이 없음"을 더함
  - `docs/erd.md`: 지운 표의 이유(`CONFIRMATION` 대상 열, `EVALUATION_DRAFT` 분리와 수명)를 옮김. 조장 설명의 출처와 잠금 범위를 고침
- 확인:
  - `gradlew clean build` 성공. 테스트 131개, 실패 0
  - Newman으로 Postman 모음을 실제 백엔드에서 실행: 요청 130개, 확인 294개, 실패 0. 화면과 응답이 그대로라 가짜 API, `npm run build`, `npm run lint`는 돌리지 않음
  - 겹침 재현(저장소에 넣지 않은 일회용 스크립트, 로컬 서버). 수정 전과 후는 아래와 같음
    - 역할 변경끼리 겹침: 수정 전 첫 시도에 이전 역할이 같은 이력 두 줄. 수정 후 300회 재현 없음
    - 다른 조원 역할 변경과 조장 교체 겹침(조장 0명): 수정 전 3번째 시도에 재현. 수정 후 1500회 재현 없음
    - 조장 자기 역할 변경과 조장 교체 겹침(조장 2명): 수정 전 15번째 시도에 재현. 수정 후 1500회 재현 없음
  - 재현 없음이 요청 실패 때문이 아님을 따로 확인함. 역할 변경끼리는 60회 모두 둘 다 200이고 이전 역할이 같은 줄 없음. 역할과 교체를 겹친 120회는 교체가 모두 200이고 역할은 67회 200, 53회 403임. 조장은 늘 한 명임
  - 서버 오류 로그 없음
  - rule-check: 금지 패턴 없음. 권한 검사는 `checkLeader` 호출 그대로이고, 값을 바꾸는 메서드의 `@Transactional`도 그대로임
  - 통합 테스트는 더하지 않음. 테스트 하나가 트랜잭션 하나라 겹침을 만들 수 없음(`TODO.md` 5절)
- 읽을 때 볼 곳:
  - `TeamService.findTeamWithLock`. 잠금은 첫 줄에서 잡아야 함. 그 뒤에 읽는 조장 여부와 조원 행이 앞 요청이 커밋한 값이 됨
  - 잠금은 `TeamRepository.findWithLockById`의 `@Lock`이 걸고, 트랜잭션이 끝날 때 풀림. `@Transactional`이 없으면 이 잠금 조회가 실패함
  - 합류, 초대 코드 발급은 `findTeam` 그대로임. `team_member`의 `leader`와 역할 값을 고치지 않아서 잠그지 않음
  - 시작 데이터는 서버가 요청을 받기 시작한 뒤에 들어감. 시험 스크립트에서 틀린 비밀번호의 401로 준비 여부를 보면 시작 데이터 전에 로그인할 수 있음. 로그인 200을 기다려야 함
- 내가 채운 가정:
  - `TODO.md` 5절에 조장 한 명 규칙 줄을 더하고 해결 항목을 O-1로 적음. 완료확인 줄과 같은 이유임
  - `CLAUDE.md` 업무 규칙은 고치지 않음. "조장은 조마다 한 명"은 그대로이고 잠금은 구현 방법임
  - 긴 파일 이름 500, 산출물 삭제·평가 시작의 잠금, 합류 시각, O-1 개발 설정, X-1, 평가 시작과 겹친 쓰기는 이번 범위가 아니라 손대지 않음

## 2026.11.05 README Entity 개수 수정
- 결정: `README.md` 폴더 구조의 Entity 목록을 코드에 맞춤. 8개로 적혀 있었고 기여 항목, 완료확인, 평가 임시 저장이 빠져 있었음
- 고친 파일: `README.md`, `docs/worklog.md`
- 확인: `entity/`의 `@Entity` 클래스 11개, `docs/erd.md`의 "엔티티 11개"와 대조함. 문서 변경뿐이라 빌드와 테스트는 돌리지 않음

## 2026.11.05 노트북 Whiteboard 보드 갱신
- 결정:
  - 노트북 앱의 "발자국 구조 뼈대", "발자국 JWT 흐름" 보드를 `736487c` 기준으로 옮김. 선택지: 그대로 둠, 새 보드. 보드 ID와 링크를 유지하려고 기존 보드를 고침
  - 구조 보드는 흐름과 화면 지도, 데이터 구조, 요청 하나의 길을 다시 쓰고 권한과 과제 단계, 기능별 코드 지도, 남은 일 섹션을 더함
  - JWT 보드는 인증 구조가 그대로라 바뀐 줄 번호와 `/login?expired=1` 이동만 고침
- 고친 파일: `docs/worklog.md`
- 확인:
  - 고친 코드 링크가 가리키는 줄을 앱이 돌려준 인용과 대조함
  - CI는 `736487c`에서 성공함(`gh run list`)
  - 문서 변경뿐이라 빌드와 테스트는 돌리지 않음
- 읽을 때 볼 곳:
  - 보드는 앱에 저장되고 저장소에 들어가지 않음. 데스크톱 앱의 보드와 동기화되지 않음
  - 예전에 공유한 구조 보드 사본은 `4f5be17` 기준 그대로임
- 내가 채운 가정:
  - 첫 섹션 제목 "무엇·왜2"는 관리자가 고친 것일 수 있어 그대로 둠

## 2026.11.06

### 문서와 코드가 어긋날 때의 기준 (관리자 지시 "리포지토리의 문서와 기존 코드가 서로 일치하지 않을 때, 기존 코드에서 사용된 규칙을 따르고, 최종 답변에서 문서와 코드 사이에 발견한 각 불일치를 명세할 것")

- 결정:
  - `CLAUDE.md` 맨 끝에 관리자 문장을 그대로 넣고 날짜를 붙임
  - 이전 규칙 "코드가 이것과 어긋나면 코드가 틀린 것이다"를 지움. 새 규칙과 정반대라 둘 다 둘 수 없음
  - "기준 문서" 목록은 문서끼리의 순서로 두고, 기존 코드가 문서보다 앞선다는 줄을 위에 더함
  - 규칙 검사는 새로 만든 코드와 기존 코드를 나눔. 새 코드에서 걸린 것은 고침. 기존 코드의 규칙을 따라 어긋난 것과 손대지 않은 코드에서 걸린 것은 고치지 않고 보고함. 선택지: 전부 보고만 함. 새 코드의 실수까지 남게 되어 고르지 않음
  - "금지" 목록은 예외로 둠(관리자 지시 "응, 예외로 둠"). 기존 코드가 금지 항목을 어겨도 그 방식을 따르지 않고 보고함. 새 코드가 금지 항목에 걸리면 기존 코드를 따랐더라도 고침. 선택지: 금지 목록도 코드 우선. 어긴 코드 하나가 다른 코드로 퍼질 수 있어 고르지 않음
  - 학습 저장소보다 이 저장소가 우선임(관리자 지시 "study보다 demo가 더 우선순위가 높음"). 학습 저장소 문서(`기준 문서` 5, 6번)는 이 저장소의 문서와 코드 모두에 짐. 6번의 "그대로 따른다"는 이 저장소의 문서와 코드에 없는 부분에만 적용함
- 고친 파일:
  - `CLAUDE.md`: "기준 문서", "업무 규칙" 첫 줄, "작업 방식"의 규칙 검사 줄, 맨 끝 규칙
  - `.claude/skills/rule-check/SKILL.md`: 스스로 검사할 때의 절차
- 확인:
  - 문서만 고쳐서 빌드와 테스트는 돌리지 않음
  - 실제 코드와 문서의 대조는 하지 않음
- 읽을 때 볼 곳:
  - 관리자가 `/rule-check`로 직접 부르면 보고만 하고, "고쳐"라고 하면 고치는 절차는 그대로임
- 내가 채운 가정:
  - 대화에서 관리자가 방금 한 결정은 문서가 아니라서 기존 코드보다 앞선다고 적음
  - 맨 끝 규칙에 절 제목을 달지 않음. "실행 환경" 절 아래에 있음

## 2026.11.10 ERD 검수와 문구 보강
- 결정:
  - `docs/erd.md`를 Entity 코드, Service의 잠금, 다른 문서와 대조함. 열 75개, 관계선 19개, unique 3개에서 어긋난 곳은 없었음
  - 조장 한 명 서술에 첫 조장을 넣는 곳(`AssignmentService`)을 더함. `TeamService`만 적혀 있었고 그쪽은 교체와 합류만 맡음
  - `CONTRIBUTION.reportMemo` 비고에 "최대 2000자"를 더함. `Contribution`의 `@Column(length = 2000)`과 `api-draft.md`에 맞춤
  - A-1a 항목의 옛 unique 서술은 2026.10.29까지의 기록이라 그대로 둠
- 고친 파일: `docs/erd.md`, `docs/worklog.md`
- 확인: 문서 변경뿐이라 빌드와 테스트는 돌리지 않음. Mermaid가 GitHub에서 그려지는지와 실제 데이터베이스 스키마는 보지 않음

## 2026.11.12 프론트엔드 웹 가이드 우선 (관리자 지시 "충돌하는 부분이 있으면 해당 플러그인을 우선하고 싶은데. 어차피 학습의 방향도 트렌드를 맞춰야 하니까")
- 결정:
  - `modern-web-guidance@claude-plugins-official`(Google Chrome 팀, 0.0.194)을 관리자 PC에 user scope로 설치함. 이 저장소 설정에는 넣지 않음
  - `frontend/src`의 HTML·CSS·클라이언트 JS는 이 플러그인 가이드가 "이 코드를 읽을 사람"의 단순함·일관성, "코드 규칙 — 프론트엔드", 기존 프론트 코드보다 앞섬
  - 가이드가 이기지 못하는 것: "업무 규칙", "금지", `docs/api-draft.md`, 가짜 API 응답 모양 규칙, `mock/middleware.cjs`. 선택지: 금지까지 가이드 우선. 금지는 관리자가 먼저 정하는 목록이라 고르지 않음
  - 기존 코드는 한꺼번에 바꾸지 않고 만들거나 고치는 코드에만 적용함. 선택지: 지금 프론트 전체를 가이드에 맞춰 바꿈. 단위 업무 하나씩 만드는 작업 방식과 맞지 않아 고르지 않음
  - 가이드를 따라 규칙과 달라진 곳은 그 단위 worklog "결정" 줄에 가이드 id를 적음
- 고친 파일:
  - `CLAUDE.md`: "기준 문서" 첫 문단, 새 절 "프론트엔드 웹 가이드", 맨 끝 규칙
  - `.claude/skills/rule-check/SKILL.md`: 프론트엔드 금지 패턴 줄의 예외
- 확인: 문서 변경뿐이라 빌드와 테스트는 돌리지 않음
- 읽을 때 볼 곳:
  - 새 프론트 코드와 기존 코드의 방식이 섞임(예: `.then`과 `async`/`await`). 조원이 둘 다 읽게 됨
  - 가이드는 `npx modern-web-guidance@latest`로 매번 최신판을 받음. 같은 질문에도 시점마다 답이 다를 수 있음
- 내가 채운 가정:
  - 플러그인이 없는 환경(조원 PC, 노트북)에서는 이 절을 쓰지 않고 `CLAUDE.md` 규칙대로 만듦
  - 초심자가 처음 볼 브라우저 API에는 이유 주석 한 줄을 닮
  - 강단PC 브라우저 지원은 따로 규칙을 두지 않음

## 2026.11.12 X-6 화면 안 알림
- 결정:
  - 새 엔티티 `Notification`(받는 회원, 종류, 문장, 조, 만든 시각, 읽은 시각). 엔티티는 12개가 됨. 받는 쪽은 조 참여가 아니라 회원을 가리킴. 여러 과제의 알림을 한 목록으로 찾기 때문임. 선택지: `TeamMember`를 가리킴. 그러면 목록 조회가 조 참여를 거쳐야 함
  - 종류는 enum `NotificationType` 3개(`EVALUATION_STARTED`, `CONTRIBUTION_ASSIGNED`, `CONTRIBUTION_REPORTED`). 내용은 서버가 만든 한국어 문장 하나로 저장함. 문장 앞에 "[과제 제목 조 이름]"을 붙임. "1조"만으로는 어느 과제인지 알 수 없음. 선택지: 항목명·이름을 칸으로 나눠 저장하고 화면이 문장을 만듦. 칸이 늘고 종류마다 화면 분기가 생겨 고르지 않음
  - 받는 회원
    - 평가 시작: 그 과제 모든 조의 조원(조장 포함). 교수는 시작한 본인이라 받지 않음
    - 담당 지정: 담당 조원. 조장이 자신을 담당으로 정하면 만들지 않음. 스스로 한 일을 알릴 필요가 없음. 선택지: 언제나 만듦
    - 완료 보고: 같은 조에서 보고한 조원을 뺀 조원 전원(조장 포함). 담당 조원을 뺀 조원 누구나 완료확인을 하므로 조장만으로 좁히지 않음. 교수는 이력만 보는 쪽이라 받지 않음. 선택지: 조장만
  - 저장은 `AssignmentService.startEvaluation`, `ContributionService.create`, `ContributionService.report`가 같은 트랜잭션 안에서 `NotificationService`의 `notify...` 메서드를 직접 부름. 이벤트 리스너·AOP를 쓰지 않음(작업 지시와 "금지"). 알림 저장이 실패하면 원래 요청도 취소됨
  - 남의 알림 읽음 표시는 403. 검사는 다른 "본인만" 검사처럼 `TeamAccessChecker.checkRecipient`에 둠. 목록은 처음부터 본인 것만 찾아서 검사가 없음
  - 이미 읽은 알림을 다시 표시해도 200이고 처음 시각을 둠. 선택지: 409 `DUPLICATE`. 두 번 눌러도 해가 없는 일이라 오류로 만들지 않음
  - 단계 검사를 하지 않음. 알림 읽음은 조 운영도 과정 기록도 아니라서 평가 단계에서도 됨
  - **안 읽은 개수를 API에도 화면에도 두지 않음.** "건수를 담지 않음" 원칙과 같은 쪽을 고름. 화면은 `readAt`이 null인 알림이 있는지만 보고 종 모양 버튼에 점을 찍음. 선택지: 응답에 `unreadCount`, 버튼에 숫자. 평가와 상관없는 개수라 원칙에 직접 걸리지는 않지만, 개수 계산을 한 곳이라도 두면 다른 화면으로 번지기 쉬워 고르지 않음
  - 알림을 지우는 API와 "모두 읽음"은 두지 않음. 작업 범위의 두 API만 만듦
  - 시작 데이터에도 알림을 넣음. 시작 기록(담당 지정 1건, 완료 보고 1건, 평가 시작 1건)이 실제 요청으로 생겼을 때와 같은 알림을 `NotificationService`로 만듦. 가짜 API `db.json`에도 같은 6건을 넣음
  - 화면: 메뉴 상자 아래(`MeBox`)에 종 모양 버튼을 두고 `/notifications` 알림 화면으로 감. 버튼은 어느 화면에서나 보이는 자리라 고름. 선택지: 각 화면의 `TopBar`(화면마다 넣어야 함), 메뉴 줄(과제를 고르면 메뉴가 바뀜)
  - 공통 틀 `AppLayout`이 주소가 바뀔 때마다 알림을 다시 불러 점을 정함. 실시간 전송이 없어서 화면을 옮길 때가 새로 볼 때임. 점은 알림 배열에서 계산하고 따로 state로 두지 않음
  - 알림을 누르면 읽음 표시 후 그 조의 화면으로 감. 평가 시작은 동료평가, 나머지는 기여 항목 화면. 읽음만 하는 버튼은 따로 두지 않음
  - 종류 이름(`NOTIFICATION_TYPE_LABELS`)과 갈 주소(`notificationPathOf`)는 `RECORD_TYPE_LABELS`가 있는 `format.js`에 둠. 기존 코드를 따름
  - 프론트엔드 웹 가이드(오늘 앞 항목)를 따라 고친 곳. 이 세션에는 플러그인 스킬이 올라오지 않았고 `npx modern-web-guidance` 실행은 권한 확인에서 막혀, 관리자 PC에 받아 둔 가이드 파일(0.0.194의 `guides/accessibility/accessibility.md`)을 직접 읽음
    - `accessibility`: 알림 목록을 `<div>` 대신 `<ul>`, `<li>`로 그림("Use lists for repeated, contiguous content"). 기존 `RecordList`는 `<div>`라 두 방식이 섞임
    - `accessibility`: 종 버튼의 이름은 "알림"으로 두고 "안 읽은 알림이 있습니다"는 `aria-describedby`로 따로 읽게 함("Don't pack descriptions, error messages, or instructions into the label"). 숨긴 글자용 `.visually-hidden`을 `index.css`에 더함
    - `accessibility`: 알림 문장 버튼에 `min-block-size: 24px`("Interactive elements should be at least 24×24 CSS pixels")
- 만든 파일:
  - 백엔드: `entity/Notification`, `entity/NotificationType`, `repository/NotificationRepository`, `service/NotificationService`, `controller/NotificationController`, `dto/NotificationResponse`, 테스트 `NotificationRuleTests`
  - 화면: `api/notifications.js`, `pages/NotificationsPage.jsx`, `components/NotificationList.jsx`
- 고친 파일:
  - 백엔드: `service/AssignmentService`, `service/ContributionService`, `service/TeamAccessChecker`, `repository/TeamMemberRepository`, `config/DataInitializer`
  - 가짜 API: `frontend/db.json`, `frontend/mock/middleware.cjs`
  - 화면: `App.jsx`, `format.js`, `index.css`, `pages/AppLayout.jsx`, `components/MeBox.jsx`, `components/Icon.jsx`
  - 문서: `docs/api-draft.md`, `docs/erd.md`, `docs/architecture.md`, `docs/postman/`, `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`
- 확인:
  - `gradlew.bat build` 성공. 테스트 141개, 실패 0(`NotificationRuleTests` 10개 더함)
  - `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 136개, 확인 309개, 실패 0. 새 폴더 "7. 알림" 6개 요청
  - 실제 백엔드와 가짜 API에 같은 요청 24건(목록, 담당 지정, 자기 지정, 완료 보고, 평가 시작, 읽음, 다시 읽음, 403, 404, 400, 401)을 보내 응답이 같음을 비교함(시각 값만 가림, 저장소에 넣지 않은 일회용 스크립트)
  - 브라우저에서 실제 백엔드로 확인: 이서연이 종 버튼의 점을 보고 알림 화면에서 4건을 봄. 완료 보고 알림을 누르면 기여 항목 화면으로 가고 돌아오면 "읽음". 평가 시작 알림은 동료평가 화면으로 감. 모두 읽으면 점이 사라짐. 교수는 "받은 알림이 없습니다"와 점 없음. 375px 폭에서 가로로 밀리지 않음. 콘솔 오류 없음
  - 시험 뒤 `frontend/db.json`을 되돌림
  - rule-check: 금지 패턴 없음. `NotificationList`의 `.length`는 빈 목록 문구를 고를 때만 씀. Controller가 DTO를 돌려주고, 값을 바꾸는 Service 메서드에 `@Transactional`이 있음
- 읽을 때 볼 곳:
  - `NotificationService`의 `@Transactional`. 다른 Service의 트랜잭션 안에서 불리면 새로 열지 않고 그 트랜잭션에 들어감(기본 전파 `REQUIRED`). 그래서 알림 저장이 실패하면 평가 시작이나 기여 항목 등록도 취소됨. `DataInitializer`처럼 트랜잭션 밖에서 부르면 메서드마다 새로 열림
  - `NotificationResponse.from`의 `notification.getTeam().getId()`. `team`이 LAZY라도 id만 읽으면 조 행을 따로 읽지 않음
  - `AppLayout`의 알림 `useEffect` 의존 배열 `[location.pathname, reloadCount]`. 주소가 바뀌면 다시 부름
  - `MeBox`의 `aria-describedby`와 `.visually-hidden`. 화면에는 점만 보이고, 화면 읽기 프로그램은 "알림" 다음에 "안 읽은 알림이 있습니다"를 읽음
  - `mock/middleware.cjs`의 `notify...` 함수 3개는 실제 `NotificationService`와 순서·문장이 같아야 함. 문장을 고치면 `db.json` 시작 알림과 `NotificationRuleTests`도 같이 고칠 것
- 내가 채운 가정:
  - 받는 회원 범위(위 "결정"). 특히 조장 자기 지정 제외, 완료 보고를 조원 전원에게 보냄
  - 알림 문장 3개와 화면 문구("알림", "새 알림", "읽음", "받은 알림이 없습니다." 등)는 내가 씀
  - 알림 문장 길이 상한 1000자(과제 제목 255자 + 항목명 255자 + 이름이 들어가도 넘지 않음)
  - 다시 읽음 표시는 200으로 처음 시각 유지
  - 알림 화면 주소 `/notifications`, 종 버튼 자리
  - 알림을 지우거나 보관 기간을 두지 않음. X-2 보관 기간을 정할 때 같이 볼 것

## 2026.11.12 X-4 합류 전 미리보기, 초대 링크 복사 버튼
- 결정:
  - 새 API `GET /teams/join/{inviteCode}`(학생). 응답은 `{ assignmentTitle, teamName, leaderName }` 세 칸뿐임. 조원 수 같은 건수, 조원 목록, 조 번호, 과제 단계는 넣지 않음. 초대 코드를 아는 사람에게 필요한 최소만 줌. 선택지: 조 번호와 단계까지 줌. 화면이 쓰지 않는 값이라 고르지 않음
  - **미리보기는 합류와 같은 검사를 같은 순서로 하고 같은 오류를 돌려줌.** 초대 코드(404) → 학생인지(403) → 같은 과제의 조 참여(같은 조면 200, 다른 조면 409) → 기록 단계인지(400). 미리보기가 200이면 곧바로 누른 합류도 200임. 선택지: 미리보기는 늘 200으로 조 정보를 주고 `joinable`, `alreadyMember` 같은 칸을 더함. 칸이 늘고, 합류할 수 없는 학생에게도 조장 이름을 주게 되어 고르지 않음
  - 이미 그 조의 조원이면 평가 단계여도 200임. 합류 API가 그 경우 200이라 맞춤. 화면은 같은 "합류" 버튼을 보여 주고, 누르면 그 조 화면으로 감
  - 검사 코드는 `TeamService`의 private 메서드 `checkJoinable` 하나로 모아 `join`과 `findJoinPreview`가 함께 씀. 이미 그 조의 조원이면 true, 새로 합류할 수 있으면 false, 안 되면 예외임. 두 곳에 같은 검사를 따로 두면 한쪽만 고쳐질 수 있어서 모음. `join`의 동작과 오류는 그대로임(`TeamRuleTests` 통과)
  - Service는 조장의 조 참여 기록(`TeamMember`)을 돌려주고 Controller가 `TeamJoinPreviewResponse.from(leader)`로 바꿈. 과제 상세의 `TeamSummaryResponse.from(leader)`와 같은 방식임
  - 접근 범위 검사(`TeamAccessChecker`)는 하지 않음. 아직 조원이 아닌 학생이 부르는 API라서 합류 API와 같이 "초대 코드를 아는가"가 권한임
  - 주소 `GET /teams/join/{inviteCode}`는 `docs/api-draft.md` 추천안 그대로 둠. 코드 자리에 `members`처럼 다른 조 주소의 끝 글자를 넣으면 Spring이 `GET /teams/{teamId}/members`를 골라 400 `INVALID_INPUT`이 됨(테스트로 확인 후 테스트는 지움). 서버가 발급하는 코드는 UUID, 시작 데이터 코드는 `demo-team-1` 같은 값이라 겹치지 않아서 주소를 바꾸지 않음. 선택지: `GET /invite-codes/{inviteCode}`처럼 겹치지 않는 주소. 추천안 주소를 바꿀 만큼의 문제가 아니라 고르지 않음. 가짜 API는 이 경우 404로 답함(실제와 다름. 아래 "읽을 때 볼 곳")
  - 화면 `JoinPage`: 열면 미리보기를 불러 과제, 조, 조장을 `dl.props`로 보여 주고 "합류" 버튼을 따로 누르게 함. 미리보기가 실패하면 "이 링크로 합류할 수 없습니다"와 서버 문구, "내 과제로 가기"만 보여 주고 합류 버튼은 없음. 합류 버튼의 오류는 미리보기 오류와 나눔(`joinError`)
  - 화면 안에서 다른 초대 링크로 옮겨 가면 같은 `JoinPage`가 다시 쓰여 이전 오류가 남는 문제를 브라우저에서 찾아 고침. 결과를 받을 때마다 이전 미리보기·오류를 지우고, `ignore` 변수로 늦게 온 옛 응답을 버림(`TeamMembersPage`의 역할 이력과 같은 방식)
  - 복사 버튼: 조장 화면 `InviteLinkPanel`의 링크 아래에 "링크 복사" 버튼과 결과 문구 칸을 둠. 복사 결과 문구, timer는 `TeamMembersPage`가 갖고 props로 내림. "page만 state를 가짐" 규칙을 지킴. 다시 발급하면 문구를 지움
  - 성공 문구 "링크를 복사했습니다."는 2초 뒤 지움. 실패 문구 "복사하지 못했습니다. 위의 링크를 직접 선택해 복사하세요."는 지우지 않음. 직접 복사하는 동안 읽혀야 해서임
  - 프론트엔드 웹 가이드를 따라 정한 곳. 이 세션에서는 `modern-web-guidance` 스킬로 `npx modern-web-guidance search`, `retrieve`를 실행해 가이드를 받음
    - `copy-button`: 결과 문구 칸은 `role="status"`이고 버튼 밖에 둠, 문구가 생기기 전부터 빈 칸으로 그림("a live region inside one is not reliably announced"). 버튼은 문구가 보이는 동안에도 막지 않음("DO NOT disable the button while showing feedback"). 다시 누르면 이전 timer를 지우고 새로 셈
    - `copy-button`: 실패도 화면에 보임("clipboard access can be denied: show the failure too"). `navigator.clipboard`가 없는 비보안 context의 오류도 같은 `.catch`로 받으려고 `Promise.resolve().then(() => navigator.clipboard.writeText(...))`로 부름
    - `copy-button`: 직전과 같은 문구면 끝에 ` `을 붙임. VoiceOver가 같은 문구를 다시 읽지 않는 문제 때문임("VoiceOver skips a message identical to the previous one")
    - `copy-button`에서 따르지 않은 것: Invoker Commands(`command`, `commandfor`) 대신 React `onClick`을 씀. Invoker Commands는 Baseline "Newly available"(2025-12)이라 가이드가 주는 대체 경로(click에서 같은 복사 함수 호출)를 처음부터 씀. `async`/`await` 대신 `.then`을 씀(rule-check 금지 패턴, 동작은 같음). `document.execCommand('copy')` 대체는 넣지 않음. 가이드가 "Only as a last resort"로 두고, 비보안 context의 해법은 https라고 함. 아이콘 바꾸기, `data-copied` 속성은 글자 버튼이라 두지 않음
- 만든 파일:
  - 백엔드: `dto/TeamJoinPreviewResponse`, 테스트 `JoinPreviewRuleTests`
- 고친 파일:
  - 백엔드: `service/TeamService`(`findJoinPreview`, `findTeamByInviteCode`, `checkJoinable`, `join` 정리), `controller/TeamController`
  - 가짜 API: `frontend/mock/middleware.cjs`(`checkJoinable`, `findTeamByInviteCodeOrSendError`, `handleGetJoinPreview`, `handleJoinTeam` 정리, 주소 연결). `frontend/db.json`은 고치지 않음. 시작 조에 이미 초대 코드와 조장이 있음
  - 화면: `api/teams.js`(`getJoinPreview`), `pages/JoinPage.jsx`, `pages/TeamMembersPage.jsx`, `components/InviteLinkPanel.jsx`
  - 문서: `docs/api-draft.md`, `docs/postman/`(요청 3개), `CLAUDE.md`, `TODO.md`, `README.md`, `backend/README.md`
- 확인:
  - `gradlew.bat build` 성공. 테스트 146개, 실패 0(`JoinPreviewRuleTests` 5개 더함)
  - `npm run build`, `npm run lint` 성공
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 139개, 확인 318개, 실패 0. "2. 조 운영"에 미리보기 요청 3개(조원 200, 교수 403, 같은 과제의 다른 조 409)
  - curl로 실제 백엔드와 가짜 API에 같은 경우(유효 코드, 같은 조 조원, 같은 과제의 다른 조, 평가 단계 비조원·조원, 교수, 없는 코드, token 없음)를 보내 상태 코드, `code`, `message`가 같음을 확인. 가짜 API로 새 과제 → 발급 → 다시 발급 → 옛 코드 404 → 새 코드 미리보기 → 합류 → 다시 미리보기 200까지 확인
  - 브라우저에서 실제 백엔드로 확인: 로그인하지 않고 초대 링크를 열면 로그인 뒤 그 링크로 돌아오고 과제·조·조장이 보임. 합류 버튼을 누르면 조 화면으로 감. 없는 코드와 다른 조 코드는 오류 화면
  - 복사 버튼: 앱 안 브라우저는 클립보드 권한을 주지 않아 실제 클릭이 실패 문구로 끝남(실패 경로 확인). 성공 경로는 `navigator.clipboard.writeText`를 바꿔 끼워 문구, 같은 문구의 ` `, 2초 뒤 지움, 실제로 넘긴 링크 값을 확인함. `navigator.clipboard`를 없애도 실패 문구가 나옴
  - 시험 뒤 `frontend/db.json`을 되돌림
  - rule-check: 금지 패턴 없음. Controller가 DTO를 돌려주고 변환은 Controller에 있음. 조회만 하는 `findJoinPreview`에는 `@Transactional`이 없음(다른 조회와 같음). component(`InviteLinkPanel`)에 state 없음
- 읽을 때 볼 곳:
  - `TeamService.checkJoinable`의 반환값. true(이미 조원)와 false(새로 합류)를 받아 `join`만 저장으로 이어 감. 미리보기는 반환값을 버리고 예외만 씀
  - `TeamMembersPage`의 `useRef` 두 개(`copyTimerRef`, `lastCopyMessageRef`). 바뀌어도 화면을 다시 그릴 필요가 없는 값이라 state가 아님. 화면을 떠날 때 timer를 지우는 `useEffect` 정리 함수도 같이 볼 것
  - `Promise.resolve().then(() => navigator.clipboard.writeText(...))`. `navigator.clipboard`가 없으면 `.writeText`를 부르는 순간 오류가 나는데, `.then` 안이라 `.catch`로 감
  - `JoinPage`의 `ignore` 변수와 결과마다 이전 값을 지우는 부분
  - 실제 백엔드와 가짜 API의 차이 하나: 코드 자리가 `members`, `invite-code` 같은 글자이면 실제는 400, 가짜는 404임. 발급되는 코드로는 생기지 않음
- 내가 채운 가정:
  - 미리보기의 오류 규칙(합류와 같은 검사·순서·오류)과 이미 조원이면 평가 단계여도 200
  - 응답 칸 이름 `assignmentTitle`, `teamName`, `leaderName`, DTO 이름 `TeamJoinPreviewResponse`
  - 화면 문구("초대 링크를 확인하는 중입니다.", "이 링크로 합류할 수 없습니다", "링크 복사", "링크를 복사했습니다.", "복사하지 못했습니다. 위의 링크를 직접 선택해 복사하세요.")는 내가 씀
  - 성공 문구 2초, 실패 문구는 남김
  - 클립보드 실패 원인(권한 거부, 비보안 context)을 문구로 나누지 않음. 어느 쪽이든 할 일은 직접 복사라서임

## 2026.11.12 X-7 400 응답에 틀린 칸 담기
- 결정:
  - 응답 모양은 그대로 `{ code, message }`임. 칸(`errors` 등)을 더하지 않고, 새 `code`도 만들지 않음. `message` 끝에 `칸 이름: 이유`를 붙임. 앞 문장과는 `. `, 칸끼리는 `, `로 이음. 예: "입력값이 올바르지 않습니다. startDate: 값이 없습니다, title: 비어 있습니다". 선택지: `ApiErrorResponse`에 `fields` 목록 칸을 더함. 오류 응답 모양이 하나라는 규칙(CLAUDE.md)과 TODO.md 추천안에 어긋나서 고르지 않음
  - **칸 이름은 요청 JSON의 필드 이름**(`title`, `evaluations[0].goalSharing`)임. 선택지: 한국어 이름("과제명"). 칸마다 이름표를 백엔드와 가짜 API 두 곳에 같은 내용으로 둬야 하고, 목록 안 칸(`evaluations[1].goalSharing`)을 한국어로 옮기기 어렵고, `docs/api-draft.md`와 Postman이 쓰는 이름과 달라져서 고르지 않음. 이유 부분은 한국어임. 화면에서 이 문장이 사용자에게 보이는 곳은 과제 만들기에서 칸을 비웠을 때뿐임(다른 입력 화면은 숫자·길이를 화면이 먼저 막음)
  - **`@Valid`에 걸린 칸은 모두 적고, `칸 이름: 이유` 글자를 문자 코드 순으로 정렬함.** 검사기(Hibernate Validator)가 돌려주는 순서는 정해져 있지 않아서 정렬로 고정함. Java `String.compareTo`와 JavaScript `sort()`(비교 함수 없음)가 같은 UTF-16 문자 코드 순이라 가짜 API가 같은 문장을 냄. 선택지: 첫 칸만 적음. 사용자가 한 칸씩 고치고 다시 보내야 해서 고르지 않음. 선택지: DTO에 적은 순서. 검사기가 순서를 주지 않아 reflection이 필요해서 고르지 않음
  - 한 칸이 두 검사에 걸리면 둘 다 적음(공백 256자 제목 → "title: 255자 이하여야 합니다, title: 비어 있습니다")
  - 이유 문장은 검사기 기본 문구 대신 `GlobalExceptionHandler.constraintReasonOf`가 걸린 어노테이션으로 정함: `@NotBlank`·`@NotEmpty` "비어 있습니다", `@NotNull` "값이 없습니다", `@Size` "N자 이하여야 합니다", `@Min` "N 이상이어야 합니다", `@Max` "N 이하여야 합니다". 기본 문구는 서버의 언어 설정(Locale)에 따라 바뀌어서 쓰지 않음. 선택지: DTO 어노테이션마다 `message = "..."`를 적음. 요청 DTO 11개를 모두 고쳐야 하고 가짜 API와 맞출 문장이 흩어져서 고르지 않음
  - **body 타입 오류는 처음 틀린 칸 하나만 적음.** Jackson이 JSON을 앞에서부터 읽다가 처음 틀린 칸에서 멈추기 때문임. 원인 `MismatchedInputException`의 경로(`getPath`)와 받으려던 타입(`getTargetType`)으로 "evaluations[1].goalSharing: 정수여야 합니다"를 만듦. JSON 문법이 깨졌거나 body가 없거나 객체가 아니면(`[]`, `null`) 경로가 없어 앞 문장만 보냄
  - 주소 타입 오류는 `@PathVariable` 이름을 적음("주소의 값이 올바르지 않습니다. teamId: 정수여야 합니다"). 업로드의 `file` 칸이 없으면 "파일이 없습니다" 대신 `@NotNull`과 같은 "입력값이 올바르지 않습니다. file: 값이 없습니다"로 바꿈. 형식 오류 문장을 한 모양으로 맞추려는 것임
  - 서버 내부 정보는 넣지 않음. 칸 이름은 DTO에 있는 필드와 배열 번호뿐임(DTO에 없는 칸은 Jackson이 읽지 않아 경로에 오지 않음). 클래스 이름은 `typeReasonOf`가 한국어로 바꿈
  - Service의 `InvalidInputException` 문장(예: "종료일이 시작일보다 앞입니다")은 고치지 않음. 모두 사람이 읽는 문장이고 어느 값 때문인지 이미 드러남
  - 가짜 API: 요청 body를 읽는 공통 함수 `readBodyOrSendError`를 두고, 받을 모양(`'string'`, `'integer'`, `'date'`, `{ list }`, `{ fields }`)을 넘겨 Jackson처럼 JSON에 적힌 순서대로 읽다가 처음 틀린 칸을 알려 줌. `@Valid`는 `checkNotBlank` 같은 작은 함수가 칸마다 문장을 모으고 `sendIfNotValid`가 정렬해 보냄. body가 있는 handler 11개를 모두 이 방식으로 바꿈
  - probe 테스트(지움)로 실제 Jackson 동작을 확인하고 가짜 API를 맞춤. 그동안 가짜 API가 실제와 달랐던 곳 3개를 같이 고침
    - 날짜 칸의 `""`: 실제는 형식 오류(400 "요청 body의 형식이 올바르지 않습니다")인데 가짜는 null로 읽었음(`allow-coercion-of-scalars: false` 때문)
    - 글자 칸의 숫자·`true`: 실제는 글자 `"5"`, `"true"`로 받는데 가짜는 "비어 있음"으로 거절했음
    - 주소의 `-1`: 실제는 Long으로 읽어 404인데 가짜는 400이었음
  - 화면: 오류를 보여 주는 곳은 모두 `err.message`를 그대로 보여 줌. 고정 문구로 덮는 곳은 없음. 과제 만들기에서 날짜를 비우면 화면이 `""`를 보내 서버가 형식 오류로 날짜 한 칸만 알려 주던 것을, 빈 날짜는 `null`로 보내게 고침. 이제 빈 제목·시작일·종료일이 한 문장에 모두 나옴
  - 프론트엔드 웹 가이드: `modern-web-guidance`로 `search`(서버 검증 오류 표시, 빈 날짜 값)와 `retrieve forms`를 실행함. `forms`는 `required`와 제출 전 브라우저 검사, 첫 오류 칸으로 focus 이동을 권함. 이번 단위는 서버 문장이 범위이고, 고친 곳은 보내는 값 한 줄이라 적용하지 않음. 오류 띠는 이미 `role="alert"`라 "use `aria-live` for dynamic error announcements"는 지킴
- 만든 파일:
  - 백엔드: 테스트 `InvalidInputMessageRuleTests`(8개)
- 고친 파일:
  - 백엔드: `exception/GlobalExceptionHandler`(`handleMethodArgumentNotValid`, `handleMethodArgumentTypeMismatch`, `handleMissingServletRequestPart`, `handleHttpMessageNotReadable`, private `withFields`, `constraintReasonOf`, `typeReasonOf`, `jsonPathOf`)
  - 가짜 API: `frontend/mock/middleware.cjs`(`withFields`, `TYPE_REASONS`, `readValue`, `readBodyOrSendError`, `check...`, `sendIfNotValid`, `checkEvaluationItems`, `EVALUATIONS_BODY`, `parseIdOrSendError`에 이름 인자. body를 받는 handler 11개). `hasNotIntegerScore`, `isValidEvaluationItem`, `isValidEvaluationDraftItem`은 지움
  - 화면: `pages/AssignmentCreatePage.jsx`(빈 날짜를 null로)
  - 문서: `docs/api-draft.md`(공통에 "400의 틀린 칸", 응답이 바뀌는 곳에서 X-7 줄 지움), `docs/postman/`(요청 2개 더함, 400 요청 3개에 message 확인), `CLAUDE.md`, `TODO.md`, `README.md`
- 확인:
  - `gradlew.bat build` 성공. 테스트 154개, 실패 0(`InvalidInputMessageRuleTests` 8개 더함). 기존 테스트 중 옛 문장을 기대한 것은 없었음(모두 status와 code만 확인)
  - `npm run build`, `npm run lint` 성공
  - 같은 틀린 요청 42개를 실제 백엔드와 가짜 API에 보내 status, code, message, body의 칸 목록을 비교함. 41개 같음. 다른 1개는 X-4의 알려진 차이(`GET /teams/join/members`가 실제 400, 가짜 404)
  - Newman으로 Postman 모음 실행: 실제 백엔드와 가짜 API 모두 요청 141개, 확인 330개, 실패 0. 시험 뒤 `frontend/db.json`을 되돌림
  - 브라우저에서 실제 백엔드로 과제 만들기를 빈 칸으로 등록: 고치기 전 "요청 body의 형식이 올바르지 않습니다. startDate: yyyy-MM-dd 형식의 날짜여야 합니다", 고친 뒤 "입력값이 올바르지 않습니다. endDate: 값이 없습니다, startDate: 값이 없습니다, title: 비어 있습니다"가 오류 띠에 그대로 보임
  - rule-check: 금지 패턴 없음. Service 변경 없음. 새 private 메서드와 가짜 API 함수에 주석 있음
- 읽을 때 볼 곳:
  - `constraintReasonOf`의 `error.unwrap(ConstraintViolation.class)`. Spring의 `FieldError` 안에 검사기가 만든 원래 결과가 들어 있고, 거기서 걸린 어노테이션(`@Size` 등)을 꺼냄. `constraint instanceof Size size`는 "Size이면 size라는 이름으로 바로 씀"이라는 Java 16+ 문법임
  - `handleHttpMessageNotReadable`의 `e.getCause() instanceof MismatchedInputException cause`. Spring이 Jackson 오류를 한 번 감싸서 던지므로 원인(`getCause`)을 봐야 칸 경로가 있음
  - 가짜 API `readValue`가 자기 자신을 다시 부르는 곳(목록과 객체 안쪽). 실제 Jackson이 안쪽 칸으로 들어가며 읽는 것을 흉내 냄. `Object.hasOwn`은 `toString` 같은 기본 속성 이름을 받을 모양으로 착각하지 않으려고 씀
  - 정렬은 글자 비교라 `evaluations[10]`이 `evaluations[2]`보다 앞에 옴. 순서는 고정이지만 숫자 순은 아님
- 내가 채운 가정:
  - 문장 모양(`. `, `, `, `칸 이름: 이유`)과 이유 문장 9개는 내가 정함
  - 칸 이름은 JSON 필드 이름, `@Valid`는 모두 나열, 타입 오류는 한 칸
  - 업로드의 `file` 누락 문장을 "파일이 없습니다"에서 바꿈. 20MB 초과("파일은 20MB까지 올릴 수 있습니다")와 빈 파일("빈 파일은 올릴 수 없습니다")은 그대로 둠
  - `@Size`의 이유는 "자"로 적음. 지금 `@Size`는 모두 글자 칸에 있음. 목록에 `@Size`를 붙이면 문장을 고쳐야 함
  - 가짜 API에 남는 차이: `5.0`을 정수로 받음(기존), 아주 큰 수(2^53 이상)의 주소 번호, Java와 JavaScript의 공백 기준 차이(`trim`), 글자 칸에 온 소수의 글자 모양(`5.0` → 실제 "5.0", 가짜 "5"). JSON이 아닌 Content-Type 요청은 이번에 비교하지 않음(실제는 "요청의 형식이 올바르지 않습니다")

## 2026.11.12 X-1 교수 회원 유형 변경 경로
- 결정:
  - 2026.10.26 결정 "회원 유형은 처음 만들 때만 정함"을 고침. 구글 로그인할 **때마다** 회원 유형이 `STUDENT`이고 이메일이 `google.professor-emails`에 있으면(대소문자 무시) `PROFESSOR`로 올림. 내리지 않음. 관리 화면과 새 API 없음(TODO.md 추천안 그대로)
  - **조원·조장 기록(`TeamMember` 행)이 하나라도 있는 학생은 올리지 않음.** 오류 없이 학생으로 로그인함
    - 선택지 A 그대로 올림: 비용 없음. 하지만 교수가 조원·조장으로 남음. 학생만 조에 합류하고 학생만 조장이 되는 규칙(`TeamService` 합류 검사, `AssignmentService` 조장 검사)과 어긋나고, 과제 목록이 "내가 만든 과제"로 바뀌어 자기 조의 과제가 목록에서 사라짐(주소로는 여전히 조원으로 열림). 동료평가 대상·평가자에 교수가 섞임
    - 선택지 B 조 기록이 있으면 올리지 않음(고름): 비용은 `TeamMemberRepository.existsByMemberId` 조회 하나. "조원은 모두 학생"이 유지됨. 단점은 목록에 넣었는데도 조용히 학생으로 남는 것. 이 경우 교수용으로는 조에 들어가지 않은 계정을 쓰게 안내함(`backend/README.md`)
    - 선택지 C 올리면서 조 기록을 지움: 기록 보존 규칙(삭제·취소도 줄을 남김)과 정면으로 부딪혀서 고르지 않음
    - "조 기록"은 `TeamMember` 행 하나로 봄. 산출물, 기여 항목, 완료확인, 평가, 임시 저장, 알림은 모두 조원일 때만 생기고, 조에서 나가는 기능이 없어 `TeamMember` 행은 지워지지 않음
  - **바뀐 시각과 이력은 남기지 않음**
    - 선택지 A 남기지 않음(고름): Entity 변경 없음
    - 선택지 B `Member.memberTypeChangedAt` 칸 하나: 비용은 칸 하나와 ERD 수정. 볼 화면이 없음
    - 선택지 C 이력 Entity(역할·조장 이력처럼): Entity 하나와 저장소 하나. 역할·조장 이력은 평가 근거라서 남기는 것이지만, 유형 변경은 조 기록이 없는 회원에게만 일어나 평가 근거에 닿지 않음. 무엇이 바뀌었는지의 근거는 서버 설정의 교수 목록임
    - 나중에 필요하면 B를 Entity 변경으로 더함. 서버 로그는 O-8에서 정함
  - token: `JwtProvider.createToken`은 올린 뒤에 부르므로 올린 로그인의 token부터 `memberType` claim이 `PROFESSOR`임. 이미 발급된 옛 token은 claim이 `STUDENT`지만, `JwtAuthenticationFilter`는 token에서 회원 id(`sub`)만 꺼내고 Service는 DB에서 회원 유형을 읽음(`AssignmentService.create`·`findAllForMember`, `TeamService` 합류 검사). 그래서 옛 token으로도 바로 교수 권한이 적용되고 따로 처리할 것이 없음. 테스트로 확인함
  - 접근 범위 규칙 확인: "과제를 만든 교수인가"는 회원 유형이 아니라 `assignment.professor`의 id로 보고(`TeamAccessChecker.isProfessorOf`), "조원인가"는 `TeamMember` 행으로 봄. 올린 회원은 조 기록이 없으므로 새로 보이는 조 기록이 없음. "교수만 과제를 만든다"는 DB 유형으로 보므로 올린 뒤에만 만들 수 있음
  - 테스트의 구글 ID token: 기존에 구글 로그인 테스트가 없어서 따를 방식이 없었음. `@MockitoBean`(Spring Framework 6.2, `spring-boot-starter-test`에 이미 있음. 새 의존성 아님)으로 `GoogleTokenVerifier`를 대역으로 바꿈. 선택지: `@TestConfiguration`의 손으로 만든 대역 클래스. 이메일마다 돌려줄 값을 정하는 코드를 따로 써야 해서 고르지 않음. 교수 목록과 허용 도메인은 `@TestPropertySource`로 이 테스트만 `example.com`으로 바꿈
    - 이 두 설정 때문에 이 테스트는 다른 테스트와 다른 Spring context로 켜짐. `ddl-auto: create`가 `footprint_test` 스키마를 다시 만들고 시작 데이터를 다시 넣지만 id가 같아서 전체 테스트가 그대로 통과함
  - 가짜 API: 같은 규칙(목록의 학생, `teamMembers`에 행이 없으면 `PROFESSOR`로 바꿈)을 맞춤. 맞출 수 없는 부분은 token의 `memberType`임. 가짜 token은 `mock-token-<id>`라 유형을 담지 않음. 화면은 token을 풀어 읽지 않고 `GET /members/me`(DB의 유형)를 쓰므로 화면 동작은 같음
  - 화면: 고칠 것 없음. 유형으로 갈리는 곳(`AppLayout`, `HomePage`, `AssignmentDetailPage`, `TeamMembersPage`, `MeBox`)은 모두 `GET /members/me`의 `memberType`을 씀. 로그인 화면은 `AppLayout` 밖이라 로그인한 뒤 `AppLayout`이 새로 열리며 내 정보를 다시 불러옴. 이미 열려 있던 다른 탭은 새로 고침 전까지 옛 유형을 보여 줌(서버 권한은 DB 기준이라 문제 없음). `frontend/src`를 고치지 않아서 `modern-web-guidance`는 쓰지 않음
  - Postman: 구글 로그인은 모음에 없어서(ID token을 받을 수 없음) 고칠 것 없음
- 만든 파일:
  - 백엔드: 테스트 `ProfessorPromoteRuleTests`(8개)
- 고친 파일:
  - 백엔드: `entity/Member`(`promoteToProfessor`), `repository/TeamMemberRepository`(`existsByMemberId`), `service/AuthService`(`promoteIfListed`, `isProfessorEmail`, 생성자에 `TeamMemberRepository`), `application.yml`(`professor-emails` 주석)
  - 가짜 API: `frontend/mock/middleware.cjs`(`handleGoogleLogin`)
  - 문서: `docs/api-draft.md`(구글 로그인 "서버 쪽 규칙"), `CLAUDE.md`(업무 규칙의 회원 유형), `TODO.md`, `README.md`, `backend/README.md`("구글 로그인"), `infra/.env.example`(주석), 이 파일의 2026.10.26 구글 로그인 결정 줄에 바뀐 날짜를 덧붙임
- 확인:
  - `gradlew.bat build` 성공. 테스트 162개, 실패 0(`ProfessorPromoteRuleTests` 8개 더함): 목록의 학생이 다시 로그인하면 교수, 그 로그인 token claim과 `GET /members/me`가 교수, 바로 과제 생성 200 / 목록에 없으면 학생 / 목록에서 빠진 교수는 교수 / 대소문자 무시 / 조 기록이 있는 student01은 학생으로 남고 데모 과제를 그대로 봄 / 처음 로그인은 목록대로 / 올리기 전 token(claim STUDENT)으로 `GET /members/me`가 교수, 과제 생성 200 / 비밀번호 로그인은 유형을 바꾸지 않음
  - `npm run build`, `npm run lint` 성공
  - 가짜 API(db 복사본, 포트 3099): 목록 밖 학생으로 만든 뒤 목록에 넣고 다시 켜서 로그인 → `PROFESSOR`, 그 회원으로 과제 생성 200 / 목록에서 뺀 교수 → `PROFESSOR` 유지 / 목록에 넣은 student01(조 기록 있음) → `STUDENT`. `frontend/db.json`은 건드리지 않음
  - rule-check: 금지 패턴 없음(`@Autowired`는 다른 테스트와 같은 생성자 주입). 값을 바꾸는 `loginWithGoogle`에 `@Transactional` 있음. 새 메서드에 주석 있음. 실제 API와 가짜 API 응답 모양 같음
- 읽을 때 볼 곳:
  - `promoteIfListed`의 `member.promoteToProfessor()` 뒤에 `save`가 없는 이유: `@Transactional` 안에서 읽은 Entity는 값이 바뀌면 트랜잭션이 끝날 때 JPA가 UPDATE 함(변경 감지)
  - `loginWithGoogle`에서 `promoteIfListed`를 `createToken`보다 먼저 부르는 순서. 바꾸면 올린 로그인의 token에 옛 유형이 담김
  - `existsByMemberId`: 메서드 이름의 `MemberId`는 `member.id`를 따라감(다른 메서드와 같은 규칙). Spring Data가 `select ... limit 1` 모양의 존재 확인 쿼리를 만듦
  - 테스트의 `@MockitoBean`: Spring이 진짜 `GoogleTokenVerifier` 대신 Mockito 대역을 넣음. `when(...).thenReturn(...)`으로 정한 credential에만 계정을 돌려줌
- 내가 채운 가정:
  - 조 기록이 있는 학생은 올리지 않음(TODO.md에 없던 규칙). 오류를 내지 않고 학생으로 로그인시킴
  - 바뀐 시각과 이력은 남기지 않음
  - 새로 만드는 회원은 지금처럼 목록대로 바로 교수로 만듦(만든 뒤 올리는 두 단계로 바꾸지 않음)
  - 이미 있는 회원의 이메일 찾기(`findByEmail`)는 대소문자를 가림(기존 동작). 목록 비교만 대소문자를 무시함

## 2026.11.12 X-2 개인정보 고지와 동의
- 결정:
  - TODO.md 3절의 "개인정보 고지 문구와 보관 기간"은 관리자가 조율 세션에서 **"초안으로 진행"**으로 정함(2026.11.12). 동의 구조(화면, 동의 시각, API)는 지금 만들고, 문구와 보관 기간은 `frontend/src/constants/privacyNotice.js` 한 파일에 "확인 전 초안"으로 모음. **학교·담당 교수 확인 전 초안. 확인 뒤 이 파일만 고침**
  - API: `POST /members/me/privacy-consent`(회원 본인, body 없음, 응답은 `GET /members/me`와 같은 모양). `GET /members/me`에 `privacyConsentedAt` 추가(동의 전 `null`). 주소에 회원 번호가 없어서 남의 동의를 기록할 길이 없음
  - **이미 동의한 회원이 다시 부르면 200이고 처음 시각을 둠**
    - 선택지 A 200·처음 시각 유지(고름): 알림 읽음 표시(X-6)와 같은 방식. 두 탭에서 동의하거나 버튼을 두 번 눌러도 화면이 오류를 띄우지 않음. 동의 시각은 "처음 동의한 때"의 기록이라 덮어쓰지 않음
    - 선택지 B 409 `DUPLICATE`: 평가 제출·완료 보고처럼 "한 번만"을 오류로 알림. 하지만 동의는 같은 내용을 다시 하는 것이라 거절할 이유가 약하고, 화면이 409를 따로 처리해야 함
  - **"동의 전에는 다른 화면으로 가지 못함"은 화면에서만 막음. 서버는 동의 전 요청을 거부하지 않음**
    - 선택지 A 화면만(고름): `RequirePrivacyConsent`(page)가 `getMe`의 `privacyConsentedAt`을 보고 `/privacy`로 보냄. 비용은 화면 파일 2개와 `App.jsx` Route 한 겹. 기존 테스트 169개, Postman 모음, 가짜 API의 다른 주소는 그대로 동작함. TODO.md 규칙의 문장도 "다른 화면으로 가지 못함"임
    - 선택지 B 서버도 막음: 권한 검사를 Service의 보이는 메서드 호출로 둬야 해서(AOP·숨은 필터 금지) 로그인·내 정보·동의를 뺀 API 38개가 부르는 Service 메서드마다 `memberConsentChecker.checkConsented(memberId)`를 더해야 함. 응답 code는 새로 만들 수 없어 403 `FORBIDDEN`을 써야 하는데 "다른 조의 기록" 403과 섞임. 가짜 API의 모든 handle 함수에도 같은 검사를 넣어야 함. 동의 전 회원으로 로그인하는 테스트가 없어 기존 테스트는 깨지지 않지만 새 테스트가 Service마다 필요함
    - 선택지 C `JwtAuthenticationFilter`에서 막음: 한 곳이지만 "숨은 필터"라서 CLAUDE.md "금지"(AOP·커스텀 어노테이션으로 숨기는 권한 검사)의 취지에 걸림. 필터가 매 요청 DB를 읽게 됨
    - 동의는 고지를 읽었다는 확인이고, 조의 기록을 볼 권한은 이미 조원·교수 검사가 막음. API를 직접 부르는 사람(Postman)은 화면의 고지를 보지 않는 개발자라 서버에서 막아 얻는 것이 작음. 서버가 막지 않는다는 것은 `PrivacyConsentRuleTests.serverDoesNotBlockBeforeConsent`와 `docs/api-draft.md`에 적어 둠
  - 화면 구조: `RequireLogin`(token) 안쪽에 `RequirePrivacyConsent`를 한 겹 더 둠. `/privacy`는 동의 전에도 열려야 해서 `RequireLogin` 안, `RequirePrivacyConsent` 바깥. 초대 링크(`/join/:inviteCode`)도 동의 전에는 막힘. `state.from`으로 원래 주소를 넘겨 동의 뒤 그곳으로 감(로그인 → 고지 → 동의 → 원래 주소). `RequirePrivacyConsent`는 데이터를 불러오고 state를 가져서 `components`가 아니라 `pages`에 둠(`AppLayout`과 같은 이유)
  - 동의하지 않는 길: "동의하지 않고 로그아웃" 버튼 하나. 동의 거부를 서버에 기록하지 않음
  - **화면에 초안 표시를 넣음**: 고지 맨 위에 `lockband` 띠로 "학교·담당 교수의 확인을 받기 전 초안" 문구. 실제 학생이 보고 확정된 안내로 오해하지 않게 함. 확인이 끝나면 `PRIVACY_NOTICE_DRAFT_BANNER`를 `null`로 바꾸면 띠가 사라짐
  - 초안 문구: 저장하는 것은 `backend/.../entity`의 칸을 보고 적음(회원의 이메일·이름·유형·동의 시각, 조 참여와 역할·역할 이력·조장 이력, 산출물의 파일 내용·이름·크기·시각·역할명, 기여 항목과 보고 메모, 완료확인, 동료평가 점수·의견·제출 시각과 임시 저장, 알림). 받지 않는 것(비밀번호, 학번, 전화번호, 프로필 사진)과 브라우저에 남는 것(sessionStorage의 token, localStorage의 테마)도 적음. 누가 보는지는 접근 범위 규칙(같은 조 조원, 과제를 만든 교수, 평가 결과는 교수만, 임시 저장·알림은 본인만, 초대 링크 미리보기의 조장 이름)대로 적음. 학교·교수가 승인했다는 표현과 법 조항을 근거로 단정하는 표현은 넣지 않음
  - 보관 기간 초안: "과제가 속한 학기의 성적 처리와 이의 신청 기간이 끝날 때까지". 지금은 자동 삭제와 탈퇴 기능이 없다는 사실을 그대로 적고, 지우는 방법은 확인 뒤 정한다고 적음
  - **고지의 판(버전)은 이번 범위에 넣지 않음**
    - 선택지 A 넣지 않음(고름): 문구가 확인 뒤 한 번 바뀔 것이 확실한데, 그때 다시 동의를 받을지는 학교·교수 확인과 함께 정할 일임. 넣으면 `Member`에 판 번호 칸, 상수 파일에 판 번호, 비교 규칙이 더해짐
    - 선택지 B `privacyNoticeVersion` 칸을 더하고 화면이 상수의 판과 다르면 다시 고지: 칸 하나와 API 요청 body(판 번호)가 생김. "클라이언트가 보낸 값"을 받는 첫 동의 API가 되어 검증 규칙도 정해야 함
    - 확정 문구로 바꿀 때 이미 동의한 회원에게 다시 받아야 하면 B를 그때 더함. 데모는 서버를 켤 때마다 데이터베이스를 새로 만들어서(`ddl-auto: create`) 지금은 문제가 드러나지 않음
  - 시작 데이터: 임지아(student10)만 동의 전, 나머지 10명은 동의한 상태(`DataInitializer.saveMember`, `db.json`). 모두 동의 전이면 시연과 조원 개발 때 계정마다 고지 화면을 거쳐야 하고, 모두 동의하면 시작 계정으로 고지 화면을 시험할 길이 없음. 임지아는 2조의 일반 조원이라 다른 흐름(조장, 평가 단계 과제, 알림 테스트)에 쓰이지 않음. 서버는 동의 전 요청을 막지 않으므로 기존 테스트·Postman 모음에 영향 없음. 시작 데이터의 동의 시각은 실제 백엔드가 켤 때의 서버 시각, 가짜 API는 `2026-10-25T09:00:00`(다른 시작 시각과 같은 값)
  - 회원 탈퇴, 동의 철회, 기록 삭제는 TODO.md 2절 "명세 규칙을 바꿔야 하는 것"이라 만들지 않음
  - 프론트엔드 웹 가이드(`modern-web-guidance`): `privacy`, `accessibility`, `content-first-noise-reduction`을 찾아봄
    - `privacy`: "DON'T use deceptive patterns (dark patterns) to trick users into giving consent" → 동의와 로그아웃을 같은 크기 버튼으로 나란히 두고 미리 체크된 칸을 두지 않음. "DON'T bury data collection explanations in long, complex privacy policies" → 구역 4개(저장하는 것, 쓰는 목적, 누가 보는지, 보관 기간)의 짧은 목록으로 씀
    - `accessibility`: 제목은 h1 하나와 h2(구역), 항목은 `ul`. 불러오는 중 표시는 live region이 아니라 `<progress>`("Don't use live regions for interstitial states like Loading…")로 둠. 기존 화면은 `aria-busy` 상자와 글자를 씀(이번 단위에서 만든 두 화면에만 적용). 오류는 기존처럼 `role="alert"`
    - `content-first-noise-reduction`: 페이지가 열릴 때 막는 화면은 줄이라는 가이드지만, 고지 동의는 관리자가 정한 필수 단계(TODO.md "동의 전에는 다른 화면으로 가지 못함")라서 모달이 아니라 전체 화면 page로 둠
- 만든 파일:
  - 백엔드: 테스트 `PrivacyConsentRuleTests`(7개)
  - 화면: `frontend/src/pages/PrivacyNoticePage.jsx`, `frontend/src/pages/RequirePrivacyConsent.jsx`, `frontend/src/constants/privacyNotice.js`
- 고친 파일:
  - 백엔드: `entity/Member`(`privacyConsentedAt`, `hasConsentedToPrivacy`, `consentToPrivacy`), `service/MemberService`(`consentToPrivacy`), `controller/MemberController`(`POST /members/me/privacy-consent`), `dto/MemberResponse`(`privacyConsentedAt`), `config/DataInitializer`(`PRIVACY_PENDING_EMAIL`)
  - 가짜 API: `frontend/mock/middleware.cjs`(`toMemberResponse`, `handleConsentToPrivacy`, 구글 로그인의 새 회원), `frontend/db.json`(회원 11명에 `privacyConsentedAt`)
  - 화면: `frontend/src/App.jsx`(`/privacy`, `RequirePrivacyConsent` Route), `frontend/src/api/auth.js`(`consentToPrivacy`), `frontend/src/index.css`(`.notice-box`, `.notice-section`)
  - Postman: 모음에 "8. 개인정보 고지 동의 (X-2)" 폴더(요청 5개)와 "내 정보 (교수)"의 동의 시각 test, `docs/postman/README.md`
  - 문서: `docs/api-draft.md`, `CLAUDE.md`(업무 규칙의 기록 시각과 개인정보 고지), `TODO.md`(순서 18, 단위별 규칙, 늘어나는 데이터, 2절 회원 탈퇴, 3절), `README.md`, `backend/README.md`(시작 계정), `frontend/README.md`(가짜 API), `docs/erd.md`(`MEMBER`), `docs/architecture.md`(프론트엔드 구조 그림과 설명)
- 확인:
  - `gradlew.bat build` 성공. 테스트 22개 클래스 169개, 실패 0(`PrivacyConsentRuleTests` 7개 더함): 동의 전 `null`과 시작 계정의 동의 시각 / 동의하면 서버 시각이 저장되고 `GET /members/me`에도 같은 시각 / body로 보낸 2000년 시각은 무시 / 다시 동의해도 처음 시각(1.1초 기다린 뒤 비교) / 다른 회원의 동의 시각은 그대로 / token 없음 401 / 동의 전에도 `GET /assignments` 200(서버는 막지 않음)
  - `npm run build`, `npm run lint` 성공
  - Newman: 실제 백엔드(새로 켠 서버)와 가짜 API 모두 요청 146개, assertion 341개, 실패 0. 실행 뒤 `frontend/db.json`을 되돌림
  - 브라우저(가짜 API, 실제 백엔드 각각): 로그아웃 상태로 `/join/demo-team-2` → `/login` → student10 로그인 → `/privacy`(초안 띠, h1 하나와 h2 4개) → "동의합니다" → `/join/demo-team-2` 미리보기. 가짜 API에서는 동의 전에 `/teams/2`를 직접 열어도 `/privacy`로 가고 동의 뒤 `/teams/2`로 돌아옴. 동의한 뒤 `/privacy`를 열면 동의 시각과 "돌아가기"만 보임. 375px 폭에서 가로 스크롤 없음
  - rule-check: 금지 패턴 없음(`@Autowired`는 다른 테스트와 같은 생성자 주입). 값을 바꾸는 `consentToPrivacy`에 `@Transactional` 있음. 클라이언트 시각을 받지 않음(`@RequestBody` 없음). 새 클래스·메서드에 주석 있음. 실제 API와 가짜 API 응답 모양 같음(칸 순서까지). 고칠 것 없었음
- 읽을 때 볼 곳:
  - `MemberService.consentToPrivacy`에 `save`가 없는 이유: `@Transactional` 안에서 읽은 Entity의 값이 바뀌면 트랜잭션이 끝날 때 JPA가 UPDATE 함(변경 감지). X-1의 `promoteIfListed`와 같음
  - `MemberController.consentToPrivacy`에 `@RequestBody`가 없음. 그래서 body에 무엇을 보내도 서버가 읽지 않음
  - `App.jsx`의 Route 겹침: `RequireLogin` → (`/privacy` | `RequirePrivacyConsent` → (`/join` | `AppLayout` → 화면)). `/privacy`가 `RequirePrivacyConsent` 바깥에 있어야 동의 전에도 열림. 안에 넣으면 `/privacy`로 보내고 다시 `/privacy`로 보내는 무한 반복이 됨
  - `RequirePrivacyConsent`는 들어올 때 한 번만 `getMe`를 부름. 안쪽 화면끼리 옮겨 다닐 때는 다시 만들어지지 않음. 고지 화면에서 동의하고 돌아오면 바깥(`/privacy`)에서 들어오는 것이라 새로 만들어지며 다시 불러옴
  - `<progress>`에 `value`가 없으면 "끝을 모르는 진행 막대"로 그려짐
- 내가 채운 가정:
  - 다시 동의하면 200·처음 시각 유지(409 아님)
  - 서버는 동의 전 요청을 막지 않음(화면만 막음)
  - 고지의 판(버전)은 저장하지 않음. 문구가 바뀌어도 이미 동의한 회원에게 다시 묻지 않음
  - 화면에 초안 표시 띠를 둠
  - 시작 데이터는 임지아(student10)만 동의 전
  - 초안 문구의 보관 기간("학기의 성적 처리와 이의 신청 기간이 끝날 때까지")과 "광고나 다른 서비스에 쓰지 않고 서비스 밖으로 보내지 않음", "궁금한 점은 담당 교수에게 문의" 문장. 모두 학교·담당 교수 확인 대상
  - 이미 동의한 회원이 고지를 다시 볼 메뉴 링크는 두지 않음. 주소 `/privacy`로는 열림
