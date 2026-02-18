#!/usr/bin/env pwsh

# Financial Backend Services - Production Stop Script

Write-Host "🛑 Stopping Financial Backend Services" -ForegroundColor Red
Write-Host "=====================================" -ForegroundColor Red

# Stop all services
docker-compose down

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ All services stopped successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "💡 To remove all data volumes as well, run:" -ForegroundColor Yellow
    Write-Host "   docker-compose down -v" -ForegroundColor White
    Write-Host ""
    Write-Host "🔍 To view stopped containers:" -ForegroundColor Yellow
    Write-Host "   docker-compose ps -a" -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "❌ Error stopping services" -ForegroundColor Red
    exit 1
}