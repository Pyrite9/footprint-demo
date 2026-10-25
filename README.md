# 발자국 시연본

조별과제 과정기록 & 평가지원 서비스 "발자국"의 12.11 시연본입니다.
흐름: 과제 생성 → 초대 링크로 합류 → 역할 배정 → 산출물 업로드·다운로드 → 동료평가 제출.

## 한 번에 실행 (Docker만 있으면 됩니다)

Java, Node.js 없이 Docker Desktop만으로 백엔드, 화면, PostgreSQL, MinIO, 시작 데이터를 한 번에 켭니다.

```powershell
docker compose up -d --build
```

- 처음에는 이미지를 만드느라 몇 분 걸립니다. 다음부터는 `docker compose up -d`만 쓰면 됩니다(코드를 바꿨으면 `--build`를 붙입니다).
- 화면: http://localhost:5173 (주소는 `127.0.0.1`이 아니라 `localhost`로 엽니다. 백엔드가 이 주소만 허용합니다.)
- 백엔드: http://127.0.0.1:8080
- MinIO 콘솔: http://localhost:9001 (계정은 `infra/.env.example`)
- 시작 계정과 시작 데이터: `backend/README.md`의 "시작 계정"부터. 비밀번호는 모두 `pass1234`입니다.
- 서버가 켜질 때마다 데이터베이스를 새로 만들고 시작 데이터를 넣습니다. 올린 산출물과 제출한 평가는 `docker compose restart backend`로 지워집니다.

끄기:

```powershell
docker compose down
```

### 켜기 전에 확인할 것

- 학습 저장소(`footprint-study`)의 infra가 켜져 있으면 먼저 끕니다. 포트(5432, 9000, 9001)가 같습니다.
- 개발 중에 켠 백엔드(8080)와 화면 개발 서버(5173)도 끕니다. 같은 포트를 씁니다.
- `infra/.env`가 없어도 됩니다. 없으면 `infra/.env.example`과 같은 기본값을 씁니다.

## 개발할 때

DB와 MinIO만 Docker로 켜고, 백엔드와 화면은 내 컴퓨터에서 실행합니다.

- DB·MinIO: `infra` 폴더에서 `docker compose up -d`
- 백엔드: `backend/README.md`
- 화면: `frontend/README.md`

두 방법은 같은 이름(`footprint-demo`)이라 같은 DB·MinIO 컨테이너를 씁니다. 한 번에 실행한 뒤 개발로 돌아갈 때는 맨 위 폴더에서 `docker compose down`을 하고 `infra`에서 다시 켭니다.

## 문서

- `docs/demo-scope.md`: 시연 범위와 업무 규칙
- `docs/api-draft.md`: API 명세
- `docs/postman/`: API를 시연 흐름 순서로 모은 Postman 모음과 쓰는 방법
- `docs/demo-script.md`: 12.11 시연 대본(준비, 계정, 장면별로 누를 것과 말할 것)
- `docs/worklog.md`: 작업 기록과 결정
