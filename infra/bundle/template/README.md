# 발자국 실행 묶음

- 조별과제 과정기록 & 평가지원 서비스 "발자국"을 이 폴더 하나로 켜는 묶음임
- 화면, 서버, 데이터베이스(PostgreSQL), 파일 저장소(MinIO)가 Docker 컨테이너 4개로 켜짐
- 인터넷 없이 켜짐. 소스 코드, Java, Node.js는 필요 없음. 구글 로그인만 인터넷이 필요함
- 묶음 버전과 만든 commit은 `VERSION.txt`

## 1. 필요한 것

- **Docker Desktop**(Windows 10·11 또는 macOS). Linux는 Docker Engine과 compose plugin
  - `docker compose version`이 v2.17 이상
  - Windows는 설치 기본값(WSL 2, Linux 컨테이너) 그대로 씀
- **메모리**: Docker Desktop에 4GB 이상(설치 기본값이면 대부분 충분함)
- **디스크**: 이 폴더 약 500MB, 이미지를 넣은 뒤 Docker 안에 약 2GB 더
- **포트 5173, 8080**이 비어 있어야 함(아래 "자주 막히는 곳")
- 웹 브라우저(Chrome, Edge 등)

## 2. USB에서 PC로 복사한 뒤 실행 (추천)

- USB에서 바로 실행해도 켜지지만, 이 폴더를 통째로 PC에 복사한 뒤 실행하는 것을 추천함
  - 켜면 이 폴더에 `.env`(비밀값)가 생기고, 백업하면 `backup/bundles/`(개인정보)가 생김. USB에서 실행하면 USB를 다른 사람에게 넘길 때 함께 넘어감
  - 처음 켤 때 이미지 파일(약 450MB)을 읽음. USB가 느리면 오래 걸림
  - macOS에서 exFAT USB의 파일은 실행 권한이 없고 `._`로 시작하는 파일이 생김
- 복사할 곳 예: Windows `C:\footprint-demo`, macOS `~/footprint-demo`. 공백과 한글이 없는 경로를 추천함
- 복사가 제대로 됐는지 보려면(선택) 이 폴더에서
  - Windows PowerShell: `Get-Content SHA256SUMS.txt | ForEach-Object { $h, $p = $_ -split '  ', 2; if ((Get-FileHash $p -Algorithm SHA256).Hash.ToLower() -ne $h) { "다름: $p" } }` — 아무것도 나오지 않으면 같음
  - macOS: `shasum -a 256 -c SHA256SUMS.txt`, Linux: `sha256sum -c SHA256SUMS.txt` — 모두 `OK`면 같음

## 3. 켜기

- Windows: `start.bat`을 더블클릭함. 창에 "켜졌습니다"가 나오면 아무 키나 눌러 창을 닫음
- macOS·Linux: 터미널에서 이 폴더로 가서 `bash start.sh`
- start가 하는 일: Docker가 켜져 있는지 확인 → 이미지가 없으면 `images/`에서 넣음(처음 한 번, 1~3분) → `.env`가 없으면 만들고 비밀값을 무작위로 채움 → 켬 → 서버가 준비될 때까지 기다림(처음 1~2분) → 열 주소를 알려 줌
- 화면: **http://localhost:5173** — `127.0.0.1:5173`으로 열면 로그인이 되지 않음
- 상태 확인: http://127.0.0.1:8080/actuator/health 에서 `{"status":"UP"}`이면 서버와 데이터베이스가 정상임
- 이미 켜져 있을 때 다시 실행해도 됨. 바뀐 것이 없으면 그대로 둠
- **처음 켠 직후 몇 초 동안은 시작 계정 로그인이 실패할 수 있음.** "켜졌습니다"가 나온 뒤에도 서버가 시작 데이터를 넣는 중일 수 있음. 몇 초 뒤 다시 로그인하면 됨(두 번째부터는 없음)

## 4. 시작 계정과 처음 볼 화면 (시연용 demo)

- 로그인 화면 아래 "시작 계정으로 로그인 (개발·백업용)"을 펼쳐 이메일과 비밀번호를 넣음. **비밀번호는 모두 `pass1234`**
- 시작 계정은 저장소에 공개된 시연용 가짜 계정임. 실제 사람이 아님

| 이메일 | 이름 | 유형 | 시작 과제에서 |
|---|---|---|---|
| professor@example.com | 이교수 | 교수 | 시작 과제 2개를 만든 교수 |
| student01@example.com | 김민준 | 학생 | 두 과제 모두 1조 조장 |
| student02@example.com | 이서연 | 학생 | 두 과제 모두 1조 |
| student03@example.com | 박도윤 | 학생 | 두 과제 모두 1조 |
| student04@example.com | 최서윤 | 학생 | 데모 과제 1조 |
| student05@example.com | 정하준 | 학생 | 데모 과제 1조 |
| student06@example.com | 강지우 | 학생 | 데모 과제 2조 조장 |
| student07@example.com ~ student09@example.com | 조은우, 윤하은, 장시우 | 학생 | 데모 과제 2조 |
| student10@example.com | 임지아 | 학생 | 데모 과제 2조. 개인정보 고지에 아직 동의하지 않은 상태 |

- 시작 과제
  - **발자국 데모 과제**(기록 단계): 조 2개. 산출물 업로드, 기여 항목, 완료확인, 진척 화면을 봄
  - **발자국 평가 단계 과제**(평가 단계): 1조 3명. 동료평가 입력, 근거 패널을 봄. 교수는 평가 결과와 CSV를 봄
- 처음 볼 순서 예
  1. `professor@example.com`으로 로그인 → 과제 목록 → 두 과제의 조와 기록
  2. 로그아웃 → `student01@example.com` → 데모 과제 1조에서 산출물 올리기, 진척 화면
  3. `student02@example.com` → 평가 단계 과제에서 동료평가 입력과 근거 패널
  4. `student10@example.com` → 개인정보 고지 화면이 먼저 나옴
- 서비스 원칙은 "판단은 사람, 시스템은 근거만"임. 점수 합계, 평균, 순위, 진척률은 일부러 없음

## 5. 끄기

- Windows: `stop.bat` 더블클릭. macOS·Linux: `bash stop.sh`
- **데이터는 지워지지 않음.** 다시 켜면 올린 파일, 기록, 평가가 그대로 있음
- PC를 끄거나 Docker Desktop을 끄기 전에 끄는 것을 추천함. 끄지 않고 PC를 꺼도 데이터는 남고, 다음에 start로 다시 켬

## 6. 실행 방식 — 시연용(demo)과 운영(prod)

- 실행 방식은 `.env`의 `FOOTPRINT_MODE` 한 줄로 정함. 처음 켤 때 만들어지는 값은 `demo`임

| | demo (기본) | prod |
|---|---|---|
| 시작 계정, 시작 과제 | 있음 | 없음 |
| 이메일·비밀번호 로그인 | 됨 | 없음(화면에 폼이 없고 서버도 받지 않음) |
| 구글 로그인 | 됨(인터넷 필요) | 됨(인터넷 필요). 이것만 됨 |
| 인터넷 없이 시연 | 됨 | 안 됨 |
| 필요한 값 | 없음 | `PROFESSOR_EMAILS`(교수 학교 이메일) |

- prod로 바꾸는 순서
  1. 데이터를 지움: Windows `reset.bat`, macOS·Linux `bash reset.sh`(demo의 시작 계정이 남지 않게)
  2. `.env`를 메모장 등으로 열어 `FOOTPRINT_MODE=prod`, `PROFESSOR_EMAILS=교수이메일@g.yju.ac.kr`로 고침(여러 명이면 쉼표)
  3. start로 켬
- demo로 돌아갈 때도 같은 순서(reset → `FOOTPRINT_MODE=demo` → start)
- **구글 로그인의 조건**
  - 인터넷이 연결되어 있어야 함
  - 학교 구글 계정(`g.yju.ac.kr`)만 받음
  - 구글 로그인 설정(Google Cloud의 OAuth 동의 화면)이 "테스트 중"이면, 만든 사람이 테스트 사용자로 등록한 계정만 로그인됨. 로그인이 막히면 묶음을 준 사람에게 계정 등록을 요청함
  - 화면을 `http://localhost:5173`으로 열어야 함

## 7. 데이터가 남는 곳과 지우는 법

- 데이터베이스와 올린 파일은 **이 폴더가 아니라 Docker 안**(volume)에 남음
  - `footprint-bundle_postgres-data`: 회원(이름, 이메일), 과제, 조, 기록, 동료평가 점수·의견
  - `footprint-bundle_minio-data`: 올린 산출물 파일
- **개인정보가 들어 있음.** 실제 학생이 쓰면 이름, 학교 이메일, 평가 점수·의견, 올린 파일이 남음
- 이 폴더에 남는 것: `.env`(비밀값), `backup/bundles/`(백업한 경우)
- 모두 지우는 순서(다 쓴 뒤)
  1. reset으로 컨테이너와 데이터를 지움(`delete`를 입력해야 지워짐)
  2. 이미지를 지움: `docker rmi footprint-backend:<버전> footprint-frontend:<버전>-demo footprint-frontend:<버전>-prod` — 버전은 `VERSION.txt`. `postgres:17`, `pgsty/minio:...`는 다른 곳에서 쓰지 않으면 같이 지움
  3. 이 폴더를 지움(`.env`, 백업 묶음 포함)
- 폴더만 지우면 Docker 안의 데이터는 남음. 반대로 reset만 하면 `.env`와 백업 묶음은 남음

## 8. 백업과 복구

- 켜 둔 상태에서 이 폴더에서 실행함. 결과는 `backup/bundles/<날짜-시각>/`(데이터베이스, 올린 파일, 설명 파일)
  - Windows PowerShell: `.\backup\backup.ps1 -PostgresContainer footprint-bundle-postgres -MinioContainer footprint-bundle-minio`
  - macOS·Linux: `bash backup/backup.sh --postgres-container footprint-bundle-postgres --minio-container footprint-bundle-minio`
- 화면에서 기록이 바뀌지 않는 때(아무도 쓰지 않을 때) 하는 것을 추천함
- 백업 묶음에도 개인정보가 들어 있음. 다른 곳에 보관하려면 개인 저장 장치에 옮기고, 공유 폴더나 메일로 보내지 않음. 보관 기간이 지나면 폴더째 지움
- 복구는 **빈 묶음에만** 됨(데이터가 있으면 아무것도 바꾸지 않고 멈춤)
  1. reset으로 데이터를 지움
  2. 데이터베이스와 파일 저장소만 켬: `docker compose up -d --wait postgres minio`(이 폴더에서)
  3. Windows: `.\backup\restore.ps1 backup\bundles\<날짜-시각> -PostgresContainer footprint-bundle-postgres -MinioContainer footprint-bundle-minio`
     macOS·Linux: `bash backup/restore.sh backup/bundles/<날짜-시각> --postgres-container footprint-bundle-postgres --minio-container footprint-bundle-minio`
  4. "행 수와 파일 개수가 manifest.txt와 같습니다"가 나오면 start로 켬
- 같은 `.env`가 있어야 복구한 뒤에도 켜짐. 다른 PC로 옮기면 그 PC의 `.env`로 새로 만든 데이터베이스에 되살리므로 문제없음

## 9. 자주 막히는 곳

- **"Docker가 켜져 있지 않습니다"** — Docker Desktop을 켜고 "Engine running"이 될 때까지 기다린 뒤 다시 start
- **포트가 쓰이는 중**(`port is already allocated`, `bind: ... address already in use`) — 5173이나 8080을 다른 프로그램이 씀. 개발 중인 서버, 다른 Docker 컨테이너가 흔함
  - 무엇이 쓰는지: Windows `netstat -ano | findstr ":5173 :8080"`(마지막 숫자가 프로그램 번호, 작업 관리자의 PID), macOS `lsof -i :5173 -i :8080`
  - Docker 컨테이너면 `docker ps`에서 찾아 끔
  - 포트는 바꿀 수 없음. 화면이 부르는 주소(127.0.0.1:8080)와 로그인 허용 주소(localhost:5173)가 이미지 안에 들어 있음
- **로그인 화면에서 "Failed to fetch" 또는 아무 반응 없음** — 주소를 `http://localhost:5173`으로 열었는지 확인. 서버가 켜진 직후면 몇 초 뒤 다시 시도
- **"데이터는 남아 있는데 설정 파일 .env가 없습니다"** — `.env`를 지웠거나 다른 폴더에서 실행한 경우임. 데이터베이스의 비밀번호는 처음 켤 때 만든 `.env`의 값으로 고정되어 새 `.env`로는 열리지 않음
  - 처음 쓰던 폴더의 `.env`를 이 폴더로 복사하고 다시 start
  - 데이터를 버려도 되면 reset 뒤 start(새 `.env`가 만들어짐)
- **Windows에서 `.ps1`을 더블클릭하면 메모장이 열리거나 실행이 막힘** — `.bat` 파일을 더블클릭함. `.bat`이 이번 실행에만 PowerShell 실행 정책을 풀어 줌
- **macOS에서 `permission denied`** — `./start.sh` 대신 `bash start.sh`로 부름
- **Apple Silicon Mac(M1 이후)에서 느림** — 이미지가 Intel·AMD(amd64)용이라 Docker Desktop이 변환해서 실행함. 처음 켜기가 몇 분 더 걸릴 수 있음. Docker Desktop 설정 General의 "Use Rosetta for x86_64/amd64 emulation on Apple Silicon"을 켜면 빨라짐
- **켜지지 않을 때 원인 보기** — `docker logs footprint-bundle-backend --tail 100`. `docker compose ps`(이 폴더에서)로 네 컨테이너의 상태를 봄. 백엔드가 `healthy`면 정상

## 10. 이 폴더에 든 것

| 파일 | 무엇 |
|---|---|
| `README.md` | 이 안내 |
| `start.bat`, `stop.bat`, `reset.bat` | Windows에서 더블클릭하는 파일. 같은 이름의 `.ps1`을 실행함 |
| `start.ps1`, `stop.ps1`, `reset.ps1` | Windows PowerShell 스크립트 |
| `start.sh`, `stop.sh`, `reset.sh` | macOS·Linux 스크립트(`bash start.sh`) |
| `docker-compose.yml` | 컨테이너 4개의 설정 |
| `env.template` | `.env`의 원본. start가 처음 켤 때 복사해서 비밀값을 채움 |
| `images/` | Docker 이미지 tar 5개와 목록(`images.txt`) |
| `backup/` | 백업·복구 스크립트 |
| `VERSION.txt` | 묶음 버전, 만든 날짜, commit, 이미지 이름 |
| `SHA256SUMS.txt` | 파일마다의 해시. 복사가 제대로 됐는지 볼 때 씀 |
| `.env` | 처음 켠 뒤 생김. 실행 방식과 비밀값. 다른 사람에게 보내지 않음 |
