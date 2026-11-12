# backup.ps1(또는 backup.sh)로 만든 묶음 하나를 빈 PostgreSQL·MinIO에 되살립니다. Windows PowerShell용입니다. macOS·Linux는 restore.sh를 씁니다.
# 사용법(저장소 맨 위에서): .\infra\backup\restore.ps1 infra\backup\bundles\<날짜-시각>
#   다른 컨테이너에 되살리려면: ... -PostgresContainer 이름 -MinioContainer 이름
# 대상이 비어 있지 않으면(테이블이나 버킷이 이미 있으면) 아무것도 바꾸지 않고 멈춥니다. 덮어쓰는 옵션은 없습니다.
# 백엔드는 복구가 끝난 뒤에 켭니다. 먼저 켜면 빈 데이터베이스에 시작 데이터가 들어가서 대상이 비어 있지 않게 됩니다.
param(
    [Parameter(Mandatory = $true)][string]$BundleDir,
    [string]$PostgresContainer = "footprint-demo-postgres",
    [string]$MinioContainer = "footprint-demo-minio"
)

# docker 명령을 실행하고, 실패하면(종료 코드가 0이 아니면) 스크립트를 멈춥니다. 결과 글자는 그대로 돌려줍니다.
function Invoke-Docker {
    docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker 명령이 실패했습니다: docker $args" }
}

# 1. 묶음 폴더에 필요한 것이 다 있는지 확인합니다.
$manifestFile = Join-Path $BundleDir "manifest.txt"
$dumpFile = Join-Path $BundleDir "db.dump"
$filesDir = Join-Path $BundleDir "files"
if (-not ((Test-Path $manifestFile) -and (Test-Path $dumpFile) -and (Test-Path $filesDir))) {
    throw "묶음 폴더를 지정하세요. 그 안에 db.dump, files\, manifest.txt가 있어야 합니다."
}
$manifest = Get-Content -Path $manifestFile -Encoding UTF8
$bucket = (($manifest | Where-Object { $_ -like "bucket=*" }) -replace '^bucket=', '').Trim()

# 2. 두 컨테이너가 켜져 있는지 확인합니다.
foreach ($container in @($PostgresContainer, $MinioContainer)) {
    $running = docker inspect -f '{{.State.Running}}' $container 2>$null
    if ($running -ne "true") { throw "컨테이너 $container 가 켜져 있지 않습니다." }
}
$dbUser = Invoke-Docker exec $PostgresContainer printenv POSTGRES_USER
$dbName = Invoke-Docker exec $PostgresContainer printenv POSTGRES_DB

# 3. 대상이 비어 있는지 확인합니다. 둘 다 확인한 뒤에야 쓰기 시작해서, 한쪽만 되살아나는 일을 막습니다.
$tableCount = Invoke-Docker exec $PostgresContainer psql -U $dbUser -d $dbName -tA `
    -c "select count(*) from information_schema.tables where table_schema = 'public'"
if ($tableCount -ne "0") {
    throw "멈춤: 대상 데이터베이스($PostgresContainer)의 public 스키마에 테이블이 $tableCount 개 있습니다. 빈 환경(새 volume)에만 복구합니다."
}
Invoke-Docker exec $MinioContainer sh -c 'mc alias set footprint-restore http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD > /dev/null'
# 버킷이 없으면 mc stat이 실패합니다. 오류 글자는 컨테이너 안에서 버려서(> /dev/null 2>&1) 종료 코드만 봅니다.
docker exec $MinioContainer sh -c "mc stat footprint-restore/$bucket > /dev/null 2>&1"
if ($LASTEXITCODE -eq 0) {
    throw "멈춤: 대상 MinIO($MinioContainer)에 버킷 $bucket 이 이미 있습니다. 빈 환경(새 volume)에만 복구합니다."
}

# 4. 데이터베이스를 되살립니다. Flyway 이력 테이블(flyway_schema_history)도 함께 돌아와서 서버가 migration을 다시 돌리지 않습니다.
#    -n public: public 스키마 안의 테이블·데이터·인덱스만 되살리고 "CREATE SCHEMA public"은 건너뜁니다. 새 데이터베이스에는 public이 이미 있어서 그 줄이 실패하기 때문입니다.
#    --no-owner: 대상의 사용자 이름이 원본과 달라도 지금 사용자가 주인이 됩니다. --exit-on-error: 오류가 나면 바로 멈춥니다.
Invoke-Docker cp -q $dumpFile "${PostgresContainer}:/tmp/footprint-restore.dump"
Invoke-Docker exec $PostgresContainer pg_restore -U $dbUser -d $dbName -n public --no-owner --no-privileges --exit-on-error /tmp/footprint-restore.dump
Invoke-Docker exec $PostgresContainer rm -f /tmp/footprint-restore.dump

# 5. 버킷을 만들고 파일을 되살립니다. 파일 이름이 곧 산출물의 저장 위치(key)라서 데이터베이스 행과 다시 짝이 맞습니다.
Invoke-Docker exec $MinioContainer rm -rf /tmp/footprint-restore-files
# 컨테이너 안에 footprint-restore-files가 없어서, docker cp가 그 폴더를 만들고 files 안의 파일을 넣습니다.
Invoke-Docker cp -q $filesDir "${MinioContainer}:/tmp/footprint-restore-files"
Invoke-Docker exec $MinioContainer sh -c "mc mb --quiet footprint-restore/$bucket > /dev/null"
Invoke-Docker exec $MinioContainer sh -c "mc mirror --quiet /tmp/footprint-restore-files footprint-restore/$bucket > /dev/null"
Invoke-Docker exec $MinioContainer rm -rf /tmp/footprint-restore-files

# 6. 되살린 결과를 묶음의 manifest.txt와 비교합니다. 테이블마다의 행 수와 파일 개수입니다.
Invoke-Docker cp -q (Join-Path $PSScriptRoot "count-rows.sql") "${PostgresContainer}:/tmp/footprint-count-rows.sql"
$restoredRows = Invoke-Docker exec $PostgresContainer psql -U $dbUser -d $dbName -tA -f /tmp/footprint-count-rows.sql
Invoke-Docker exec $PostgresContainer rm -f /tmp/footprint-count-rows.sql
$expectedRows = $manifest | Where-Object { $_ -like "rows.*" }
$restoredFiles = @(Invoke-Docker exec $MinioContainer mc ls --recursive "footprint-restore/$bucket").Count
# 컨테이너 안에 남은 접속 이름(alias)을 지웁니다.
Invoke-Docker exec $MinioContainer sh -c "mc alias remove footprint-restore > /dev/null"
$expectedFiles = (($manifest | Where-Object { $_ -like "file_count=*" }) -replace '^file_count=', '').Trim()

# -join: 여러 줄을 한 글자열로 이어 붙여 한 번에 비교합니다.
if ((($restoredRows -join "`n") -eq ($expectedRows -join "`n")) -and ("$restoredFiles" -eq $expectedFiles)) {
    Write-Host "복구를 마쳤습니다. 테이블별 행 수와 파일 개수($restoredFiles 개)가 manifest.txt와 같습니다."
    Write-Host "이제 백엔드를 켜고 GET /actuator/health 가 UP인지, 산출물 다운로드가 되는지 확인하세요."
} else {
    Write-Host "--- manifest.txt (파일 $expectedFiles 개)"
    $expectedRows | ForEach-Object { Write-Host $_ }
    Write-Host "--- 되살린 결과 (파일 $restoredFiles 개)"
    $restoredRows | ForEach-Object { Write-Host $_ }
    throw "복구는 끝났지만 manifest.txt와 다릅니다. 위를 비교하세요."
}
