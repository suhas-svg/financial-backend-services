#!/usr/bin/env pwsh

# Financial Backend Services - Production Verification Script

Write-Host "🏦 Verifying Financial Backend Services in Production Mode" -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Green

# Check if services are running
Write-Host ""
Write-Host "📊 Service Status:" -ForegroundColor Cyan
docker-compose ps

Write-Host ""
Write-Host "🏥 Health Check Results:" -ForegroundColor Cyan

# Check Account Service Health
try {
    $accountHealth = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 10
    if ($accountHealth.status -eq "UP") {
        Write-Host "✅ Account Service (8080): HEALTHY" -ForegroundColor Green
        Write-Host "   Database: $($accountHealth.components.db.status)" -ForegroundColor White
    } else {
        Write-Host "⚠️  Account Service (8080): $($accountHealth.status)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Account Service (8080): FAILED - $($_.Exception.Message)" -ForegroundColor Red
}

# Check Transaction Service Health
try {
    $transactionHealth = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -TimeoutSec 10
    if ($transactionHealth.status -eq "UP") {
        Write-Host "✅ Transaction Service (8081): HEALTHY" -ForegroundColor Green
        Write-Host "   Database: $($transactionHealth.components.db.status)" -ForegroundColor White
    } else {
        Write-Host "⚠️  Transaction Service (8081): $($transactionHealth.status)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Transaction Service (8081): FAILED - $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "🗄️ Database Connections:" -ForegroundColor Cyan
Write-Host "  • Account Database:     myfirstdb (port 5432)" -ForegroundColor White
Write-Host "  • Transaction Database: transactiondb (port 5433)" -ForegroundColor White

Write-Host ""
Write-Host "🌐 Service Endpoints:" -ForegroundColor Cyan
Write-Host "  • Account Service:      http://localhost:8080" -ForegroundColor White
Write-Host "  • Transaction Service:  http://localhost:8081" -ForegroundColor White
Write-Host "  • Account Health:       http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host "  • Transaction Health:   http://localhost:8081/actuator/health" -ForegroundColor White

Write-Host ""
Write-Host "🎉 Production verification complete!" -ForegroundColor Green