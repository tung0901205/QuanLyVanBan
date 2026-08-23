Write-Host "=== QLDA V4.2 verification ===" -ForegroundColor Cyan

docker compose ps

Write-Host "`n[1] AI internal security + OCR endpoint" -ForegroundColor Yellow
Select-String -Path ".\qlda-system\ai-service\src\main\java\com\qlda\aiservice\config\SecurityConfig.java" -Pattern "securityMatcher\(\"/internal/\*\*\"\)"
Select-String -Path ".\qlda-system\ai-service\src\main\java\com\qlda\aiservice\controller\AiController.java" -Pattern "/ocr/file"

Write-Host "`n[2] Semantic search excludes guide chunks" -ForegroundColor Yellow
Select-String -Path ".\qlda-system\ai-service\src\main\java\com\qlda\aiservice\service\VectorSearchService.java" -Pattern "van_ban_id > 0"

Write-Host "`n[3] Notification newest first" -ForegroundColor Yellow
Select-String -Path ".\qlda-system\notification-service\src\main\java\com\qlda\notificationservice\notification\service\NotificationService.java" -Pattern "Sort.Order.desc"

Write-Host "`n[4] Running frontend contains V4.2 semantic label" -ForegroundColor Yellow
docker exec qlda-frontend sh -c "grep -R 'Gemini/local embedding' -n /usr/share/nginx/html/assets 2>/dev/null | head"

Write-Host "`n[5] AI bootstrap log" -ForegroundColor Yellow
docker compose logs ai-service --tail=200 | Select-String "AI document bootstrap completed|Started AiServiceApplication|ERROR|Exception"
