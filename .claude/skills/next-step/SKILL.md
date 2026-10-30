---
name: next-step
description: 데모의 다음 작업 단위(단위 업무 하나)를 골라 계획을 세운다. 관리자가 /next-step 으로 부를 때만 쓴다.
disable-model-invocation: true
argument-hint: "[단위 업무 ID, 예: T-1-2 — 비우면 다음 순서]"
---

다음 작업 단위를 정한다. 아직 코드를 쓰지 않는다.

1. `docs/worklog.md`와 `git log --oneline -15`로 어디까지 됐는지 확인한다.
2. `$ARGUMENTS`가 있으면 그 단위 업무를, 없으면 `TODO.md` 1절("미룬 단위 업무") 순서에서 다음 것을 고른다.
3. `../footprint-study/docs/api.md`가 있으면 해당 API 항목을, 없으면 `docs/api-draft.md`를 읽는다. 해당 API가 어디에도 없으면 api-draft에 추가할 초안부터 제시한다.
4. 아래 형식으로 답하고, 묻지 않고 바로 시작한다(CLAUDE.md "묻지 않고 추천안으로 진행하고 기록할 것", 2026.10.25 관리자 결정).

```
대상: <단위 업무 ID와 이름>
API: <method 주소> — 요청/응답 모양 한 줄
만들 파일: backend … / mock … / frontend …
걸리는 미정 항목: <TODO.md "아직 정하지 않은 것"의 항목, 없으면 없음>
막힐 만한 곳: <초심자 기준 1~2개>
예상 분량: <파일 수, 대략 줄 수>
```

5. 미정 항목에 걸리면 선택지 2~3개와 각각의 비용을 적고, 추천안을 골라 진행한다. 고른 것과 이유는 `docs/worklog.md`의 그 단위 항목 "결정" 줄에 남긴다.
6. 분량이 파일 8개를 넘으면 둘로 나눠 앞의 것부터 한다. 관리자가 그날 다 읽을 수 있어야 한다.
