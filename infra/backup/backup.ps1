# PostgreSQL과 MinIO를 한 묶음으로 백업합니다. Windows PowerShell용입니다. macOS·Linux는 backup.sh를 씁니다.
# 사용법(저장소 맨 위에서): .\infra\backup\backup.ps1
#   다른 컨테이너를 백업하려면: .\infra\backup\backup.ps1 -PostgresContainer 이름 -MinioContainer 이름
# 결과: infra\backup\bundles\<날짜-시각>\ 폴더에 db.dump, files\, manifest.txt
# 비밀번호는 이 스크립트가 다루지 않습니다. 명령을 컨테이너 안에서 실행해 컨테이너가 가진 접속값을 그대로 씁니다.
param(
    [string]$PostgresContainer = "footprint-demo-postgres",
    [string]$MinioContainer = "footprint-demo-minio"
)

# docker 명령을 실행하고, 실패하면(종료 코드가 0이 아니면) 스크립트를 멈춥니다. 결과 글자는 그대로 돌려줍니다.
function Invoke-Docker {
    docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker 명령이 실패했습니다: docker $args" }
}

# 버킷 이름은 infra\.env의 STORAGE_BUCKET, 없으면 백엔드의 기본값 footprint입니다.
$bucket = "footprint"
$envFile = Join-Path $PSScriptRoot "..\.env"
if (Test-Path $envFile) {
    $match = Select-String -Path $envFile -Pattern '^STORAGE_BUCKET=(.*)$' | Select-Object -Last 1
    if ($match) { $bucket = $match.Matches[0].Groups[1].Value.Trim() }
}

# 1. 두 컨테이너가 켜져 있는지 확인합니다.
foreach ($container in @($PostgresContainer, $MinioContainer)) {
    $running = docker inspect -f '{{.State.Running}}' $container 2>$null
    if ($running -ne "true") {
        throw "컨테이너 $container 가 켜져 있지 않습니다. infra 폴더에서 docker compose up -d 를 먼저 실행하세요."
    }
}

# 2. 데이터베이스 이름과 사용자 이름은 컨테이너의 환경 변수에서 읽습니다(비밀번호는 읽지 않습니다).
$dbUser = Invoke-Docker exec $PostgresContainer printenv POSTGRES_USER
$dbName = Invoke-Docker exec $PostgresContainer printenv POSTGRES_DB

$bundleDir = Join-Path $PSScriptRoot ("bundles\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Path $bundleDir -ErrorAction Stop | Out-Null
Write-Host "백업 폴더: $bundleDir"
Write-Host "서버(백엔드)를 멈춘 뒤에 백업하는 것을 권장합니다. 켜 둔 채면 백업 도중의 업로드가 묶음에 빠질 수 있습니다."

# 3. 데이터베이스를 먼저 백업합니다. -n public: 서비스 데이터가 있는 public 스키마만 담고, 빌드할 때 테스트가 쓰는 스키마(footprint_test 등)는 뺍니다.
#    -Fc: pg_restore로 되살리는 압축 형식입니다. 파일은 컨테이너 안에 만들고 docker cp로 꺼냅니다.
Invoke-Docker exec $PostgresContainer pg_dump -U $dbUser -d $dbName -n public -Fc -f /tmp/footprint-backup.dump
Invoke-Docker cp -q "${PostgresContainer}:/tmp/footprint-backup.dump" (Join-Path $bundleDir "db.dump")
Invoke-Docker exec $PostgresContainer rm -f /tmp/footprint-backup.dump

# 4. 확인용 사실을 데이터베이스에서 읽습니다. Flyway가 마지막으로 적용한 migration 번호와 테이블마다의 행 수입니다.
$flywayVersion = Invoke-Docker exec $PostgresContainer psql -U $dbUser -d $dbName -tA `
    -c "select version from flyway_schema_history where success order by installed_rank desc limit 1"
Invoke-Docker cp -q (Join-Path $PSScriptRoot "count-rows.sql") "${PostgresContainer}:/tmp/footprint-count-rows.sql"
$rowCounts = Invoke-Docker exec $PostgresContainer psql -U $dbUser -d $dbName -tA -f /tmp/footprint-count-rows.sql
Invoke-Docker exec $PostgresContainer rm -f /tmp/footprint-count-rows.sql
$pgDumpVersion = Invoke-Docker exec $PostgresContainer pg_dump --version

# 5. 그다음 버킷 파일을 백업합니다. mc(MinIO 클라이언트)는 MinIO 컨테이너 안에 들어 있습니다.
#    작은따옴표 안의 $MINIO_ROOT_USER 등은 PowerShell이 풀지 않고 컨테이너 안에서 풀립니다. 그래서 비밀번호가 이 컴퓨터의 화면이나 명령에 나오지 않습니다.
Invoke-Docker exec $MinioContainer sh -c 'mc alias set footprint-backup http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD > /dev/null'
Invoke-Docker exec $MinioContainer rm -rf /tmp/footprint-backup-files
Invoke-Docker exec $MinioContainer mkdir -p /tmp/footprint-backup-files
Invoke-Docker exec $MinioContainer sh -c "mc mirror --quiet footprint-backup/$bucket /tmp/footprint-backup-files > /dev/null"
# 대상 폴더(files)가 아직 없어서, docker cp가 files 폴더를 만들고 그 안에 파일을 넣습니다.
Invoke-Docker cp -q "${MinioContainer}:/tmp/footprint-backup-files" (Join-Path $bundleDir "files")
Invoke-Docker exec $MinioContainer rm -rf /tmp/footprint-backup-files
# 컨테이너 안에 남은 접속 이름(alias)을 지웁니다.
Invoke-Docker exec $MinioContainer sh -c "mc alias remove footprint-backup > /dev/null"
# @( ): 파일이 0개여도 Count가 0으로 나오게 배열로 감쌉니다.
$fileCount = @(Get-ChildItem -Path (Join-Path $bundleDir "files") -Recurse -File).Count

# 6. 묶음 설명 파일을 씁니다. 비밀값은 넣지 않습니다. 복구 스크립트가 이 파일의 rows., file_count 줄로 결과를 비교합니다.
$manifest = @(
    "# 발자국 백업 묶음. 개인정보(이름, 이메일, 평가 점수·의견, 올린 파일)가 들어 있음. 저장소·공유 폴더에 올리지 않음"
    "created_at=" + (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    "database=$dbName"
    "bucket=$bucket"
    "flyway_version=$flywayVersion"
    "pg_dump_version=$pgDumpVersion"
    "file_count=$fileCount"
) + $rowCounts
Set-Content -Path (Join-Path $bundleDir "manifest.txt") -Value $manifest -Encoding UTF8

Write-Host "백업을 마쳤습니다. 파일 $fileCount 개, Flyway 버전 $flywayVersion"
Write-Host "manifest: $(Join-Path $bundleDir 'manifest.txt')"
