---
name: rule-check
description: 데모 코드가 CLAUDE.md 규칙과 데모 범위를 지키는지 검사한다. 관리자가 /rule-check 로 부르거나 단위 업무 하나를 끝낸 직후 쓴다.
argument-hint: "[폴더 — 비우면 backend, frontend 전체]"
---

`$ARGUMENTS`(없으면 `backend/src`, `frontend/src`)를 검사한다. 고치지 말고 먼저 보고한다.

## 금지 패턴 (grep으로 찾는다)

- 백엔드: `mapstruct`, `querydsl`, `extends Base`, `@MappedSuperclass`, `ApiResponse<`, `Result<`, `@RequiredArgsConstructor`, `@Autowired`(필드 주입), `@PreAuthorize`, `@Aspect`
- 프론트엔드: `redux`, `zustand`, `recoil`, `jotai`, `@tanstack`, `axios`, `async `/`await `(예시와 달리 `.then`을 써야 함), `src/api/` 밖의 `fetch(`
- 공통: `average`, `avg`, `sum(`, `rank`, `score` 합산·평균 계산 코드

## 구조 검사

- Controller가 Entity를 그대로 돌려주는 곳
- Entity → DTO 변환이 Service에 있는 곳
- 값을 바꾸는데 `@Transactional`이 없는 Service 메서드
- 조의 기록을 다루는데 접근 범위 검사(조원인지, 과제를 만든 교수인지)가 없는 Service 메서드
- 클라이언트가 보낸 시각을 저장하는 곳
- 주석 없는 클래스·public 메서드
- 실제 API와 가짜 API(`db.json`, `mock/`)의 응답 모양이 다른 곳
- `TODO.md`의 "만들지 않기로 한 것"과 "2단계 기능"에 해당하는 코드

## 보고 형식

등급별로 나눈다: **수정 필요 / 확인 필요 / 선택 사항**. 항목마다 `파일:줄`, 무엇이 문제인지 한 줄, 수정안 한 줄. 문제가 없으면 "없음"이라고만 쓴다.
관리자가 "고쳐"라고 하면 그때 고친다.
