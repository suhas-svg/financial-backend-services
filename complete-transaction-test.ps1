# Complete Transaction Service Test

Write-Host "=== COMPLETE TRANSACTION SERVICE TEST ===" -ForegroundColor Cyan

$passed = 0
$failed = 0

function Test-Endpoint {
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
        $statusCode = $null
        $errorMessage = $_.Exception.Message
        
        try {
            $statusCode = $_.Exception.Response.StatusCode.value__
        } catch {}
        
        if ($statusCode -eq $ExpectedStatus) {
            Write-Host "PASS: $Name (Expected $statusCode)" -ForegroundColor Green
            $script:passed++
        } else {
            Write-Host "FAIL: $Name (Status: $statusCode) - $errorMessage" -ForegroundColor Red
            $script:failed++
        }
    }
}

# Step 1: Verify Account Service is working (needed for auth)
Write-Host "`n=== STEP 1: Account Service Verification ===" -ForegroundColor Yellow

$accountHealth = Test-Endpoint "Account Service Health" "http://localhost:8080/actuator/health"

# Create test user for authentication
$testUser = @{
    username = "transactiontest$(Get-Random -Minimum 1000 -Maximum 9999)"
    email = "transactiontest$(Get-Random -Minimum 1000 -Maximum 9999)@example.com"
    password = "TestPassword123!"
    firstName = "Transaction"
    lastName = "Test"
} | ConvertTo-Json

$registerResponse = Test-Endpoint "User Registration" "http://localhost:8080/api/auth/register" "POST" $testUser @{} 201

if ($registerResponse) {
    # Login to get auth token
    $loginBody = @{
        username = ($testUser | ConvertFrom-Json).username
        password = "TestPassword123!"
    } | ConvertTo-Json
    
    $loginResponse = Test-Endpoint "User Login" "http://localhost:8080/api/auth/login" "POST" $loginBody
    
    if ($loginResponse -and $loginResponse.token) {
        $authHeaders = @{ "Authorization" = "Bearer $($loginResponse.token)" }
        Write-Host "Authentication successful - Token obtained" -ForegroundColor Green
        
        # Create an account for transaction testing
        $accountBody = @{
            accountType = "CHECKING"
            initialBalance = 2000.00
        } | ConvertTo-Json
        
        $accountResponse = Test-Endpoint "Create Test Account" "http://localhost:8080/api/accounts" "POST" $accountBody $authHeaders 201
        
        if ($accountResponse -and $accountResponse.id) {
            $testAccountId = $accountResponse.id
            Write-Host "Test account created: $testAccountId" -ForegroundColor Green
        }
    }
}

# Step 2: Test Transaction Service Infrastructure
Write-Host "`n=== STEP 2: Transaction Service Infrastructure ===" -ForegroundColor Yellow

# Test database connection
$transactionDb = docker exec -e PGPASSWORD=postgres transaction-service-postgres-dev psql -U postgres -d transactiondb -c "SELECT 1;" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "PASS: Transaction Database Connection" -ForegroundColor Green
    $passed++
} else {
    Write-Host "FAIL: Transaction Database Connection" -ForegroundColor Red
    $failed++
}

# Test Redis connection
$redisTest = docker exec transaction-service-redis-dev redis-cli -a redis-password ping 2>&1
if ($redisTest -match "PONG") {
    Write-Host "PASS: Redis Connection" -ForegroundColor Green
    $passed++
} else {
    Write-Host "FAIL: Redis Connection" -ForegroundColor Red
    $failed++
}

# Step 3: Test Transaction Service Endpoints
Write-Host "`n=== STEP 3: Transaction Service Endpoints ===" -ForegroundColor Yellow

# Test health endpoint with more detailed error handling
Write-Host "Testing transaction service health endpoint..." -ForegroundColor Gray
try {
    $healthResponse = Invoke-WebRequest -Uri "http://localhost:8081/actuator/health" -Method GET -TimeoutSec 10
    Write-Host "PASS: Transaction Service Health (Status: $($healthResponse.StatusCode))" -ForegroundColor Green
    $passed++
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $responseBody = ""
    try {
        $responseBody = $_.Exception.Response | ConvertTo-Json
    } catch {}
    Write-Host "FAIL: Transaction Service Health (Status: $statusCode)" -ForegroundColor Red
    Write-Host "Error details: $($_.Exception.Message)" -ForegroundColor Gray
    $failed++
}

# Test actuator info endpoint
Test-Endpoint "Transaction Service Info" "http://localhost:8081/actuator/info"

# Test metrics endpoint
Test-Endpoint "Transaction Service Metrics" "http://localhost:8081/actuator/metrics"

# Step 4: Test Transaction API Endpoints (if auth is available)
if ($authHeaders -and $testAccountId) {
    Write-Host "`n=== STEP 4: Transaction API Endpoints ===" -ForegroundColor Yellow
    
    # Test transaction endpoints with authentication
    Test-Endpoint "Get User Transactions" "http://localhost:8081/api/transactions" "GET" $null $authHeaders
    Test-Endpoint "Get Transaction Limits" "http://localhost:8081/api/transactions/limits" "GET" $null $authHeaders
    
    # Test deposit transaction
    $depositRequest = @{
        accountId = $testAccountId
        amount = 500.00
        description = "Test deposit transaction"
    } | ConvertTo-Json
    
    Test-Endpoint "Process Deposit" "http://localhost:8081/api/transactions/deposit" "POST" $depositRequest $authHeaders 201
    
    # Test withdrawal transaction
    $withdrawalRequest = @{
        accountId = $testAccountId
        amount = 200.00
        description = "Test withdrawal transaction"
    } | ConvertTo-Json
    
    Test-Endpoint "Process Withdrawal" "http://localhost:8081/api/transactions/withdraw" "POST" $withdrawalRequest $authHeaders 201
    
    # Test transfer transaction (if we have multiple accounts)
    $transferRequest = @{
        fromAccountId = $testAccountId
        toAccountId = $testAccountId  # For testing, using same account
        amount = 100.00
        description = "Test transfer transaction"
    } | ConvertTo-Json
    
    Test-Endpoint "Process Transfer" "http://localhost:8081/api/transactions/transfer" "POST" $transferRequest $authHeaders 201
    
    # Get updated transaction history
    Test-Endpoint "Get Updated Transaction History" "http://localhost:8081/api/transactions" "GET" $null $authHeaders
} else {
    Write-Host "Skipping API tests - Authentication not available" -ForegroundColor Yellow
}

# Step 5: Alternative Health Check on Different Port
Write-Host "`n=== STEP 5: Alternative Health Checks ===" -ForegroundColor Yellow

# Try actuator on port 9002 (as configured in properties)
Test-Endpoint "Transaction Service Health (Port 9002)" "http://localhost:9002/actuator/health"

# Final Results
Write-Host "`n=== FINAL RESULTS ===" -ForegroundColor Cyan
$total = $passed + $failed
$successRate = if ($total -gt 0) { [math]::Round(($passed / $total) * 100, 1) } else { 0 }

Write-Host "Total Tests: $total" -ForegroundColor White
Write-Host "Passed: $passed" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor Red
Write-Host "Success Rate: $successRate%" -ForegroundColor $(if ($successRate -ge 70) { "Green" } elseif ($successRate -ge 50) { "Yellow" } else { "Red" })

# Service Status Summary
Write-Host "`n=== SERVICE STATUS SUMMARY ===" -ForegroundColor Cyan
Write-Host "Account Service: http://localhost:8080 - " -NoNewline -ForegroundColor White
if ($accountHealth) {
    Write-Host "WORKING" -ForegroundColor Green
} else {
    Write-Host "ISSUES" -ForegroundColor Red
}

Write-Host "Transaction Service: http://localhost:8081 - " -NoNewline -ForegroundColor White
if ($successRate -ge 50) {
    Write-Host "PARTIALLY WORKING" -ForegroundColor Yellow
} else {
    Write-Host "NEEDS ATTENTION" -ForegroundColor Red
}

Write-Host "`n=== NEXT STEPS ===" -ForegroundColor Cyan
if ($successRate -ge 70) {
    Write-Host "Great! Most transaction service endpoints are working." -ForegroundColor Green
} else {
    Write-Host "Transaction service needs configuration fixes." -ForegroundColor Yellow
    Write-Host "Check application logs for detailed error information." -ForegroundColor Gray
}

Write-Host "`n=== Available Endpoints ===" -ForegroundColor Cyan
Write-Host "Transaction Service API: http://localhost:8081/api/transactions" -ForegroundColor White
Write-Host "Transaction Service Health: http://localhost:8081/actuator/health" -ForegroundColor White
Write-Host "Transaction Service Metrics: http://localhost:8081/actuator/metrics" -ForegroundColor White