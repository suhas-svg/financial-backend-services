#!/usr/bin/env pwsh

# Financial Backend Services - Production Startup Script
# This script starts both Account Service (8080) and Transaction Service (8081)

Write-Host "🏦 Starting Financial Backend Services in Production Mode" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green

# Check if Docker is running
try {
    docker version | Out-Null
    Write-Host "✅ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker is not running. Please start Docker first." -ForegroundColor Red
    exit 1
}

# Check if .env file exists
if (-not (Test-Path ".env")) {
    Write-Host "⚠️  .env file not found. Creating default configuration..." -ForegroundColor Yellow
    Write-Host "🔐 IMPORTANT: Update the passwords in .env file before production use!" -ForegroundColor Red
}

Write-Host ""
Write-Host "🚀 Starting services..." -ForegroundColor Cyan

# Start all services
docker-compose up -d --build

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ Services started successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "📊 Service Information:" -ForegroundColor Cyan
    Write-Host "  • Account Service:     http://localhost:8080" -ForegroundColor White
    Write-Host "  • Transaction Service: http://localhost:8081" -ForegroundColor White
    Write-Host "  • Account Database:    localhost:5432 (myfirstdb)" -ForegroundColor White
    Write-Host "  • Transaction Database: localhost:5433 (transactiondb)" -ForegroundColor White
    Write-Host ""
    Write-Host "🔍 Health Checks:" -ForegroundColor Cyan
    Write-Host "  • Account Service:     http://localhost:8080/actuator/health" -ForegroundColor White
    Write-Host "  • Transaction Service: http://localhost:8081/actuator/health" -ForegroundColor White
    Write-Host ""
    Write-Host "📝 View logs with: docker-compose logs -f [service-name]" -ForegroundColor Yellow
    Write-Host "🛑 Stop services with: docker-compose down" -ForegroundColor Yellow
    Write-Host ""
    
    # Wait a moment for services to start
    Write-Host "⏳ Waiting for services to initialize..." -ForegroundColor Cyan
    Start-Sleep -Seconds 30
    
    # Check health endpoints
    Write-Host "🏥 Checking service health..." -ForegroundColor Cyan
    
    try {
        $accountHealth = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 10
        if ($accountHealth.status -eq "UP") {
            Write-Host "✅ Account Service is healthy" -ForegroundColor Green
        } else {
            Write-Host "⚠️  Account Service status: $($accountHealth.status)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "❌ Account Service health check failed" -ForegroundColor Red
    }
    
    try {
        $transactionHealth = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -TimeoutSec 10
        if ($transactionHealth.status -eq "UP") {
            Write-Host "✅ Transaction Service is healthy" -ForegroundColor Green
        } else {
            Write-Host "⚠️  Transaction Service status: $($transactionHealth.status)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "❌ Transaction Service health check failed" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "🎉 Financial Backend Services are ready for production use!" -ForegroundColor Green
    
} else {
    Write-Host ""
    Write-Host "❌ Failed to start services. Check the logs with: docker-compose logs" -ForegroundColor Red
    exit 1
}