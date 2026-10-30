# Postman 모음

발자국 데모의 API 17개 중 16개를 요청 29개로 데모 흐름 순서에 맞춰 모았습니다. 요청마다 상태 코드와 응답을 확인하는 test가 붙어 있습니다.
구글 로그인(`POST /auth/google`)은 넣지 않았습니다. 구글이 발급한 ID token을 Postman에서 받을 수 없기 때문입니다. 모음은 시작 계정의 이메일·비밀번호 로그인을 씁니다.

| 파일 | 내용 |
|---|---|
| `footprint-demo.postman_collection.json` | Postman 모음 (Collection v2.1) |
| `sample-upload.txt` | 산출물 업로드 요청이 올리는 시험 파일 |

## 순서

| 폴더 | 내용 |
|---|---|
| 0. 로그인 | 교수, 조장(student01), 조원(student02), 다른 조 학생(student06)으로 로그인해 token을 저장합니다 |
| 1. 과제 | 교수가 새 과제(조 2개)를 만들고 목록·상세를 봅니다 |
| 2. 조 운영 | 조장이 초대 코드를 발급하고, 조원이 합류하고, 조장이 역할을 지정합니다 |
| 3. 산출물 | 조원이 파일을 올리고, 교수가 목록을 보고 내려받습니다 |
| 4. 동료평가 | 조원이 조장을 평가해 제출합니다. 다시 내면 409입니다 |
| 5. 오류 예시 | 401, 403, 404, 400 응답 모양 |

앞 요청이 저장한 값(token, `assignmentId`, `teamId`, `inviteCode` 등)을 뒤 요청이 씁니다. 그래서 **폴더 0부터 차례로** 실행합니다.
실행할 때마다 새 과제를 만들므로 서버를 다시 켜지 않아도 몇 번이든 다시 실행할 수 있습니다.

## Postman에서 쓰기

1. Postman에서 Import → `footprint-demo.postman_collection.json`을 고릅니다.
2. 서버를 켭니다. 한 번에 실행(`docker compose up -d --build`)이든 개발용 `bootRun`이든 같습니다.
3. 모음 이름 옆 `...` → Run collection → Run을 누르면 처음부터 끝까지 실행합니다. 요청을 하나씩 보낼 때도 위에서부터 Send 합니다.

알아 둘 것:

- 변수는 모음에 저장됩니다(모음을 누르고 Variables 탭). 백엔드 주소 `baseUrl`의 기본값은 `http://127.0.0.1:8080`입니다. Windows에서 `localhost`가 IPv6로 연결되어 멈추는 일이 있어서 `127.0.0.1`을 씁니다(`docs/worklog.md`의 "한 번에 실행").
- token은 요청마다 Authorization 탭의 Bearer Token에 `{{professorToken}}`처럼 들어 있습니다. 로그인 요청의 Scripts(Post-response) 탭이 받은 token을 저장합니다.
- 산출물 업로드 요청은 Body 탭의 `file` 칸에 `sample-upload.txt`가 지정되어 있습니다. Postman이 파일을 찾지 못한다고 표시하면 그 칸에서 이 폴더의 `sample-upload.txt`(또는 아무 파일)를 다시 고릅니다.
- 다운로드 요청은 Send 옆 화살표의 "Send and Download"로 파일을 저장할 수 있습니다.

## 가짜 API에 실행하기

`frontend`에서 `npm run mock`을 켠 뒤, 모음의 Variables 탭에서 `baseUrl`을 `http://localhost:3001`로 바꿔 실행합니다. 같은 test가 모두 통과해야 합니다(가짜 API의 응답 모양이 실제와 같다는 확인입니다).
가짜 API는 `frontend/db.json`을 바꿉니다. 처음 상태로 되돌리려면 `git restore frontend/db.json`을 실행합니다.
업로드 요청이 올린 시험 파일은 `frontend/mock/files/`에 남습니다. git이 무시하는 파일이지만, 정리하려면 `seed-1.txt`, `seed-2.txt`를 뺀 파일을 지웁니다.

## 명령줄에서 실행하기 (선택)

Postman 없이 Newman(Postman 모음을 실행하는 명령줄 도구)으로 실행할 수 있습니다. 저장소 맨 위 폴더에서 실행합니다. 처음 한 번은 Newman을 내려받습니다.

```powershell
npx newman@6.2.1 run docs/postman/footprint-demo.postman_collection.json --working-dir docs/postman
```

가짜 API에 실행하려면 끝에 `--env-var baseUrl=http://localhost:3001`을 붙입니다.

## 모음을 고칠 때

API를 바꾸거나 새로 만들면 `docs/api-draft.md`와 함께 이 모음에도 요청을 추가합니다. Postman에서 고친 뒤 모음 `...` → Export(v2.1)로 이 파일을 덮어씁니다.
