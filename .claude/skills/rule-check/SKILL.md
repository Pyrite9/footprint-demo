---
name: rule-check
description: 데모 코드가 CLAUDE.md 규칙과 데모 범위를 지키는지 검사한다. 관리자가 /rule-check 로 부르거나 단위 업무 하나를 끝낸 직후 쓴다.
argument-hint: "[폴더 — 비우면 backend, frontend 전체]"
---

`$ARGUMENTS`(없으면 `backend/src`, `frontend/src`)를 검사한다.

- 단위 업무를 끝낸 직후에 스스로 검사할 때: 보고한 뒤 이번 단위에서 만든 코드의 "수정 필요"를 바로 고치고 다시 빌드한다(CLAUDE.md "작업 방식"). 고친 것은 그 단위의 worklog 항목에 적는다. 다만 기존 코드의 규칙을 따른 결과로 문서와 어긋난 것과 손대지 않은 기존 코드에서 걸린 것은 고치지 않고 최종 답변에 불일치로 명세한다. 이번 단위에서 만든 코드가 "금지" 항목에 걸리면 기존 코드를 따랐더라도 고친다.
- 관리자가 `/rule-check`로 직접 부를 때: 고치지 말고 보고만 한다. 관리자가 "고쳐"라고 하면 그때 고친다.

## 금지 패턴 (grep으로 찾는다)

- 백엔드: `mapstruct`, `querydsl`, `extends Base`, `@MappedSuperclass`, `ApiResponse<`, `Result<`, `@RequiredArgsConstructor`, `@Autowired`(필드 주입), `@PreAuthorize`, `@Aspect`
- 프론트엔드: `redux`, `zustand`, `recoil`, `jotai`, `@tanstack`, `axios`, `async `/`await `(예시와 달리 `.then`을 써야 함), `src/api/` 밖의 `fetch(`
  - 다만 `frontend/src`의 HTML·CSS·클라이언트 JS가 `modern-web-guidance` 가이드를 따른 결과이고 worklog에 가이드 id가 적혀 있으면 "코드 규칙 — 프론트엔드"·단순함 위반으로 잡지 않는다(CLAUDE.md "프론트엔드 웹 가이드"). 위의 금지 라이브러리는 그대로 잡는다
- 공통: `average`, `avg`, `sum(`, `rank`, `score` 합산·평균 계산 코드

## 구조 검사

- Controller가 Entity를 그대로 돌려주는 곳
- Entity → DTO 변환이 Service에 있는 곳
- 값을 바꾸는데 `@Transactional`이 없는 Service 메서드
- 조의 기록을 다루는데 접근 범위 검사(조원인지, 과제를 만든 교수인지)가 없는 Service 메서드
- 클라이언트가 보낸 시각을 저장하는 곳
- 주석 없는 클래스·public 메서드
- 실제 API와 가짜 API(`db.json`, `mock/`)의 응답 모양이 다른 곳
- `TODO.md`의 "만들지 않기로 한 것", 2절 "추가 기능 제안", "명세 규칙을 바꿔야 하는 것"에 해당하는 코드(관리자가 1절로 옮긴 것은 빼고)
- 만든 단위 업무가 `TODO.md` 1절의 규칙과 다른 곳. 아직 만들지 않은 단위의 규칙은 검사하지 않음

## 보고 형식

등급별로 나눈다: **수정 필요 / 확인 필요 / 선택 사항**. 항목마다 `파일:줄`, 무엇이 문제인지 한 줄, 수정안 한 줄. 문제가 없으면 "없음"이라고만 쓴다.
