# Comprehensive Test Script

Write-Host "=== COMPREHENSIVE FINANCIAL SERVICES TEST ===" -ForegroundColor Cyan

$passed = 0
$failed = 0

function Test-Service {
    param($Name, $Url, $Method = "GET", $Body = $null, $Headers = @{}, $ExpectedStatus = 200)
    
    try {
        $params = @{
            Uri = $Url
            Method = $Method
            Headers = $Headers
            TimeoutSec = 10
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
            Write-Host "PASS: $Name (Expected $statusCode)" -ForegroundColor Green
            $script:passed++
        } else {
            Write-Host "FAIL: $Name (Status: $statusCode)" -ForegroundColor Red
            $script:failed++
        }
    }
}

# Test Account Service
Write-Host "`n=== ACCOUNT SERVICE TESTS ===" -ForegroundColor Yellow

Test-Service "Account Service Health" "http://localhost:8080/actuator/health"

# Get authentication token
$loginBody = @{username="testuser123"; password="TestPassword123!"} | ConvertTo-Json
$loginResponse = Test-Service "User Login" "http://localhost:8080/api/auth/login" "POST" $loginBody

if ($loginResponse -and $loginResponse.accessToken) {
    $token = $loginResponse.accessToken
    $headers = @{"Authorization" = "Bearer $token"}
    Write-Host "Authentication successful" -ForegroundColor Green
    
    # Test account creation
    $accountBody = @{
        accountType = "SAVINGS"
        balance = 15000.00
        ownerId = "testuser123"
    } | ConvertTo-Json
    
    $accountResponse = Test-Service "Create Account" "http://localhost:8080/api/accounts" "POST" $accountBody $headers 201
    
    if ($accountResponse -and $accountResponse.id) {
        $accountId = $accountResponse.id
        Write-Host "Account created: ID = $accountId" -ForegroundColor Green
        
        # Test account retrieval
        Test-Service "Get Account Details" "http://localhost:8080/api/accounts/$accountId" "GET" $null $headers
    }
} else {
    Write-Host "Skipping authenticated tests - Login failed" -ForegroundColor Yellow
}

# Test Transaction Service Infrastructure
Write-Host "`n=== TRANSACTION SERVICE INFRASTRUCTURE ===" -ForegroundColor Yellow

# Test database
$dbTest = docker exec -e PGPASSWORD=postgres transaction-service-postgres-dev psql -U postgres -d transactiondb -c "SELECT 1;" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "PASS: Transaction Database" -ForegroundColor Green
    $passed++
} else {
    Write-Host "FAIL: Transaction Database" -ForegroundColor Red
    $failed++
}

# Test Redis
$redisTest = docker exec transaction-service-redis-dev redis-cli -a redis-password ping 2>&1
if ($redisTest -match "PONG") {
    Write-Host "PASS: Redis Cache" -ForegroundColor Green
    $passed++
} else {
    Write-Host "FAIL: Redis Cache" -ForegroundColor Red
    $failed++
}

# Test Transaction Service
Write-Host "`n=== TRANSACTION SERVICE TESTS ===" -ForegroundColor Yellow

# Check if service is running
$portCheck = netstat -ano | findstr :8081
if ($portCheck) {
    Write-Host "PASS: Transaction Service Running (Port 8081)" -ForegroundColor Green
    $passed++
} else {
    Write-Host "FAIL: Transaction Service Not Running" -ForegroundColor Red
    $failed++
}

# Test basic connectivity
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081" -Method GET -TimeoutSec 5
    Write-Host "PASS: Transaction Service Responds ($($response.StatusCode))" -ForegroundColor Green
    $passed++
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 403) {
        Write-Host "PASS: Transaction Service Responds (403 - Auth Required)" -ForegroundColor Green
        $passed++
    } else {
        Write-Host "FAIL: Transaction Service Responds ($statusCode)" -ForegroundColor Red
        $failed++
    }
}

# Test actuator health
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -Method GET -TimeoutSec 10
    Write-Host "PASS: Transaction Service Health ($($health.status))" -ForegroundColor Green
    $passed++
} catch {
    Write-Host "FAIL: Transaction Service Health (503)" -ForegroundColor Red
    $failed++
}

# Test authenticated endpoints if we have auth
if ($headers -and $accountId) {
    Write-Host "`n=== TRANSACTION API TESTS ===" -ForegroundColor Yellow
    
    Test-Service "Get Transactions" "http://localhost:8081/api/transactions" "GET" $null $headers
    Test-Service "Get Transaction Limits" "http://localhost:8081/api/transactions/limits" "GET" $null $headers
    
    # Test deposit
    $depositBody = @{
        accountId = $accountId
        amount = 3000.00
        description = "Comprehensive test deposit"
    } | ConvertTo-Json
    
    Test-Service "Process Deposit" "http://localhost:8081/api/transactions/deposit" "POST" $depositBody $headers 201
}

# Results
Write-Host "`n=== RESULTS ===" -ForegroundColor Cyan
$total = $passed + $failed
$successRate = if ($total -gt 0) { [math]::Round(($passed / $total) * 100, 1) } else { 0 }

Write-Host "Passed: $passed" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor Red
Write-Host "Success Rate: $successRate%" -ForegroundColor $(if ($successRate -ge 70) { "Green" } elseif ($successRate -ge 50) { "Yellow" } else { "Red" })

if ($successRate -ge 70) {
    Write-Host "`nEXCELLENT! Most services are working!" -ForegroundColor Green
} elseif ($successRate -ge 50) {
    Write-Host "`nGOOD! Core functionality is working" -ForegroundColor Yellow
} else {
    Write-Host "`nNEEDS WORK! Several issues to resolve" -ForegroundColor Red
}

Write-Host "`n=== WORKING COMMANDS ===" -ForegroundColor Cyan
if ($loginResponse) {
    Write-Host "# Login and get token:" -ForegroundColor Gray
    Write-Host '$loginBody = @{username="testuser123"; password="TestPassword123!"} | ConvertTo-Json' -ForegroundColor White
    Write-Host '$loginResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $loginBody -ContentType "application/json"' -ForegroundColor White
    Write-Host '$token = $loginResponse.accessToken' -ForegroundColor White
    Write-Host '$headers = @{"Authorization" = "Bearer $token"}' -ForegroundColor White
}

if ($accountResponse) {
    Write-Host "`n# Create account:" -ForegroundColor Gray
    Write-Host '$accountBody = @{accountType="CHECKING"; balance=10000.00; ownerId="testuser123"} | ConvertTo-Json' -ForegroundColor White
    Write-Host 'Invoke-RestMethod -Uri "http://localhost:8080/api/accounts" -Method POST -Body $accountBody -ContentType "application/json" -Headers $headers' -ForegroundColor White
}

Write-Host "`n=== TEST COMPLETE ===" -ForegroundColor Cyan