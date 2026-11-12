# 발자국 실행 묶음을 켭니다. Windows PowerShell용입니다. macOS·Linux는 start.sh를 씁니다.
# 사용법: 이 폴더의 start.bat을 더블클릭하거나, PowerShell에서 .\start.ps1
# 하는 일: Docker 확인 → 이미지가 없으면 load → .env가 없으면 만듦 → 켜기 → 백엔드가 준비될 때까지 기다림 → 열 주소 안내

# 이 스크립트가 있는 폴더(묶음 폴더)를 기준으로 파일을 찾습니다. 어느 폴더에서 실행해도 같게 동작합니다.
$bundleDir = $PSScriptRoot
$composeFile = Join-Path $bundleDir "docker-compose.yml"
$envFile = Join-Path $bundleDir ".env"
$envTemplate = Join-Path $bundleDir "env.template"
$imageList = Join-Path $bundleDir "images\images.txt"

# 문장을 빨간 글자로 보여 주고 스크립트를 멈춥니다(종료 코드 1).
function Stop-WithMessage([string]$message) {
    Write-Host $message -ForegroundColor Red
    exit 1
}

# 무작위 값을 16진수 글자로 만듭니다. 암호용 난수(RandomNumberGenerator)를 씁니다. 바이트 하나가 글자 두 개가 됩니다.
function New-RandomHex([int]$byteCount) {
    $bytes = New-Object byte[] $byteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

# .env에서 "이름=값" 줄의 값을 읽습니다. 없으면 빈 글자입니다.
function Get-EnvValue([string]$name) {
    $match = Select-String -Path $envFile -Pattern "^$name=(.*)$" -Encoding UTF8 | Select-Object -Last 1
    if ($match) { return $match.Matches[0].Groups[1].Value.Trim() }
    return ""
}

# 1. Docker가 켜져 있는지 확인합니다. docker info는 Docker Desktop이 꺼져 있으면 실패합니다.
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Stop-WithMessage "Docker가 켜져 있지 않습니다. Docker Desktop을 켜고 왼쪽 아래가 'Engine running'이 된 뒤 다시 실행하세요."
}

# 2. 필요한 이미지가 이 컴퓨터에 없으면 images 폴더의 tar 파일에서 넣습니다(docker load). 인터넷을 쓰지 않습니다.
#    images.txt의 한 줄은 "tar 파일 이름|이미지 이름"입니다. 이미 있는 이미지는 건너뜁니다.
foreach ($line in Get-Content -Path $imageList -Encoding UTF8) {
    if ($line.Trim() -eq "" -or $line.StartsWith("#")) { continue }
    $parts = $line.Split("|")
    $tarFile = Join-Path $bundleDir ("images\" + $parts[0].Trim())
    $image = $parts[1].Trim()
    docker image inspect $image *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "이미지를 넣는 중: $image (처음 한 번만, 1~3분 걸립니다)"
        docker load -i $tarFile
        if ($LASTEXITCODE -ne 0) { Stop-WithMessage "이미지를 넣지 못했습니다: $tarFile" }
    }
}

# 3. 설정 파일 .env가 없으면 env.template을 복사해 만들고, 비어 있는 비밀값 세 줄을 무작위 값으로 채웁니다.
#    값은 화면에 찍지 않습니다. 데이터(volume)가 이미 있는데 .env만 없으면, 새 비밀번호로는 데이터베이스가 열리지 않으므로 멈춥니다.
if (-not (Test-Path $envFile)) {
    docker volume inspect footprint-bundle_postgres-data *> $null
    if ($LASTEXITCODE -eq 0) {
        Stop-WithMessage ("데이터는 남아 있는데 설정 파일 .env가 없습니다. 처음 켤 때 만든 .env를 이 폴더에 되살리세요.`n" +
            "데이터를 버리고 새로 시작하려면 reset 스크립트로 지운 뒤 다시 켜세요.")
    }
    $secrets = @{
        "JWT_SECRET"          = (New-RandomHex 32)
        "POSTGRES_PASSWORD"   = (New-RandomHex 24)
        "MINIO_ROOT_PASSWORD" = (New-RandomHex 24)
    }
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $lines = foreach ($line in [System.IO.File]::ReadAllLines($envTemplate, $utf8)) {
        $name = $line.Split("=")[0]
        if ($secrets.ContainsKey($name) -and $line -eq "$name=") { "$name=" + $secrets[$name] } else { $line }
    }
    # BOM 없는 UTF-8로 씁니다. Windows PowerShell의 Set-Content -Encoding UTF8은 파일 맨 앞에 BOM을 붙여서 docker compose가 첫 줄을 잘못 읽을 수 있습니다.
    [System.IO.File]::WriteAllLines($envFile, $lines, $utf8)
    Write-Host "설정 파일 .env를 만들었습니다. 비밀값은 이 파일에만 있습니다. 다른 사람에게 보내지 마세요."
}

# 4. 실행 방식을 확인합니다. prod는 교수 이메일이 없으면 백엔드가 켜지지 않으므로 미리 멈춥니다.
$mode = Get-EnvValue "FOOTPRINT_MODE"
if ($mode -eq "") { $mode = "demo" }
if ($mode -ne "demo" -and $mode -ne "prod") {
    Stop-WithMessage ".env의 FOOTPRINT_MODE는 demo 또는 prod여야 합니다. 지금 값: $mode"
}
if ($mode -eq "prod" -and (Get-EnvValue "PROFESSOR_EMAILS") -eq "") {
    Stop-WithMessage "FOOTPRINT_MODE=prod에서는 .env의 PROFESSOR_EMAILS에 교수 학교 이메일을 적어야 합니다."
}
Write-Host "실행 방식: $mode"

# 5. 켭니다. --wait: 모든 컨테이너가 켜지고 healthcheck(백엔드는 /actuator/health)가 통과할 때까지 기다립니다. 처음에는 1~2분 걸립니다.
Write-Host "켜는 중입니다. 백엔드가 준비될 때까지 기다립니다..."
docker compose -f $composeFile up -d --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) {
    Stop-WithMessage ("켜지 못했습니다. 자주 막히는 곳은 README.md의 '자주 막히는 곳'에 있습니다.`n" +
        "- 포트 5173이나 8080을 다른 프로그램이 쓰고 있지 않은지`n" +
        "- 백엔드 로그: docker logs footprint-bundle-backend --tail 100")
}

# 6. 열 주소를 알려 줍니다. 화면은 127.0.0.1이 아니라 localhost로 열어야 로그인이 됩니다(백엔드가 이 주소만 허용합니다).
Write-Host ""
Write-Host "켜졌습니다." -ForegroundColor Green
Write-Host "  화면: http://localhost:5173"
Write-Host "  상태 확인: http://127.0.0.1:8080/actuator/health"
if ($mode -eq "demo") { Write-Host "  시작 계정: professor@example.com / pass1234 (다른 계정은 README.md)" }
Write-Host "  끄기: stop.bat (데이터는 남습니다)"
