$ErrorActionPreference = "Continue"
Write-Host "=== QLDA V4 AI verification ===" -ForegroundColor Cyan

Write-Host "`n[1] Source markers"
$checks = @(
  ".\FIXES_2026-08-21-V4_AI.md",
  ".\qlda-system\ai-service\src\main\java\com\qlda\aiservice\service\chatbot\SystemKnowledgeService.java",
  ".\qlda-system\ai-service\src\main\java\com\qlda\aiservice\controller\InternalAiController.java"
)
foreach ($p in $checks) { Write-Host "$p = $(Test-Path $p)" }

Write-Host "`n[2] Containers"
docker compose ps

Write-Host "`n[3] AI upload mount"
docker inspect qlda-ai --format '{{range .Mounts}}{{println .Destination .RW}}{{end}}'

Write-Host "`n[4] Frontend V4 marker"
docker exec qlda-frontend sh -c "grep -R 'Hỏi về văn bản, quy trình, thống kê' -n /usr/share/nginx/html/assets 2>/dev/null | head -1"

Write-Host "`n[5] AI log tail"
docker compose logs ai-service --tail=60

Write-Host "`nHay test tren UI: 'cach tai len van ban', 'uy quyen nhu nao', 'di choi di', 'tim van ban ma ...', va nut Tom tat." -ForegroundColor Green
