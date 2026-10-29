# 발자국 데모 (footprint-demo)

조별과제 과정기록 & 평가지원 서비스 "발자국"의 **12.11 데모**를 만드는 저장소다.
목표는 11.14(토)까지 `과제 생성 → 조원 합류·역할 배정 → 산출물 업로드 → 동료평가 입력` 흐름 전체가 동작하는 것.
12.15 PBL 결과물 제출의 대비이고, 11.16부터 조원 코드를 이 데모의 같은 파일에 끼워 넣는 기준이 된다.

## 이 파일은 무엇인가

이 파일은 **AI 코딩 도구(Claude Code)에 주는 이 저장소의 작업 규칙**이다. 사람이 읽는 안내는 `README.md`에 있다.
사람에게 보내는 지시문이 아니라 도구 설정이라서, 아래 글은 명령형 반말로 적혀 있다.

- **"관리자"** 는 이 저장소를 만들고 있는 사람(저장소 소유자)을 가리키는 호칭이다. 역할 이름이고, 권한 설정과는 관계가 없다.
- **`../footprint-study`** 는 같은 사람의 **비공개 학습 저장소**다. 이 저장소 밖에 있고 공개되지 않는다. 아래에서 그 폴더의 문서를 불러오는 줄(`@../footprint-study/...`)은 그 폴더가 있을 때만 쓰이고, 없으면 그냥 무시된다. 이 저장소만 clone 해도 코드는 그대로 빌드되고 실행된다.
- 규칙에 붙은 **날짜(예: 2026.10.26 관리자 결정)** 는 그 규칙을 정한 날이다. 같은 결정의 배경과 선택지는 `docs/worklog.md`에 남긴다.
- "묻지 않고 진행한다"로 적힌 항목은 **AI가 중간에 멈춰 묻지 않고 추천안으로 만들고 기록까지 남긴다**는 뜻이다. 사람의 검토를 건너뛴다는 뜻이 아니다. 관리자가 그날 생성된 코드를 모두 읽는 것이 전제다.

## 대화 방식

- 한국어 반말(~해 체). 사용자 호칭은 "관리자". 영어 기술 용어는 그대로 쓴다.
- 간결하게. 이모티콘 금지. 칭찬으로 시작하지 않는다. 문제 → 근거 → 수정안 순서.
- 관리자가 개념이나 "왜"를 물으면 답을 바로 주지 말고 단계별로 이끈다(질문으로 짚고, 비유·예시 사용).
- 관리자가 결정한 적 없는 빈틈을 채웠으면 응답 끝에 **"내가 채운 것"** 목록을 따로 붙인다.
- 문서끼리 어긋난 것을 발견하면 작업 전에 먼저 알린다. 조용히 한쪽으로 맞추지 않는다.

## 이 코드를 읽을 사람

팀 6명 전원 Spring Boot·React 초심자다. 이 코드는 조원이 복사해서 읽을 코드다.
**영리함보다 단순함. 같은 패턴을 일관되게.** 짧은 코드보다 따라 읽을 수 있는 코드가 낫다.
관리자는 생성된 코드를 그날 안에 모두 읽고 설명할 수 있어야 한다. 생성량보다 읽을 수 있는 양이 기준이다.

## 기준 문서 (충돌하면 위가 이김)

1. 이 대화에서 관리자가 방금 한 결정
2. `docs/demo-scope.md` — 시연 범위, 업무 규칙, 시작 데이터, 동료평가 문장 (아래에서 불러옴)
3. 학습 저장소의 결정 기록 `../footprint-study/docs/decisions.md` (아래에서 불러옴)
4. 학습 저장소의 API 명세 `../footprint-study/docs/api.md`와 골격 `../footprint-study/skeleton/` — **있으면** 그대로 따른다. 파일 이름, 주소, 응답 모양까지 같게.

@docs/demo-scope.md
@../footprint-study/docs/decisions.md

API 명세가 아직 없으면 코드보다 **`docs/api-draft.md`를 먼저 쓴다.** 관리자 확인을 기다리지 않고 바로 만든다(2026.10.25 관리자 결정, 아래 "묻지 않고 추천안으로 진행하고 기록할 것").

## 고정 스택과 구조

- 백엔드: Java 21, Spring Boot 3.5.16, Gradle(Groovy) wrapper 8.14.3, JPA, Spring Security(JWT), Lombok, PostgreSQL 17
- 파일 저장: MinIO(S3 호환). 코드는 S3 API로만 접근한다.
- 프론트엔드: Node.js 24, Vite 8, React 19, JavaScript, React Router, 가짜 API는 json-server 0.17.4
- 시작점은 `../footprint-study/example/backend`, `example/frontend`를 복사한 것. start.spring.io로 새로 만들지 않는다(4.x만 제공).

```
backend/    Spring Boot. 패키지 com.footprint.backend, rootProject.name = 'footprint-backend'
frontend/   React. src/pages, src/components, src/api, src/constants
infra/      docker-compose.yml(PostgreSQL, MinIO), .env.example
docs/       demo-scope.md, api-draft.md, worklog.md
```

백엔드 패키지는 계층형: `controller`, `service`, `repository`, `entity`, `dto`, `exception`, `config` (+ 인증은 `security`).

## 코드 규칙 — 백엔드

- 의존성 주입은 생성자 주입만. 생성자를 직접 쓴다(`@RequiredArgsConstructor` 안 씀). 필드는 `final`.
- Lombok은 Entity의 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`까지만.
- 값을 바꾸는 Service 메서드에 `@Transactional`.
- DTO는 `record`. 요청 하나에 DTO 하나(`XxxCreateRequest`). 응답은 Entity가 아니라 응답 DTO.
- Entity → DTO 변환은 Controller에서 `XxxResponse.from(entity)`. Service는 Entity를 돌려준다.
- 목록 응답은 연결된 데이터를 펼친 모양. 예: 조원 목록 `{ "id", "name", "roleName" }`.
- API 경로는 복수형(`/assignments`, `/teams/{teamId}/members`).
- 오류 응답은 `ApiErrorResponse { code, message }` 하나. 처리는 `GlobalExceptionHandler` 한 곳. handler 이름은 `handle` + 예외 이름에서 `Exception` 뺀 것.
- `code` 목록: `NOT_FOUND` 404, `INVALID_INPUT` 400, `UNAUTHORIZED` 401, `FORBIDDEN` 403, `DUPLICATE` 409, `SERVER_ERROR` 500(2026.10.26 관리자 결정). 새 code가 필요하면 먼저 묻는다.
- 전용 예외: `NotFoundException`, `DuplicateException`, `ForbiddenException`. `IllegalArgumentException`을 404로 바꾸지 않는다.
- 시작 데이터는 `config/DataInitializer`(`CommandLineRunner`). 개발 중 `ddl-auto: create`.
- 설정값은 `application.yml`에 `${ENV:개발용기본값}` 형태. 조원이 환경 변수 없이 실행돼야 한다. 예: `${JWT_SECRET:...}`.
- 날짜는 `LocalDate`(JSON `yyyy-MM-dd`), 기록 시각은 서버가 `LocalDateTime`으로 저장. 클라이언트가 보낸 시각은 받지 않는다.

## 코드 규칙 — 프론트엔드

- 컴포넌트 하나에 파일 하나. 데이터를 불러오고 state를 갖는 건 page, component는 props로 받아 그린다.
- `fetch`는 `src/api/`에만. 공통 함수 `src/api/client.js`를 거친다. 실패하면 body를 읽어 `status`, `code`를 담은 오류를 던진다. FormData면 Content-Type을 붙이지 않는다.
- 비동기는 예시 프로젝트처럼 `.then` 방식. 데이터를 불러오는 화면은 `loading`, `error` state를 둔다.
- 다른 값으로 계산되는 값은 state로 두지 않는다. 배열 state는 새 배열로 교체.
- API 주소는 `VITE_API_URL` 하나. `.env`는 저장소에 올리고 개인 설정은 `.env.local`.
- 가짜 API(`db.json`, `mock/routes.json`, `mock/middleware.cjs`)의 응답 모양은 실제 API와 똑같이 맞춘다. API를 바꾸면 가짜 API도 같은 작업 안에서 고친다.
- `mock/middleware.cjs`는 CommonJS(`require`, `module.exports`)로 쓴다. json-server 0.17.4가 `require`로 읽는데 `package.json`이 `"type": "module"`이라 `.js`로 두면 읽히지 않는다.

## 금지

이 목록은 기술의 좋고 나쁨을 가린 것이 아니다. 초심자 6명이 읽고 고칠 코드에서 추상 계층과 숨은 동작을 줄이려고 이 데모에서만 빼는 것이다. 12.12 이후 다시 본다.

- MapStruct, QueryDSL
- 상속으로 만드는 공통 부모 클래스(`BaseEntity`, `BaseTimeEntity`, 추상 Controller/Service 등)
- 공통 응답 감싸기 클래스(`ApiResponse<T>`, `Result<T>` 등). 성공 응답은 DTO를 그대로 돌려준다.
- 상태 관리 라이브러리(Redux, Zustand, Recoil, Jotai, React Query/TanStack Query 등), axios
- AOP·커스텀 어노테이션으로 숨기는 권한 검사. 권한 검사는 Service에서 메서드 호출로 보이게 한다.
- 점수 자동 계산, 평균, 합계, 순위, 가중치, 진척률. 화면에도 API에도 두지 않는다.
- LLM 기능, 평가 초안 생성
- `docs/demo-scope.md`의 "만들지 않는 것"에 있는 기능

## 묻지 않고 추천안으로 진행하고 기록할 것 (2026.10.25 관리자 결정)

중간에 묻지 않는다. 아래는 추천안으로 정하고 진행하되, 정한 내용을 `docs/worklog.md`의 그 단위 항목 "결정" 줄에 선택지·이유와 함께 남긴다. API는 `docs/api-draft.md`에 먼저 적고 바로 만든다.
- 새 의존성 추가(Gradle, npm 모두). 라이브러리 이름, 이유, 대안을 worklog에 적는다.
- `docs/demo-scope.md`의 "미정" 항목에 걸리는 코드. 선택지와 비용, 고른 것을 worklog에 적는다.
- Entity 필드·연결 변경, API 주소·응답 모양 변경

## 반드시 먼저 말할 것

- `git push`, 원격 저장소 설정은 하기 전에 반드시 관리자에게 말한다. **PR은 절대 자동으로 만들지 않는다.**
- 외부 플랫폼 작업(메일 발송 등).
- `../footprint-study`는 **읽기만** 한다. 고치거나 commit 하지 않는다. 데모 관련 내용을 학습 저장소와 그 PR에 쓰지 않는다.
- 이 저장소는 GitHub public 저장소다(2026.10.26 관리자 결정). 비밀값, 실제 사람의 이메일·이름은 commit 하지 않고 gitignore된 `.env`나 환경 변수로 넣는다. 개발용 기본값(`change-me` 등)만 저장소에 둔다.

## 주석

- 클래스와 public 메서드마다 "무엇을 하는지" 한 줄 한국어 주석. 짧은 완결 문장으로 쓴다(번역기로 읽는 조원이 있다).
- 초심자가 막힐 만한 줄(어노테이션 의미, 트랜잭션, 연관관계, 필터 순서 등)에는 이유 한 줄.
- 주석으로 코드를 반복 설명하지 않는다.

## 작업 방식

- 한 번에 **단위 업무 하나**(예: T-1-2 링크로 합류)를 백엔드 → 가짜 API → 화면 순서로 끝까지. 여러 기능을 한 번에 생성하지 않는다.
- 시작 전에 무엇을 만들지, 만들 파일 목록, 걸리는 미정 항목을 3~6줄로 말하고 묻지 않고 시작한다.
- 끝나면 확인한다: `backend`에서 빌드(`gradlew.bat build` 또는 `./gradlew build`), `frontend`에서 `npm run build`, `npm run lint`. 실패한 채로 끝내지 않는다.
- 끝나면 스스로 규칙 검사(rule-check 기준)를 하고 걸린 것은 고친다.
- 끝나면 `docs/worklog.md`에 오늘 날짜로 적는다: 결정, 만든·고친 파일, 관리자가 읽을 때 볼 곳(초심자가 막힐 곳), 내가 채운 가정.
- 단위 업무 하나가 확인까지 끝나면 commit 한다. commit 메시지는 영어 conventional 스타일(`feat: add team join by invite code`).

## 실행 환경

관리자 컴퓨터는 Windows다. 안내 명령은 PowerShell 기준으로 쓰고, 다르면 macOS를 한 줄 덧붙인다.
관리자는 Arch Linux 노트북에서도 작업한다(2026.10.28). 그 노트북에서 실행 중이면 명령은 bash 기준으로 쓴다(`./gradlew`). 저장소 문서의 안내는 그대로 Windows·macOS 기준으로 둔다.
DB와 MinIO는 `infra/`에서 `docker compose up -d`. 접속값은 `infra/.env.example`.
학습 저장소의 `infra`와 포트(5432, 9000, 9001)가 같다. 둘을 동시에 켜지 않는다. 켜져 있으면 학습용을 먼저 끄라고 안내한다.
