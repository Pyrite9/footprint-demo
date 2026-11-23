# main branch와 dev branch의 사이트를 함께 켭니다(배포-3). Windows PowerShell용입니다. macOS·Linux는 start.sh를 씁니다.
# 사용법: 저장소 어디서든 PowerShell에서 .\infra\sites\start.ps1 (저장소 맨 위에서 실행하는 경우)
# 하는 일: Docker·git 확인 → GitHub에서 main·dev 받기 → 두 branch의 코드 꺼내기 → 인증서가 없으면 만듦 → 두 사이트 켜기 → proxy 켜기 → 열 주소 안내

# 이 스크립트가 있는 폴더(infra\sites)를 기준으로 파일을 찾습니다. 어느 폴더에서 실행해도 같게 동작합니다.
$sitesDir = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $sitesDir "..\..")).Path
$siteFile = Join-Path $sitesDir "site.yml"
$proxyFile = Join-Path $sitesDir "proxy.yml"
$srcDir = Join-Path $sitesDir "src"
$certDir = Join-Path $sitesDir "certs"
$certFile = Join-Path $certDir "footprint.pem"
$keyFile = Join-Path $certDir "footprint-key.pem"
# Windows에 들어 있는 tar입니다. Git for Windows의 tar가 PATH에 먼저 있으면 E:\ 같은 경로를 다른 컴퓨터 이름으로 읽어서, 경로로 직접 부릅니다.
$tar = Join-Path $env:SystemRoot "System32\tar.exe"
# 켤 사이트입니다. 사이트 이름이 branch 이름이고, 주소(main.footprint.localhost)와 컨테이너 이름(footprint-main-*)에도 쓰입니다.
$sites = @("main", "dev")

# 문장을 빨간 글자로 보여 주고 스크립트를 멈춥니다(종료 코드 1).
function Stop-WithMessage([string]$message) {
    Write-Host $message -ForegroundColor Red
    exit 1
}

# 1. Docker가 켜져 있는지, git이 있는지 확인합니다. docker info는 Docker Desktop이 꺼져 있으면 실패합니다.
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Stop-WithMessage "Docker가 켜져 있지 않습니다. Docker Desktop을 켜고 왼쪽 아래가 'Engine running'이 된 뒤 다시 실행하세요."
}
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Stop-WithMessage "git이 없습니다. Git for Windows를 설치한 뒤 다시 실행하세요."
}

# 2. GitHub(origin)에서 main과 dev의 최신 commit을 받습니다. 받기만 하고, 지금 작업 중인 branch와 폴더는 바꾸지 않습니다.
#    git -C: 저장소 맨 위 폴더에서 실행한 것처럼 git을 부릅니다.
Write-Host "GitHub에서 main, dev를 받는 중입니다..."
git -C $repoRoot fetch origin main dev
if ($LASTEXITCODE -ne 0) {
    Write-Host "GitHub에서 받지 못했습니다(인터넷 확인). 이 PC에 이미 받아 둔 origin/main, origin/dev로 계속합니다." -ForegroundColor Yellow
}

# 3. 두 branch에 commit 된 코드(backend, frontend 폴더)를 src\<branch> 폴더에 꺼냅니다. site.yml이 이 폴더의 코드로 이미지를 만듭니다.
#    git archive는 그 branch에 commit 된 파일만 꺼냅니다. 지금 작업 중인 폴더(checkout)와 commit 하지 않은 변경은 쓰지 않고 바꾸지도 않습니다.
foreach ($site in $sites) {
    $ref = "origin/$site"
    git -C $repoRoot rev-parse --verify --quiet $ref *> $null
    if ($LASTEXITCODE -ne 0) {
        Stop-WithMessage "$ref 가 이 PC에 없습니다. 인터넷에 연결한 뒤 다시 실행하세요. GitHub에 $site branch가 있는지도 확인하세요."
    }

    # 이전에 꺼낸 코드를 지우고 새로 꺼냅니다. 지우지 않으면 branch에서 지운 파일이 남습니다.
    $siteSrc = Join-Path $srcDir $site
    $tarFile = Join-Path $srcDir "$site.tar"
    if (Test-Path $siteSrc) { Remove-Item -Recurse -Force $siteSrc }
    New-Item -ItemType Directory -Path $siteSrc -Force | Out-Null

    # tar 파일로 받은 뒤 풉니다. PowerShell의 |로 넘기면 글자로 바꿔 넘겨서 tar 내용이 깨지므로 파일로 받습니다.
    git -C $repoRoot archive --format=tar -o $tarFile $ref backend frontend
    if ($LASTEXITCODE -ne 0) { Stop-WithMessage "$ref 의 코드를 꺼내지 못했습니다." }
    & $tar -xf $tarFile -C $siteSrc
    if ($LASTEXITCODE -ne 0) { Stop-WithMessage "$ref 의 코드를 풀지 못했습니다: $tarFile" }
    Remove-Item -Force $tarFile

    $commit = git -C $repoRoot log -1 --format="%h %s" $ref
    Write-Host "  $site 사이트의 코드: $commit"
}

# 4. https 인증서가 없으면 이 PC에서 mkcert로 만듭니다. 이미 있으면 건너뜁니다.
#    인증서의 개인 key는 이 PC 밖으로 나가지 않게 저장소에 올리지 않습니다(.gitignore). 만든 인증서는 이 PC에서만 믿을 수 있습니다.
if (-not (Test-Path $certFile) -or -not (Test-Path $keyFile)) {
    # mkcert를 찾습니다. 실행 묶음을 만들 때 받아 둔 파일(make-bundle.ps1) → 이 PC에 설치한 mkcert 순서입니다.
    $mkcert = Join-Path $repoRoot "infra\bundle\cache\mkcert\mkcert-windows-amd64.exe"
    if (-not (Test-Path $mkcert)) {
        $mkcertCommand = Get-Command mkcert -ErrorAction SilentlyContinue
        if (-not $mkcertCommand) {
            Stop-WithMessage "인증서를 만드는 프로그램(mkcert)이 없습니다. infra\bundle\make-bundle.ps1을 한 번 실행하거나 mkcert를 설치하세요."
        }
        $mkcert = $mkcertCommand.Source
    }
    # (a) mkcert가 이 PC에 로컬 인증 기관(CA)을 만들고 Windows의 신뢰 목록에 넣습니다. 이미 넣었으면 그대로 둡니다.
    Write-Host "https 인증서를 처음 만듭니다. 이 PC에 로컬 인증 기관(mkcert)을 신뢰 목록에 넣습니다."
    Write-Host "  '보안 경고' 창이 뜨면 '예'를 누르세요. '아니요'를 눌러도 켜지지만, 브라우저에 경고 화면이 뜹니다."
    & $mkcert -install
    $caInstalled = ($LASTEXITCODE -eq 0)
    # (b) 두 사이트의 주소가 든 인증서 하나를 만듭니다. 앞 단계가 실패해도 만들 수 있습니다(CA는 신뢰 목록에 없는 채로 만들어져 있음).
    New-Item -ItemType Directory -Path $certDir -Force | Out-Null
    & $mkcert -cert-file $certFile -key-file $keyFile main.footprint.localhost dev.footprint.localhost
    if ($LASTEXITCODE -ne 0) { Stop-WithMessage "https 인증서를 만들지 못했습니다. 위의 mkcert 메시지를 확인하세요." }
    if (-not $caInstalled) {
        Write-Host ("로컬 인증 기관을 신뢰 목록에 넣지 못했습니다. 그대로 켭니다.`n" +
            "  브라우저에 '연결이 비공개로 설정되어 있지 않습니다' 경고가 뜨면 '고급' → '(안전하지 않음)으로 이동'을 누르세요.`n" +
            "  다시 넣으려면 infra\sites\certs 폴더를 지우고 start를 다시 실행하세요.") -ForegroundColor Yellow
    }
}

# 5. 두 사이트를 차례로 켭니다. 같은 site.yml을 project 이름(-p)만 바꿔 켭니다. SITE 환경 변수가 site.yml의 ${SITE}에 들어갑니다.
#    --build: 꺼낸 코드로 이미지를 새로 만듭니다. 처음에는 사이트마다 이미지 빌드에 몇 분 걸립니다.
#    --wait: 컨테이너가 켜지고 healthcheck(백엔드는 /actuator/health)가 통과할 때까지 기다립니다.
foreach ($site in $sites) {
    Write-Host "$site 사이트를 켜는 중입니다. 백엔드가 준비될 때까지 기다립니다..."
    $env:SITE = $site
    docker compose -p "footprint-$site" -f $siteFile up -d --build --wait --wait-timeout 600
    if ($LASTEXITCODE -ne 0) {
        Stop-WithMessage ("$site 사이트를 켜지 못했습니다.`n" +
            "- 백엔드 로그: docker logs footprint-$site-backend --tail 100")
    }
}

# 6. proxy를 켭니다. --force-recreate: 사이트 컨테이너가 다시 만들어지면 주소가 바뀌어서, proxy를 매번 새로 만들어 주소를 다시 찾게 합니다.
Write-Host "proxy를 켜는 중입니다..."
docker compose -f $proxyFile up -d --force-recreate
if ($LASTEXITCODE -ne 0) {
    Stop-WithMessage ("proxy를 켜지 못했습니다.`n" +
        "- 포트 443, 80을 다른 프로그램(실행 묶음 등)이 쓰고 있지 않은지`n" +
        "- proxy 로그: docker logs footprint-sites-proxy --tail 100")
}

# 7. 열 주소를 알려 줍니다.
Write-Host ""
Write-Host "켜졌습니다." -ForegroundColor Green
foreach ($site in $sites) {
    $commit = git -C $repoRoot log -1 --format="%h %s" "origin/$site"
    Write-Host "  https://$site.footprint.localhost  ($site 사이트, $commit)"
}
Write-Host "  시작 계정: professor@example.com / pass1234 (두 사이트 모두)"
Write-Host "  끄기: .\infra\sites\stop.ps1 (데이터는 남습니다)"
