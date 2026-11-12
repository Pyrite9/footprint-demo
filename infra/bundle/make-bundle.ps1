# 제출용 실행 묶음(폴더 하나)을 만듭니다. Windows PowerShell용입니다(O-3).
# 사용법(저장소 맨 위에서): .\infra\bundle\make-bundle.ps1
#   버전을 직접 정하려면: .\infra\bundle\make-bundle.ps1 -Version 2026.12.11
# 결과: infra\bundle\out\footprint-demo\ 폴더. 이 폴더를 통째로 USB에 복사합니다. out 폴더는 저장소에 올라가지 않습니다(.gitignore).
# 하는 일: 이미지 빌드(백엔드 1개, 화면 2개) → 묶음 폴더 만들기 → 이미지를 tar로 저장 → 버전·해시 파일 쓰기
# 인터넷이 필요합니다(빌드할 때 Gradle·npm 의존성과 기반 이미지를 받음). 받는 컴퓨터는 인터넷 없이 켭니다.
param(
    # 이미지 tag에 붙는 묶음 버전입니다. 기본은 오늘 날짜(예: 2026.11.12)입니다.
    [string]$Version = (Get-Date -Format "yyyy.MM.dd"),
    # 붙이면 이전 빌드 결과(cache)를 쓰지 않고 의존성 설치부터 다시 합니다. 오래 걸리지만 저장소만으로 빌드되는지 확인됩니다.
    [switch]$NoCache
)

# docker 명령을 실행하고, 실패하면(종료 코드가 0이 아니면) 스크립트를 멈춥니다. 결과 글자는 그대로 돌려줍니다.
function Invoke-Docker {
    docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker 명령이 실패했습니다: docker $args" }
}

$repoDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$templateDir = Join-Path $PSScriptRoot "template"
$outDir = Join-Path $PSScriptRoot "out\footprint-demo"
$utf8 = New-Object System.Text.UTF8Encoding $false

# 줄 목록을 BOM 없는 UTF-8, 줄 끝 LF로 씁니다. WriteAllLines는 Windows에서 CRLF로 써서, sha256sum -c가 파일 이름 끝의 \r까지 읽어 실패합니다.
function Write-LfFile([string]$path, [string[]]$lines) {
    [System.IO.File]::WriteAllText($path, (($lines -join "`n") + "`n"), $utf8)
}

# 묶음에 넣을 이미지입니다. 받는 컴퓨터가 Intel·AMD Windows PC라서 amd64로 만들고 저장합니다.
# 화면은 두 가지입니다. demo는 이메일·비밀번호 로그인 폼이 있고, prod는 없습니다(VITE_PASSWORD_LOGIN은 빌드할 때 화면 파일에 들어감).
$platform = "linux/amd64"
$backendImage = "footprint-backend:$Version"
$frontendDemoImage = "footprint-frontend:$Version-demo"
$frontendProdImage = "footprint-frontend:$Version-prod"
# PostgreSQL과 MinIO는 저장소의 infra/docker-compose.yml과 같은 이미지입니다. 그쪽을 바꾸면 여기와 template/docker-compose.yml도 바꿉니다.
$postgresImage = "postgres:17"
$minioImage = "pgsty/minio:RELEASE.2026-08-04T00-00-00Z"

# 1. Docker가 켜져 있는지 확인합니다.
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker가 켜져 있지 않습니다. Docker Desktop을 켠 뒤 다시 실행하세요." }

# 2. 백엔드와 화면 이미지를 새로 만듭니다. 화면은 백엔드를 화면과 같은 주소의 /api로 부르고, 화면 이미지의 nginx가 backend 컨테이너로 넘깁니다.
#    @cacheArgs: 배열을 명령의 인자 여러 개로 펼칩니다. -NoCache가 없으면 빈 배열이라 아무것도 붙지 않습니다.
$cacheArgs = @()
if ($NoCache) { $cacheArgs = @("--no-cache") }
Write-Host "1/5 백엔드 이미지를 만듭니다: $backendImage"
Invoke-Docker build @cacheArgs --platform $platform -t $backendImage (Join-Path $repoDir "backend")
Write-Host "2/5 화면 이미지를 만듭니다: $frontendDemoImage, $frontendProdImage"
$frontendDir = Join-Path $repoDir "frontend"
Invoke-Docker build @cacheArgs --platform $platform -t $frontendDemoImage `
    --build-arg VITE_API_URL=/api --build-arg VITE_PASSWORD_LOGIN=true $frontendDir
Invoke-Docker build @cacheArgs --platform $platform -t $frontendProdImage `
    --build-arg VITE_API_URL=/api --build-arg VITE_PASSWORD_LOGIN=false $frontendDir

# 3. PostgreSQL·MinIO 이미지가 이 컴퓨터에 없으면 받습니다. 있으면 그대로 씁니다(개발 환경이 쓰는 것과 같은 이미지).
foreach ($image in @($postgresImage, $minioImage)) {
    docker image inspect $image *> $null
    if ($LASTEXITCODE -ne 0) { Invoke-Docker pull --platform $platform $image }
}

# 4. 묶음 폴더를 새로 만듭니다. 이전 결과가 있으면 지웁니다(out 폴더는 이 스크립트가 만든 것만 들어 있음).
Write-Host "3/5 묶음 폴더를 만듭니다: $outDir"
if (Test-Path $outDir) { Remove-Item -Path $outDir -Recurse -Force }
New-Item -ItemType Directory -Path (Join-Path $outDir "images") -ErrorAction Stop | Out-Null
New-Item -ItemType Directory -Path (Join-Path $outDir "backup") -ErrorAction Stop | Out-Null

# 템플릿의 파일을 그대로 복사합니다. docker-compose.yml만 __VERSION__ 자리에 묶음 버전을 넣습니다.
Get-ChildItem -Path $templateDir -File | Where-Object { $_.Name -ne "docker-compose.yml" } |
    Copy-Item -Destination $outDir
$compose = [System.IO.File]::ReadAllText((Join-Path $templateDir "docker-compose.yml"), $utf8)
[System.IO.File]::WriteAllText((Join-Path $outDir "docker-compose.yml"), $compose.Replace("__VERSION__", $Version), $utf8)

# 백업·복구 스크립트(O-4)를 넣습니다. 묶음 안에서는 backup 폴더에 있어서, 스크립트가 읽는 ..\.env가 묶음의 .env가 됩니다.
# 복구 연습용 compose 파일(docker-compose.restore-check.yml)은 저장소의 infra 폴더가 있어야 쓰여서 넣지 않습니다.
foreach ($name in @("backup.ps1", "backup.sh", "restore.ps1", "restore.sh", "count-rows.sql")) {
    Copy-Item -Path (Join-Path $repoDir "infra\backup\$name") -Destination (Join-Path $outDir "backup")
}

# 5. 이미지를 tar 파일로 저장합니다. 받는 컴퓨터의 start 스크립트가 images.txt를 보고 없는 이미지만 docker load 합니다.
Write-Host "4/5 이미지를 저장합니다(몇 분 걸립니다)"
$images = [ordered]@{
    "footprint-backend.tar"       = $backendImage
    "footprint-frontend-demo.tar" = $frontendDemoImage
    "footprint-frontend-prod.tar" = $frontendProdImage
    "postgres.tar"                = $postgresImage
    "minio.tar"                   = $minioImage
}
$imageLines = @("# tar 파일 이름|이미지 이름. start 스크립트가 이 컴퓨터에 없는 이미지만 docker load 합니다")
foreach ($tarName in $images.Keys) {
    Invoke-Docker save --platform $platform -o (Join-Path $outDir "images\$tarName") $images[$tarName]
    $imageLines += "$tarName|$($images[$tarName])"
}
Write-LfFile (Join-Path $outDir "images\images.txt") $imageLines

# 6. 버전 파일과 해시 파일을 씁니다. VERSION.txt는 어느 commit으로 만들었는지, SHA256SUMS.txt는 복사한 뒤 파일이 같은지 보는 데 씁니다.
Write-Host "5/5 버전·해시 파일을 씁니다"
$commit = git -C $repoDir rev-parse --short HEAD
# 아직 commit 하지 않은 변경이 있으면 표시합니다. 그 묶음은 commit만으로 똑같이 다시 만들 수 없습니다.
if (git -C $repoDir status --porcelain) { $commit = "$commit (commit 하지 않은 변경 포함)" }
$versionLines = @(
    "발자국 실행 묶음",
    "version=$Version",
    "built_at=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "commit=$commit",
    "platform=$platform"
) + ($images.Values | ForEach-Object { "image=$_" })
Write-LfFile (Join-Path $outDir "VERSION.txt") $versionLines

# sha256sum -c 와 같은 모양(해시, 공백 두 칸, 묶음 폴더 기준 경로)으로 씁니다. macOS는 shasum -a 256 -c 로 확인합니다.
$sumLines = Get-ChildItem -Path $outDir -Recurse -File | Sort-Object FullName | ForEach-Object {
    $relative = $_.FullName.Substring($outDir.Length + 1).Replace("\", "/")
    (Get-FileHash -Path $_.FullName -Algorithm SHA256).Hash.ToLower() + "  " + $relative
}
Write-LfFile (Join-Path $outDir "SHA256SUMS.txt") $sumLines

$totalBytes = (Get-ChildItem -Path $outDir -Recurse -File | Measure-Object -Property Length -Sum).Sum
Write-Host ("묶음을 만들었습니다: {0} ({1:N0} MB)" -f $outDir, ($totalBytes / 1MB)) -ForegroundColor Green
Write-Host "USB에 복사하는 법과 확인하는 법은 저장소의 docs/bundle.md"
