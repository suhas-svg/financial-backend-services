# Working Endpoint Test Script

Write-Host "=== Financial Services Endpoint Testing ===" -ForegroundColor Cyan

$passed = 0
$failed = 0

function Test-Service {
    param($Name, $Url, $Method = "GET", $Body = $null, $ExpectedStatus = 200)
    
    try {
        $params = @{
            Uri = $Url
            Method = $Method
            TimeoutSec = 5
        }
        
        if ($Body) {
            $params.Body = $Body
            $params.ContentType = "application/json"
        }
        
        $response = Invoke-RestMethod @params
        Write-Host "PASS: $Name" -ForegroundColor Green
        $script:passed++
        return $response
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq $ExpectedStatus) {
            Write-Host "PASS: $Name (Status $statusCode as expected)" -ForegroundColor Green
            $script:passed++
        } else {
            Write-Host "FAIL: $Name (Status $statusCode)" -ForegroundColor Red
            $script:failed++
        }
    }
}

# Test Account Service
Write-Host "`n=== Account Service Tests ===" -ForegroundColor Yellow

Test-Service "Account Service API" "http://localhost:8080/api/auth/register" "POST" '{}' 400
Test-Service "Account Service Health (8080)" "http://localhost:8080/actuator/health"
Test-Service "Account Service Health (9001)" "http://localhost:9001/actuator/health"

# Test user registration with valid data
$validUser = '{"username":"testuser123","email":"test123@example.com","password":"TestPassword123!","firstName":"Test","lastName":"User"}'
$registerResponse = Test-Service "Valid User Registration" "http://localhost:8080/api/auth/register" "POST" $validUser 201

# Test Transaction Service
Write-Host "`n=== Transaction Service Tests ===" -ForegroundColor Yellow

Test-Service "Transaction Service Health (8081)" "http://localhost:8081/actuator/health"
Test-Service "Transaction Service Health (9002)" "http://localhost:9002/actuator/health"

# Test Database Connections
Write-Host "`n=== Database Tests ===" -ForegroundColor Yellow

$accountDb = docker exec -e PGPASSWORD=postgres account-service-postgres-dev psql -U postgres -d myfirstdb -c "SELECT 1;" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "PASS: Account Database Connection" -ForegroundColor Green
    $passed++
} else {
    Write-Host "FAIL: Account Database Connection" -ForegroundColor Red
    $failed++
}

$transactionDb = docker exec -e PGPASSWORD=postgres transaction-service-postgres-dev psql -U postgres -d transactiondb -c "SELECT 1;" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "PASS: Transaction Database Connection" -ForegroundColor Green
    $passed++
} else {
    Write-Host "FAIL: Transaction Database Connection" -ForegroundColor Red
    $failed++
}

# Test Redis
Write-Host "`n=== Redis Test ===" -ForegroundColor Yellow

$redisTest = docker exec transaction-service-redis-dev redis-cli -a redis-password ping 2>&1
if ($redisTest -match "PONG") {
    Write-Host "PASS: Redis Connection" -ForegroundColor Green
    $passed++
} else {
    Write-Host "FAIL: Redis Connection" -ForegroundColor Red
    $failed++
}

# Summary
Write-Host "`n=== SUMMARY ===" -ForegroundColor Cyan
Write-Host "Passed: $passed" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor Red

$total = $passed + $failed
$successRate = [math]::Round(($passed / $total) * 100, 1)
Write-Host "Success Rate: $successRate%" -ForegroundColor White

if ($successRate -ge 70) {
    Write-Host "`nSTATUS: Most services are working!" -ForegroundColor Green
} else {
    Write-Host "`nSTATUS: Several issues need attention" -ForegroundColor Yellow
}

Write-Host "`n=== Available Endpoints ===" -ForegroundColor Cyan
Write-Host "Account Service: http://localhost:8080/api" -ForegroundColor White
Write-Host "Transaction Service: http://localhost:8081/api" -ForegroundColor White