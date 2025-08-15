# Debug script to check service status

Write-Host "=== Service Debug Information ===" -ForegroundColor Cyan

Write-Host "`nChecking Docker containers:" -ForegroundColor Yellow
docker ps -a --filter "name=account-service"
docker ps -a --filter "name=transaction-service"

Write-Host "`nChecking ports:" -ForegroundColor Yellow
Write-Host "Port 8080:" -ForegroundColor White
netstat -ano | findstr ":8080"
Write-Host "Port 8081:" -ForegroundColor White  
netstat -ano | findstr ":8081"

Write-Host "`nTesting connectivity:" -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080" -Method GET -TimeoutSec 3
    Write-Host "Account Service API (8080): $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Account Service API (8080): Failed - $($_.Exception.Message)" -ForegroundColor Red
}

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081" -Method GET -TimeoutSec 3
    Write-Host "Transaction Service API (8081): $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Transaction Service API (8081): Failed - $($_.Exception.Message)" -ForegroundColor Red
}