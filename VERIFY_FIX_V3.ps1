$ErrorActionPreference = "Continue"
Write-Host "=== 1. Kiem tra source V3 ===" -ForegroundColor Cyan
$checks = @(
  ".\qlda-system\migrations\V010__template_document_flow_notifications_sla.sql",
  ".\qlda-system\document-service\src\main\resources\template-samples\TPL-CV-01.docx",
  ".\qlda-system\document-service\src\main\resources\template-samples\TPL-QD-01.docx"
)
foreach ($p in $checks) { Write-Host "$p -> $(Test-Path $p)" }

Write-Host "`n=== 2. Kiem tra container ===" -ForegroundColor Cyan
docker compose ps

Write-Host "`n=== 3. Kiem tra frontend dang chay dung V3 ===" -ForegroundColor Cyan
docker exec qlda-frontend sh -c "grep -R 'Tạo văn bản hoàn chỉnh' -n /usr/share/nginx/html/assets 2>/dev/null | head"
docker exec qlda-frontend sh -c "grep -R 'Văn bản đang xử lý sẽ hết hạn trong 48 giờ' -n /usr/share/nginx/html/assets 2>/dev/null | head"

Write-Host "`n=== 4. Migration ===" -ForegroundColor Cyan
docker inspect qlda-db-migrations --format '{{.State.ExitCode}}'
docker compose logs db-migrations --tail=80

Write-Host "`n=== 5. Template DB ===" -ForegroundColor Cyan
docker exec qlda-postgres psql -U postgres -d QLDA -c "select matemplate,tentemplate,tepmau,sudung from templatevanban order by id;"

Write-Host "`n=== 6. Van ban di moi nhat ===" -ForegroundColor Cyan
docker exec qlda-postgres psql -U postgres -d QLDA -c "select id,sokyhieu,trichyeu,phanloaivanban,trangthai,ngayvanban,donvichutriid from vanban where daxoa=false order by id desc limit 10;"

Write-Host "`n=== 7. Thong bao moi nhat ===" -ForegroundColor Cyan
docker exec qlda-postgres psql -U postgres -d QLDA -c "select id,tieude,loaithongbao,nguoinhanid,vanbanid,dadoc,ngaygui from thongbao order by id desc limit 10;"

Write-Host "`nHoan tat. Neu 2 lenh grep o muc 3 co ket qua thi frontend V3 dang duoc phuc vu." -ForegroundColor Green
