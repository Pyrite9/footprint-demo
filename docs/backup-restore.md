# 백업과 복구 (O-4)

PostgreSQL(기록)과 MinIO(올린 파일)를 한 묶음으로 백업하고, 빈 환경에 되살리는 방법

- 기준은 `infra/docker-compose.yml`로 켠 컨테이너 2개임: `footprint-demo-postgres`, `footprint-demo-minio`. 맨 위 `docker-compose.yml`로 켜도 같은 컨테이너임
- 서버에 올린 뒤의 자동 예약 실행, 다른 곳에 보관, 암호화는 아직 없음. 서버가 정해지는 O-3의 일임(맨 아래 "남은 것")
- 스크립트는 `infra/backup/`에 있음. Windows는 `.ps1`, macOS·Linux는 `.sh`. 하는 일은 같음
- 컴퓨터에 `pg_dump`나 MinIO 클라이언트를 설치하지 않아도 됨. 이미 켜져 있는 두 컨테이너 안의 도구(`pg_dump`, `pg_restore`, `mc`)를 씀
- 비밀번호는 스크립트가 읽지 않음. 명령을 컨테이너 안에서 실행해 컨테이너가 가진 접속값을 그대로 씀. 그래서 화면 출력과 묶음에도 비밀번호가 남지 않음

## 백업 묶음 하나에 든 것

`infra/backup/bundles/<날짜-시각>/` 폴더 하나가 같은 시점의 백업 하나임

| 파일 | 내용 |
|---|---|
| `db.dump` | 데이터베이스의 `public` 스키마 전체(`pg_dump -Fc`). 테이블, 데이터, 인덱스, Flyway 이력 테이블(`flyway_schema_history`)까지. 빌드할 때 테스트가 쓰는 스키마(`footprint_test`, `footprint_prod_test`)는 뺌 |
| `files/` | MinIO 버킷(기본 `footprint`)의 파일 전부. 파일 이름이 곧 저장 위치(key)라서 `deliverable.storage_key`와 짝이 맞음 |
| `manifest.txt` | 확인용 사실: 만든 시각, 데이터베이스 이름, 버킷 이름, Flyway 마지막 migration 번호, `pg_dump` 버전, 파일 개수, 테이블마다의 행 수. 비밀값은 없음 |

- `files/`에는 데이터베이스가 가리키지 않는 주인 없는 파일도 들어감(`TODO.md` 5절 "개발 데이터베이스를 다시 만들면 시작 파일이 주인 없이 남음"). 버킷을 통째로 담기 때문임. 화면에는 보이지 않음
- 삭제된 산출물은 행만 있고 파일은 없음(R-1-3). 되살린 뒤에도 "삭제됨"으로 보이고 다운로드는 404임

## 개인정보 — 먼저 읽을 것

- **백업 묶음은 개인정보가 든 파일임**: 회원 이름과 이메일, 평가 점수와 의견, 보고 메모, 올린 파일 본문
- 저장소에 올리지 않음. `infra/backup/bundles/`는 `.gitignore`에 있음. 다른 폴더로 옮겼다면 그 폴더가 저장소 안이 아닌지 봄
- 공유 폴더, 메신저, 개인 클라우드에 올리지 않음. 저장소 폴더를 통째로 복사해 넘길 때도 `infra/backup/bundles/`를 빼고 넘김
- 보관 기간은 개인정보 고지(X-2)의 보관 기간과 같음. 지금 초안은 "과제가 속한 학기의 성적 처리와 이의 신청 기간이 끝날 때까지"임(`frontend/src/constants/privacyNotice.js`, 학교·담당 교수 확인 전)
  - 서비스에서 기록을 지워도 그 전에 만든 백업에는 남아 있음. 기간이 지나면 백업 묶음도 함께 지워야 함
  - 자동으로 지우는 기능은 없음. 폴더 이름이 만든 날짜이므로 기간이 지난 폴더를 직접 지움
  - 고지 문구와 보관 기간이 확정되면 이 절도 같이 고침

## 백업하기

1. 서버(백엔드)를 멈춤. 켜 둔 채로도 되지만 아래 "켜 둔 채로 백업하면"의 한계가 있음
   - `bootRun`이나 IDE로 켰으면 그 창에서 멈춤
   - 맨 위 `docker-compose.yml`로 켰으면 `docker stop footprint-demo-backend`
2. DB와 MinIO 컨테이너는 켜 둠. 꺼져 있으면 `infra` 폴더에서 `docker compose up -d`
3. 저장소 맨 위 폴더에서 실행

   ```powershell
   .\infra\backup\backup.ps1
   ```

   macOS·Linux(Windows의 Git Bash도 됨)

   ```bash
   ./infra/backup/backup.sh
   ```

4. 끝에 `백업을 마쳤습니다. 파일 N 개, Flyway 버전 N`이 나오면 됨. 묶음 위치도 함께 나옴
5. 서버를 다시 켬(`docker start footprint-demo-backend` 등)

- PowerShell이 "스크립트를 실행할 수 없습니다"로 막으면 이번 한 번만 허용해서 실행함: `powershell -ExecutionPolicy Bypass -File .\infra\backup\backup.ps1`. 컴퓨터의 설정은 바뀌지 않음
- 다른 이름의 컨테이너를 백업하려면 `-PostgresContainer 이름 -MinioContainer 이름`(bash는 `--postgres-container 이름 --minio-container 이름`)
- 버킷 이름은 `infra/.env`의 `STORAGE_BUCKET`, 없으면 백엔드의 기본값 `footprint`

### 켜 둔 채로 백업하면

- 데이터베이스를 먼저, 파일을 나중에 담음. 두 저장소를 같은 순간에 찍을 방법이 없어서 그 사이에 일어난 일이 어긋날 수 있음
  - 그 사이에 올린 파일: 파일만 묶음에 들어가고 행은 없음. 주인 없는 파일이라 화면에 보이지 않음. 해는 없음
  - 그 사이에 삭제한 산출물: 묶음의 행은 삭제 전인데 파일이 없음. 되살리면 목록에는 보이지만 다운로드가 404임
  - 순서를 거꾸로(파일 먼저) 하면 그 사이에 올린 산출물이 "행은 있는데 파일이 없는" 상태가 되므로, 더 흔한 업로드 쪽이 안전한 지금 순서를 씀
- 그래서 **서버를 멈추고 백업하는 것을 권장함.** 백업은 몇 초면 끝남(2026.11.12 개발 데이터: 파일 257개 약 18MB, dump 약 40KB)

## 복구하기

복구는 **빈 환경**에만 함. 이미 데이터가 있는 곳에 덮어쓰지 않음

- 스크립트가 쓰기 전에 두 곳을 모두 확인함: 데이터베이스 `public` 스키마에 테이블이 하나라도 있거나, MinIO에 같은 이름의 버킷이 있으면 아무것도 바꾸지 않고 멈춤
- 덮어쓰는 옵션은 두지 않았음. 지금 데이터를 버리고 되돌리는 일은 사람이 volume을 직접 지워야만 할 수 있음

### 순서

1. 서버(백엔드)를 끔. **복구가 끝날 때까지 켜지 않음.** 빈 데이터베이스에서 서버가 켜지면 Flyway가 테이블을 만들고 시작 데이터와 버킷이 들어가서 대상이 더는 비어 있지 않음
2. 빈 DB·MinIO를 준비함
   - 새 컴퓨터, 또는 volume이 없어진 경우: `infra` 폴더에서 `docker compose up -d`. 처음 켜면 빈 volume이 생김
   - 지금 데이터를 버리고 묶음 시점으로 되돌리는 경우: **먼저 지금 데이터도 백업함**(위 "백업하기"). 그다음 `infra` 폴더에서 `docker compose down -v`로 volume을 지우고 `docker compose up -d`로 다시 켬. `down -v`는 되돌릴 수 없고 DB와 MinIO를 둘 다 지움
   - 복구한 환경의 DB·MinIO 비밀번호는 묶음이 아니라 지금 `infra/.env`(없으면 기본값)로 정해짐. 묶음에는 비밀번호가 없음
   - 버킷은 묶음의 `manifest.txt`에 적힌 이름으로 만듦. 서버의 `STORAGE_BUCKET`도 같은 이름이어야 다운로드가 됨(바꾸지 않았다면 둘 다 `footprint`)
3. 저장소 맨 위 폴더에서 실행. 묶음 폴더를 지정함

   ```powershell
   .\infra\backup\restore.ps1 infra\backup\bundles\20261112-093740
   ```

   ```bash
   ./infra/backup/restore.sh infra/backup/bundles/20261112-093740
   ```

4. 끝에 `복구를 마쳤습니다. 테이블별 행 수와 파일 개수(N 개)가 manifest.txt와 같습니다.`가 나와야 함. 다르면 두 목록을 나란히 보여 주고 실패로 끝남
5. 서버를 켜고 아래 "복구한 뒤 확인할 것"을 봄

### 복구한 뒤 확인할 것

- 서버 로그에 `Successfully validated 2 migrations`와 `Schema "public" is up to date. No migration necessary.`가 나옴. Flyway 이력이 함께 돌아와서 migration을 다시 돌리지 않고, `ddl-auto: validate`도 통과함(번호는 그때의 migration 개수)
- `GET /actuator/health`가 200 `{"status":"UP"}`
- 시작 데이터가 다시 들어가지 않음. 회원이 이미 있으면 `DataInitializer`가 넣지 않음(manifest의 `rows.member`와 같은지 봄)
- 로그인해서 산출물 목록이 보이고, 산출물을 내려받으면 원래 파일과 같음
- 삭제된 산출물은 여전히 "삭제됨"이고 다운로드는 404 "삭제된 산출물입니다"

### 멈추는 경우와 할 일

| 출력 | 뜻 | 할 일 |
|---|---|---|
| `멈춤: 대상 데이터베이스(...)의 public 스키마에 테이블이 N 개 있습니다` | 대상이 비어 있지 않음. 서버를 먼저 켰거나 다른 곳을 가리킴 | 아무것도 바뀌지 않았음. 위 순서 2부터 다시 |
| `멈춤: 대상 MinIO(...)에 버킷 ... 이 이미 있습니다` | 버킷이 이미 있음 | 아무것도 바뀌지 않았음. 위 순서 2부터 다시 |
| `컨테이너 ... 가 켜져 있지 않습니다` | DB나 MinIO가 꺼져 있음 | `infra`에서 `docker compose up -d` |
| `pg_restore: error: ...` 또는 `docker 명령이 실패했습니다` | 되살리는 도중 실패 | 대상이 반쯤 차 있을 수 있음. volume을 지우고 순서 2부터 다시 |
| `복구는 끝났지만 manifest.txt와 다릅니다` | 행 수나 파일 개수가 다름 | 다른 묶음을 섞었거나 묶음 폴더가 손상됨. 그 환경을 쓰지 말고 다른 묶음으로 다시 |

## 복구 연습 (개발 데이터를 건드리지 않고)

백업은 되살려 보기 전에는 믿을 수 없음. 개발 환경과 이름·포트가 다른 빈 환경을 하나 더 띄워 거기에 되살려 봄

- `infra/backup/docker-compose.restore-check.yml`이 `infra/docker-compose.yml` 위에 겹쳐서 이름(`footprint-restore-check`), 컨테이너 이름, 포트(5433, 9100, 9101)만 바꿈. volume도 `footprint-restore-check_*`로 따로 생김

1. 빈 연습 환경을 켬(`infra` 폴더에서)

   ```powershell
   docker compose -f docker-compose.yml -f backup/docker-compose.restore-check.yml up -d --wait
   ```

2. 저장소 맨 위 폴더에서 연습 환경에 복구함

   ```powershell
   .\infra\backup\restore.ps1 infra\backup\bundles\<날짜-시각> -PostgresContainer footprint-restore-check-postgres -MinioContainer footprint-restore-check-minio
   ```

   ```bash
   ./infra/backup/restore.sh infra/backup/bundles/<날짜-시각> --postgres-container footprint-restore-check-postgres --minio-container footprint-restore-check-minio
   ```

3. 백엔드를 연습 환경에 붙여 8081로 켬(`backend` 폴더에서). 환경 변수는 이 창에서만 쓰이므로 확인이 끝나면 창을 닫음

   ```powershell
   $env:POSTGRES_PORT="5433"; $env:STORAGE_ENDPOINT="http://localhost:9100"; $env:SERVER_PORT="8081"
   .\gradlew.bat bootRun
   ```

   - macOS·Linux: `POSTGRES_PORT=5433 STORAGE_ENDPOINT=http://localhost:9100 SERVER_PORT=8081 ./gradlew bootRun`
4. `http://127.0.0.1:8081/actuator/health`가 `UP`인지, 위 "복구한 뒤 확인할 것"을 봄. 화면으로 보려면 화면의 API 주소를 8081로 바꿔야 해서 Postman이나 curl이 간단함
5. 백엔드를 끄고 연습 환경을 치움(`infra` 폴더에서). **`-f` 두 개를 꼭 함께 씀.** 빠뜨리면 개발 환경을 가리킴

   ```powershell
   docker compose -f docker-compose.yml -f backup/docker-compose.restore-check.yml down -v
   ```

## 남은 것 (O-3에서 함)

서버와 제출 형태가 정해져야 정할 수 있어서 이번에 만들지 않음

- 자동 예약 실행(cron, Windows 작업 스케줄러)과 백업 주기
- 다른 컴퓨터·저장소에 보관(지금은 같은 컴퓨터의 폴더라 디스크가 고장 나면 함께 잃음)
- 묶음 암호화(서버에 두면 필요함)
- 보관 기간이 지난 묶음을 지우는 일(위 "개인정보")
- 서버를 멈추지 않고 백업하는 방법(필요해지면 MinIO 버전 관리나 DB 기준 파일 목록으로 맞추는 방식을 검토)
