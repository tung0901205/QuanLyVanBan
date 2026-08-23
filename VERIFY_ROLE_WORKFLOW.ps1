$ErrorActionPreference = "Continue"

Write-Host "=== KIEM TRA BAN SUA QLDA ===" -ForegroundColor Cyan

Write-Host "`n[1] Compose services" -ForegroundColor Yellow
$services = docker compose config --services
$services | ForEach-Object { Write-Host " - $_" }
if ($services -notcontains "db-migrations") {
    Write-Host "[FAIL] Khong co db-migrations - ban dang chay nham source." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Co db-migrations" -ForegroundColor Green

Write-Host "`n[2] Container status" -ForegroundColor Yellow
docker compose ps

Write-Host "`n[3] Database schema" -ForegroundColor Yellow
$sql = @"
SELECT 'roles' AS check_name, string_agg(manhomquyen, ', ' ORDER BY manhomquyen) AS result
FROM nhomquyen
WHERE upper(manhomquyen) IN ('ADMIN','CHUYEN_VIEN','LANH_DAO');

SELECT 'active_managers' AS check_name, string_agg(nd.username, ', ' ORDER BY nd.username) AS result
FROM nguoidung nd
JOIN nhomquyen nq ON nq.id = nd.nhomquyenid
WHERE nd.trangthai = 1 AND upper(nq.manhomquyen) = 'LANH_DAO';

SELECT 'vanban_ai_columns' AS check_name, string_agg(column_name, ', ' ORDER BY column_name) AS result
FROM information_schema.columns
WHERE table_schema='public' AND table_name='vanban'
  AND column_name IN ('aiconfidence','aiphanloai','noidungocr');

SELECT 'required_tables' AS check_name, string_agg(table_name, ', ' ORDER BY table_name) AS result
FROM information_schema.tables
WHERE table_schema='public' AND table_name IN ('chukyso','uyquyen','vanban');
"@
$sql | docker exec -i qlda-postgres psql -U postgres -d QLDA

Write-Host "`n[4] Health endpoints" -ForegroundColor Yellow
$urls = @(
    "http://localhost:8761/actuator/health",
    "http://localhost:8081/actuator/health",
    "http://localhost:8082/actuator/health",
    "http://localhost:8083/actuator/health",
    "http://localhost:8084/actuator/health",
    "http://localhost:8085/actuator/health",
    "http://localhost:8080/actuator/health",
    "http://localhost:5173"
)
foreach ($url in $urls) {
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 8
        Write-Host "[OK] $url -> $($response.StatusCode)" -ForegroundColor Green
    } catch {
        Write-Host "[FAIL] $url -> $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`nKiem tra xong. Neu API chua OK, doi 20-30 giay va chay lai script." -ForegroundColor Cyan

Write-Host "`n[5] API theo vai tro" -ForegroundColor Yellow
function Login-Qlda([string]$Username, [string]$Password) {
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json
    return Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $body -TimeoutSec 15
}

function Test-GetApi([string]$Name, [string]$Uri, [string]$Token) {
    try {
        $headers = @{ Authorization = "Bearer $Token" }
        $null = Invoke-RestMethod -Method Get -Uri $Uri -Headers $headers -TimeoutSec 15
        Write-Host "[OK] $Name" -ForegroundColor Green
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Write-Host "[FAIL] $Name -> HTTP $status $($_.Exception.Message)" -ForegroundColor Red
    }
}

try {
    $staffLogin = Login-Qlda "icetruong" "123456"
    $staffToken = $staffLogin.data.accessToken
    Test-GetApi "Chuyen vien - thong tin hien tai" "http://localhost:8080/api/auth/me" $staffToken
    Test-GetApi "Chuyen vien - loai van ban" "http://localhost:8080/api/documents/types?suDung=true" $staffToken
    Test-GetApi "Chuyen vien - don vi" "http://localhost:8080/api/auth/don-vi?size=200&suDung=true" $staffToken
    Test-GetApi "Chuyen vien - danh ba Truong don vi" "http://localhost:8080/api/auth/directory/users?role=LANH_DAO" $staffToken
    Test-GetApi "Chuyen vien - van ban den" "http://localhost:8080/api/documents/incoming?page=0&size=5" $staffToken
} catch {
    Write-Host "[FAIL] Dang nhap icetruong -> $($_.Exception.Message)" -ForegroundColor Red
}

try {
    $managerLogin = Login-Qlda "lanhdao" "123456"
    $managerToken = $managerLogin.data.accessToken
    Test-GetApi "Truong don vi - thong tin hien tai" "http://localhost:8080/api/auth/me" $managerToken
    Test-GetApi "Truong don vi - danh sach cho phe duyet" "http://localhost:8080/api/workflows/approvals/pending?page=0&size=5" $managerToken
    Test-GetApi "Truong don vi - uy quyen" "http://localhost:8080/api/workflows/delegations?page=0&size=5" $managerToken
} catch {
    Write-Host "[FAIL] Dang nhap lanhdao -> $($_.Exception.Message)" -ForegroundColor Red
}
