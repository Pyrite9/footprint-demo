# 노트북 이식 가이드 (footprint-demo → Arch Linux)

이 문서는 **노트북(Arch Linux)의 Claude Code에게 그대로 넘기는 작업 지시서**다.
작성: 2026-10-28, 데스크톱(Windows 11, `E:\YJU\footprint-demo`) 기준.

---

## 0. 관리자가 데스크톱에서 먼저 할 것 (Claude가 못 하는 부분)

1. 이 파일은 저장소의 `docs/laptop-setup.md`다. 노트북 Claude에게는 `https://github.com/Pyrite9/footprint-demo/blob/main/docs/laptop-setup.md`를 주거나, clone 한 뒤 이 파일을 읽으라고 한다.
2. `E:\YJU\footprint-demo\infra\.env`를 노트북으로 옮긴다(USB, 본인에게 보내기 등).
   - gitignore된 파일이고 `PROFESSOR_EMAILS`(실제 이메일)와 비밀번호가 들어 있다. 저장소·채팅에 붙여 넣지 않는다.
   - 없어도 실행은 된다. 다만 구글 로그인 시 교수로 판별되는 계정이 없어서 전원 학생이 된다.
   - 데스크톱 파일은 줄바꿈이 LF라 그대로 써도 된다.
3. 데스크톱의 `footprint-demo`는 `origin/main`과 같다(2026-10-28 확인, 안 올린 commit 없음). 노트북에서 작업하기 전에 데스크톱에서 새로 commit 한 게 있으면 push 해 둔다.

---

## 1. 노트북 Claude에게: 지켜야 할 것

- 사용자 호칭은 "관리자", 한국어 반말(~해 체), 이모티콘 금지.
- `git push`, 원격 설정, 패키지 설치(`pacman`, `yay` 등), **`sudo`가 필요한 모든 명령**, 외부 플랫폼 작업은 하기 전에 관리자에게 말하고 동의를 받는다. `sudo` 비밀번호는 관리자가 직접 친다.
- `footprint-study`는 읽기만 한다. 고치거나 commit 하지 않는다.
- `infra/.env`의 값은 출력하지 않는다. 키 이름 확인만 한다.
- 단계마다 결과를 짧게 보고하고, 실패하면 거기서 멈추고 오류 출력과 함께 알린다.
- **이 노트북에서는 bash 기준으로 명령을 쓴다.** `gradlew.bat` 대신 `./gradlew`(4절 참고). 프로젝트 `CLAUDE.md`의 "실행 환경" 절에도 2026.10.28에 이 내용을 넣었다.

---

## 2. 필요한 프로그램 (pacman)

먼저 확인한다. 데스크톱 기준 버전을 옆에 적었다.

| 프로그램 | 확인 명령 | 필요 조건 (데스크톱) | Arch 패키지 |
|---|---|---|---|
| Git | `git --version` | 아무 최신 | `git` |
| JDK 21 | `java -version` | **21** (21.0.12) | `jdk21-openjdk` |
| Node.js 24 | `node -v` | **24.x** (`frontend/.nvmrc`, v24.18.0) | 아래 참고 |
| Docker Engine | `docker version` | 서비스가 켜져 있어야 함 | `docker` |
| Compose | `docker compose version` | **v2.24 이상** (`include`, `env_file.required`) | `docker-compose` |
| Buildx | `docker buildx version` | `up --build`에 필요 | `docker-buildx` |
| GitHub CLI (선택) | `gh --version` | PR 볼 때만 | `github-cli` |

주의할 점:

- **Node.js**: Arch의 `nodejs` 패키지는 최신(현재 24가 아닐 가능성이 높음)이다. 24.x를 맞추는 방법 둘 중 하나를 관리자에게 고르게 한다.
  - `pacman -Ss nodejs-lts`로 24.x LTS 패키지(이름 예: `nodejs-lts-krypton`)를 찾아 `npm`과 함께 설치.
  - 또는 `fnm`/`nvm`을 깔고 `frontend`에서 `fnm use`(`.nvmrc`를 읽음).
- **JDK**: 다른 버전 JDK가 이미 있으면 `archlinux-java status`로 확인하고 `sudo archlinux-java set java-21-openjdk`로 21을 기본으로 한다.
- **Docker**: Docker Desktop은 필요 없다. 엔진을 켠다.
  ```bash
  sudo systemctl enable --now docker.service
  ```
  `sudo` 없이 `docker`를 쓰려면 `sudo usermod -aG docker $USER` 후 다시 로그인한다. docker 그룹은 사실상 root 권한이라는 점을 관리자에게 말하고 고르게 한다(싫으면 매번 `sudo docker compose ...`).

설치 명령 예(동의 받고 실행):

```bash
sudo pacman -S --needed git jdk21-openjdk docker docker-compose docker-buildx
```

### 시스템 설정 확인 두 가지

- **localhost 이름 풀이**: 화면 주소가 `http://localhost:5173`이고 백엔드 CORS도 `localhost`만 허용한다. Arch는 `/etc/hosts`가 비어 있을 수 있다.
  ```bash
  getent hosts localhost
  ```
  아무것도 안 나오면 관리자 동의를 받고 `/etc/hosts`에 `127.0.0.1 localhost`와 `::1 localhost`를 추가한다.
- **시간대**: 개발 중 `bootRun`으로 켠 백엔드는 시스템 시간대로 기록 시각(업로드, 평가 제출)을 저장한다. `timedatectl`로 `Asia/Seoul`인지 본다. 아니면 동의 받고 `sudo timedatectl set-timezone Asia/Seoul`. (`docker compose`로 켠 백엔드는 `TZ: Asia/Seoul`이 들어 있어 상관없다.)

---

## 3. 폴더 배치 — 두 저장소는 반드시 형제 폴더

`CLAUDE.md`가 `@../footprint-study/docs/decisions.md`를 불러오고, 기준 문서로 `../footprint-study`를 참조한다. 그래서 두 저장소가 **같은 부모 폴더 아래** 있어야 한다.

```
<부모 폴더>/            예: ~/yju  (관리자에게 어디로 할지 묻는다. 한글·공백 없는 경로 추천)
  footprint-demo/
  footprint-study/
```

```bash
cd <부모 폴더>
git clone https://github.com/Pyrite9/footprint-demo.git
git clone https://github.com/Pyrite9/footprint-study.git
```

- `footprint-study`는 `main` 그대로 둔다. `docs/decisions.md`가 데스크톱과 `origin/main`이 같다(2026-10-28 확인).
- git 사용자 설정을 데스크톱과 맞춘다. 이메일은 public 저장소라 여기 적지 않았다. 관리자에게 데스크톱의 `git config user.email` 값을 묻는다.
  ```bash
  git config --global user.name "Pyrite9"
  git config --global user.email "<관리자에게 묻는다>"
  ```
- 줄바꿈: 데스크톱은 `core.autocrlf=true`(Windows)다. 노트북은 `git config --global core.autocrlf input`으로 둔다. 그래야 노트북에서 commit 할 때 CRLF가 섞여 들어가지 않는다.
- push 인증: HTTPS로 clone 했으니 push 할 때 인증이 필요하다. 관리자가 직접 `gh auth login` 후 `gh auth setup-git`을 하거나, SSH 키를 등록하고 원격 주소를 SSH로 바꾼다. 원격 주소 변경은 관리자 동의를 받고 한다.

---

## 4. gradlew 실행 권한

원래 `backend/gradlew`의 파일 모드가 `100644`(실행 권한 없음)였다. Windows에서 commit 해서 생긴 문제다. 2026.10.28 데스크톱에서 `100755`로 고쳐 commit 했다(push는 관리자가 따로 함).

- 먼저 `git ls-files -s backend/gradlew`를 본다. 앞자리가 `100755`면 고친 commit이 들어온 것이니 그냥 `./gradlew`를 쓴다.
- `100644`면 **`chmod +x` 하지 않는다.** 하면 git이 mode 변경을 diff로 잡아서 commit에 섞인다. 대신 `sh ./gradlew build`처럼 `sh`로 실행하고, 관리자에게 "저장소의 gradlew 실행 권한을 고쳐야 한다"고 알린다.

`backend/.gitattributes`가 `gradlew`를 LF로 고정해 두어서 줄바꿈 문제는 없다.

---

## 5. Claude Code 설정 옮기기

### 5-1. 저장소에 이미 들어 있는 것 (clone만 하면 따라옴)

- `CLAUDE.md` — 프로젝트 지침
- `.claude/settings.json` — 허용·확인·금지 명령 목록(`./gradlew build *` 등 Linux용 항목도 들어 있다)
- `.claude/launch.json` — 화면 개발 서버 미리보기 설정(`frontend`, 5173)
- `.claude/skills/rule-check`, `read-check`, `next-step` — 프로젝트 skill

### 5-2. 전역 지침 `~/.claude/CLAUDE.md` (저장소에 없음, 새로 만든다)

경로: `~/.claude/CLAUDE.md`. 이미 파일이 있으면 덮어쓰기 전에 내용을 관리자에게 보여 주고 합칠지 묻는다. 아래 내용을 **그대로** 넣는다. 맨 끝 "이 컴퓨터" 절은 노트북에만 넣는다.

```markdown
# 사용자 전역 지침

## 호칭·말투

- 나를 **"관리자"**라고 부른다
- **반말체(~해 체) 고정** — 짧은 대답도 예외 없이 (O: 맞아 / 알겠어, X: 맞다 / 알겠다)
- 한국어로 답한다. 영어 기술 용어는 번역하지 말고 그대로 섞어 쓴다
- 이모티콘 금지

## 응답 스타일

- 간결하게. 격식 없이, 그렇다고 가볍지도 않게
- 냉철하게 — 칭찬도 비판도 담백하게, 공격은 하지 않는다
- 추상론 말고 **구체적인 방법부터** 제시한다
- 복잡한 건 단순화하지 말고 복잡한 채로 다룬다

## 가르치는 방식

- 공부·코딩은 답을 바로 던지지 말고 **단계별로 이끈다**
- 비유와 예시를 적극적으로 쓴다

## 외부 작업

- 외부 플랫폼 작업(메일 발송 등)은 **먼저 동의를 구한다**

## 참고

- GitHub 계정: `Pyrite9`

## 이 컴퓨터

- Arch Linux 노트북이다. 명령은 bash 기준으로 쓴다. 프로젝트 문서의 PowerShell·`gradlew.bat` 안내는 데스크톱(Windows)용이다.
- `sudo`가 필요한 명령과 패키지 설치는 먼저 동의를 구한다.
```

### 5-3. 프로젝트 메모리 (저장소에 없음, 새로 만든다)

메모리 폴더 이름은 **프로젝트 경로로 정해진다.** 데스크톱은 `E:\YJU\footprint-demo` → `E--YJU-footprint-demo`였다. 노트북은 `~/yju/footprint-demo`라면 `-home-<사용자>-yju-footprint-demo` 같은 이름이 된다.

- 노트북에서 `footprint-demo` 폴더로 Claude Code 세션을 한 번 연 뒤, 시스템이 알려 주는 메모리 경로(`~/.claude/projects/<경로 이름>/memory/`)를 쓴다. 추측해서 만들지 않는다.
- 내용은 `CLAUDE.md`의 "묻지 않고 추천안으로 진행하고 기록할 것" 절과 겹친다. 그래도 같게 맞추려면 아래 두 파일을 만든다.

`memory/MEMORY.md`:

```markdown
- [자율 진행 방식](autonomous-workflow.md) — 묻지 말고 추천안으로 진행, 단위마다 commit, push 전 반드시 알림
```

`memory/autonomous-workflow.md`:

```markdown
---
name: autonomous-workflow
description: "footprint-demo에서는 중간 질문 없이 추천안으로 단위 업무를 연속 진행하고, 단위마다 commit, push 전에는 반드시 알린다"
metadata:
  type: feedback
---

2026-10-25 관리자 지시: 단위 업무마다 계획 확인·선택지 질문을 하지 말고 추천안으로 쭉 진행한다. 결정은 docs/worklog.md에 선택지·이유와 함께 기록한다. 단위 하나가 빌드·확인까지 끝나면 commit 한다. push(원격 설정 포함)는 하기 전에 반드시 관리자에게 말한다. PR은 자동으로 만들지 않는다.

**Why:** 단위마다 문답하는 과정이 번거롭다고 했다. 대신 기록과 commit으로 추적하길 원한다.

**How to apply:** next-step 형식의 "진행 대기", 미정 항목 멈춤, rule-check 후 "고쳐" 대기를 생략한다. CLAUDE.md "묻지 않고 추천안으로 진행하고 기록할 것" 절이 같은 내용이다. ../footprint-study 읽기 전용, push 사전 고지는 그대로 지킨다.
```

### 5-4. 옮기지 않는 것

- `.claude/settings.local.json` — 데스크톱에서 한 번씩 허용한 명령 기록일 뿐이다.
- 전역 `~/.claude/settings.json` — 알림, auto-compact 설정 정도라 필수 아님.
- `unity-mcp-skill` 등 다른 과목용 skill·플러그인 — 이 프로젝트와 무관하다.

---

## 6. 비밀값 파일 넣기

관리자가 옮겨 온 `.env`를 `footprint-demo/infra/.env`에 둔다.

- 키 이름만 확인한다(값 출력 금지): `sed 's/=.*/=<hidden>/' infra/.env`. 있어야 할 키: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_API_PORT`, `MINIO_CONSOLE_PORT`, `PROFESSOR_EMAILS`.
- `file infra/.env`가 `CRLF`를 보고하면 `sed -i 's/\r$//' infra/.env`로 LF로 바꾼다.
- `chmod 600 infra/.env`로 본인만 읽게 한다.
- `git status`에 `infra/.env`가 **나오지 않는지** 확인한다(gitignore 대상). 나오면 멈추고 알린다. public 저장소다.
- 파일이 없으면 `cp infra/.env.example infra/.env`로 쓰고, `PROFESSOR_EMAILS`가 비어 있다는 것을 관리자에게 알린다.

`frontend/.env`(API 주소, 구글 Client ID)는 저장소에 들어 있어서 따로 할 일이 없다.

---

## 7. 동작 확인

순서대로 하고, 하나라도 실패하면 멈추고 보고한다.

### 7-1. 빌드

백엔드 테스트(`@SpringBootTest`, `StorageServiceTests`)는 PostgreSQL과 MinIO가 켜져 있어야 통과한다. 그래서 `infra`부터 켠다.

```bash
cd footprint-demo/infra
docker compose up -d
docker compose ps
```

```bash
cd footprint-demo/backend
./gradlew build        # 실행 권한이 없으면 sh ./gradlew build (4절)
```

```bash
cd footprint-demo/frontend
npm ci
npm run build
npm run lint
```

- `package-lock.json`에 Linux용 네이티브 모듈(`@rolldown/binding-linux-x64-gnu`)이 들어 있어서 `npm ci`가 그대로 된다. 만약 `Cannot find module @rolldown/binding-...` 오류가 나면 멈추고 보고한다(`package-lock.json`을 지우고 다시 만드는 식으로 고치지 않는다. 데스크톱과 lockfile이 갈라진다).
- 빌드가 실패하면 오류를 그대로 보고한다.

### 7-2. 한 번에 실행

`infra`로 켠 컨테이너와 이름이 같아서, 먼저 `infra`를 끄고 맨 위 폴더에서 켠다(`README.md` "개발할 때").

```bash
cd footprint-demo/infra
docker compose down
cd ..
docker compose up -d --build
docker compose ps
```

- 화면 http://localhost:5173 (반드시 `localhost`. `127.0.0.1`은 CORS와 구글 로그인 허용 출처에 없다.)
- 백엔드 http://127.0.0.1:8080
- 시작 계정과 비밀번호는 `backend/README.md`의 "시작 계정". 이메일·비밀번호 로그인이 되면 성공이다.
- 구글 로그인: Client ID의 허용 출처가 `http://localhost:5173`이라 노트북에서도 설정 변경 없이 된다. 학교 계정(`g.yju.ac.kr`)으로 관리자가 직접 눌러 확인한다.
- 포트 5432, 9000, 9001, 8080, 5173이 이미 쓰이고 있으면 `ss -ltnp | grep -E ':(5432|9000|9001|8080|5173)\b'`로 무엇이 쓰는지 보고, 학습 저장소 infra나 로컬 PostgreSQL 서비스(`postgresql.service`) 등이면 끄라고 안내한다.
- `docker-compose.yml` 주석의 WSL·IPv6 이야기는 Windows 사정이다. Linux에서는 신경 쓰지 않아도 된다.

확인 후 끄기:

```bash
docker compose down
```

### 7-3. 결과 보고

관리자에게 아래를 표로 보고한다: 프로그램 버전, clone 경로, localhost·시간대 확인 결과, gradlew 권한 상태, 전역 CLAUDE.md·메모리 생성 여부, `infra/.env` 유무(키 이름만), 빌드·lint 결과, 로그인 확인 결과, 남은 문제.

---

## 8. 두 컴퓨터를 오가며 작업할 때

- 작업 시작 전: `git pull` (두 저장소 모두).
- 작업 끝: commit 후 push. push는 관리자에게 말하고 한다.
- 컴퓨터를 바꾸기 전에 `git status -sb`로 `ahead`가 없는지 본다. `docs/worklog.md`는 양쪽에서 자주 고치는 파일이라 특히 충돌이 잘 난다.
- Linux는 파일 이름 대소문자를 구분한다. Windows에서 `import './Header'`인데 파일이 `header.jsx`면 데스크톱에서는 되고 노트북에서는 깨진다. 노트북에서 import 오류가 나면 대소문자부터 본다(2026-10-28 기준 `frontend/src`에는 이런 곳이 없다).
- 메모리와 전역 CLAUDE.md는 git으로 동기화되지 않는다. 한쪽에서 바꾸면 다른 쪽도 손으로 맞춘다.
