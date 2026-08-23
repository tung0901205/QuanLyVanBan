$ErrorActionPreference = "Stop"

Write-Host "=== QLDA - Khoi dong ban da sua luong va phan quyen ===" -ForegroundColor Cyan

if (-not (Test-Path ".\docker-compose.yml")) {
    throw "Khong tim thay docker-compose.yml. Hay mo PowerShell tai THU MUC GOC cua du an."
}

$services = docker compose config --services
if ($LASTEXITCODE -ne 0) { throw "docker compose config that bai." }
if ($services -notcontains "db-migrations") {
    throw "Ban dang chay nham source cu: docker-compose.yml khong co db-migrations."
}

Write-Host "Dung cac container cu (giu nguyen volume du lieu)..." -ForegroundColor Yellow
docker compose down --remove-orphans
if ($LASTEXITCODE -ne 0) { throw "docker compose down that bai." }

Write-Host "Build va khoi dong toan bo he thong..." -ForegroundColor Yellow
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { throw "docker compose up that bai." }

Write-Host "Cho cac service khoi dong..." -ForegroundColor Yellow
Start-Sleep -Seconds 20

docker compose ps

Write-Host "`n=== Log migration ===" -ForegroundColor Cyan
docker compose logs db-migrations --tail=120

Write-Host "`nMo trinh duyet: http://localhost:5173" -ForegroundColor Green
Write-Host "Tai khoan mau:" -ForegroundColor Green
Write-Host "  Admin:       admin / 123456"
Write-Host "  Chuyen vien: icetruong / 123456"
Write-Host "  Truong CNTT: lanhdao / 123456"
Write-Host "  Truong KHTC: lanhdao_ktc / 123456"
Write-Host "  Truong HC:   lanhdao_hc / 123456"
Write-Host "  Truong QLDA: lanhdao_qlda / 123456"
Write-Host "  Giam doc:    giamdoc / 123456"
Write-Host "`nNeu trinh duyet con token cu, mo Console va chay:" -ForegroundColor Yellow
Write-Host 'sessionStorage.clear(); location.href="/login";'
