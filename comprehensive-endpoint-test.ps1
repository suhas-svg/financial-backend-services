# Comprehensive Endpoint Testing Script

Write-Host "=== Comprehensive Financial Services Endpoint Testing ===" -ForegroundColor Cyan

$testResults = @()
$passed = 0
$failed = 0

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Url,
        [string]$Method = "GET",
        [string]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200,
        [string]$Description = ""
    )
    
    try {
        $params = @{
            Uri = $Url
            Method = $Method
            Headers = $Headers
            TimeoutSec = 10
            ErrorAction = "Stop"
        }
        
        if ($Body) {
            $params.Body = $Body
            $params.ContentType = "application/json"
        }
        
        $response = Invoke-RestMethod @params
        Write-Host "✓ $Name" -ForegroundColor Green
        $script:testResults += @{Name=$Name; Status="PASS"; Details="Success"; Response=$response}
        $script:passed++
        return $response
    }
    catch {
        $statusCode = $null
        try {
            $statusCode = $_.Exception.Response.StatusCode.value__
        } catch {}
        
        if ($statusCode -eq $ExpectedStatus) {
            Write-Host "✓ $Name (Expected $ExpectedStatus)" -ForegroundColor Green
            $script:testResults += @{Name=$Name; Status="PASS"; Details="Expected status $statusCode"}
            $script:passed++
        } else {
            Write-Host "✗ $Name - Status: $statusCode" -ForegroundColor Red
            $script:testResults += @{Name=$Name; Status="FAIL"; Details="Status: $statusCode, Error: $($_.Exception.Message)"}
            $script:failed++
        }
    }
}

# Test 1: Account Service Health Checks
Write-Host "`n=== Account Service Health Checks ===" -ForegroundColor Yellow

Test-Endpoint "Account Service Health (Port 8080)" "http://localhost:8080/actuator/health"
Test-Endpoint "Account Service Health (Port 9001)" "http://localhost:9001/actuator/health"
Test-Endpoint "Account Service Info" "http://localhost:8080/actuator/info"

# Test 2: Account Service Authentication Endpoints
Write-Host "`n=== Account Service Authentication ===" -ForegroundColor Yellow

Test-Endpoint "Register Endpoint (Empty Body)" "http://localhost:8080/api/auth/register" "POST" '{}' @{} 400
Test-Endpoint "Login Endpoint (Empty Body)" "http://localhost:8080/api/auth/login" "POST" '{}' @{} 400

# Test 3: User Registration and Login Flow
Write-Host "`n=== User Registration and Login Flow ===" -ForegroundColor Yellow

$testUser = @{
    username = "testuser$(Get-Random -Minimum 1000 -Maximum 9999)"
    email = "test$(Get-Random -Minimum 1000 -Maximum 9999)@example.com"
    password = "TestPassword123!"
    firstName = "Test"
    lastName = "User"
} | ConvertTo-Json

$registerResponse = Test-Endpoint "User Registration" "http://localhost:8080/api/auth/register" "POST" $testUser @{} 201

if ($registerResponse) {
    $loginBody = @{
        username = ($testUser | ConvertFrom-Json).username
        password = "TestPassword123!"
    } | ConvertTo-Json
    
    $loginResponse = Test-Endpoint "User Login" "http://localhost:8080/api/auth/login" "POST" $loginBody
    
    if ($loginResponse -and $loginResponse.token) {
        $authHeaders = @{ "Authorization" = "Bearer $($loginResponse.token)" }
        
        # Test authenticated endpoints
        Write-Host "`n=== Authenticated Account Endpoints ===" -ForegroundColor Yellow
        Test-Endpoint "Get User Accounts" "http://localhost:8080/api/accounts" "GET" $null $authHeaders
        Test-Endpoint "Get User Profile" "http://localhost:8080/api/accounts/profile" "GET" $null $authHeaders
        
        # Create an account
        $accountBody = @{
            accountType = "SAVINGS"
            initialBalance = 1000.00
        } | ConvertTo-Json
        
        $accountResponse = Test-Endpoint "Create Account" "http://localhost:8080/api/accounts" "POST" $accountBody $authHeaders 201
        
        if ($accountResponse -and $accountResponse.id) {
            Test-Endpoint "Get Account Details" "http://localhost:8080/api/accounts/$($accountResponse.id)" "GET" $null $authHeaders
            Test-Endpoint "Get Account Balance" "http://localhost:8080/api/accounts/$($accountResponse.id)/balance" "GET" $null $authHeaders
            
            # Store account info for transaction tests
            $script:testAccountId = $accountResponse.id
            $script:testAuthHeaders = $authHeaders
        }
    }
}

# Test 4: Transaction Service Health Checks
Write-Host "`n=== Transaction Service Health Checks ===" -ForegroundColor Yellow

Test-Endpoint "Transaction Service Health (Port 8081)" "http://localhost:8081/actuator/health"
Test-Endpoint "Transaction Service Health (Port 9002)" "http://localhost:9002/actuator/health"
Test-Endpoint "Transaction Service Info" "http://localhost:8081/actuator/info"

# Test 5: Transaction Service Endpoints (if auth token available)
if ($script:testAuthHeaders -and $script:testAccountId) {
    Write-Host "`n=== Transaction Service Endpoints ===" -ForegroundColor Yellow
    
    Test-Endpoint "Get User Transactions" "http://localhost:8081/api/transactions" "GET" $null $script:testAuthHeaders
    Test-Endpoint "Get Transaction Limits" "http://localhost:8081/api/transactions/limits" "GET" $null $script:testAuthHeaders
    
    # Test deposit transaction
    $depositBody = @{
        accountId = $script:testAccountId
        amount = 100.00
        description = "Test deposit transaction"
    } | ConvertTo-Json
    
    Test-Endpoint "Deposit Transaction" "http://localhost:8081/api/transactions/deposit" "POST" $depositBody $script:testAuthHeaders 201
    
    # Test withdrawal transaction
    $withdrawalBody = @{
        accountId = $script:testAccountId
        amount = 50.00
        description = "Test withdrawal transaction"
    } | ConvertTo-Json
    
    Test-Endpoint "Withdrawal Transaction" "http://localhost:8081/api/transactions/withdraw" "POST" $withdrawalBody $script:testAuthHeaders 201
}

# Test 6: Database Connectivity
Write-Host "`n=== Database Connectivity Tests ===" -ForegroundColor Yellow

try {
    $accountDbResult = docker exec -e PGPASSWORD=postgres account-service-postgres-dev psql -U postgres -d myfirstdb -c "SELECT 1;" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Account Database Connection" -ForegroundColor Green
        $testResults += @{Name="Account Database"; Status="PASS"; Details="PostgreSQL connection successful"}
        $passed++
    } else {
        Write-Host "✗ Account Database Connection" -ForegroundColor Red
        $testResults += @{Name="Account Database"; Status="FAIL"; Details="Connection failed"}
        $failed++
    }
} catch {
    Write-Host "✗ Account Database Connection" -ForegroundColor Red
    $testResults += @{Name="Account Database"; Status="FAIL"; Details="Error: $($_.Exception.Message)"}
    $failed++
}

try {
    $transactionDbResult = docker exec -e PGPASSWORD=postgres transaction-service-postgres-dev psql -U postgres -d transactiondb -c "SELECT 1;" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Transaction Database Connection" -ForegroundColor Green
        $testResults += @{Name="Transaction Database"; Status="PASS"; Details="PostgreSQL connection successful"}
        $passed++
    } else {
        Write-Host "✗ Transaction Database Connection" -ForegroundColor Red
        $testResults += @{Name="Transaction Database"; Status="FAIL"; Details="Connection failed"}
        $failed++
    }
} catch {
    Write-Host "✗ Transaction Database Connection" -ForegroundColor Red
    $testResults += @{Name="Transaction Database"; Status="FAIL"; Details="Error: $($_.Exception.Message)"}
    $failed++
}

# Test 7: Redis Connection
Write-Host "`n=== Redis Connectivity Test ===" -ForegroundColor Yellow

try {
    $redisResult = docker exec transaction-service-redis-dev redis-cli -a redis-password ping 2>&1
    if ($redisResult -match "PONG") {
        Write-Host "✓ Redis Connection" -ForegroundColor Green
        $testResults += @{Name="Redis Connection"; Status="PASS"; Details="Redis PONG response received"}
        $passed++
    } else {
        Write-Host "✗ Redis Connection" -ForegroundColor Red
        $testResults += @{Name="Redis Connection"; Status="FAIL"; Details="No PONG response"}
        $failed++
    }
} catch {
    Write-Host "✗ Redis Connection" -ForegroundColor Red
    $testResults += @{Name="Redis Connection"; Status="FAIL"; Details="Error: $($_.Exception.Message)"}
    $failed++
}

# Final Results Summary
Write-Host "`n=== TEST RESULTS SUMMARY ===" -ForegroundColor Cyan
Write-Host "Total Tests: $($passed + $failed)" -ForegroundColor White
Write-Host "Passed: $passed" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor Red

$successRate = [math]::Round(($passed / ($passed + $failed)) * 100, 1)
Write-Host "Success Rate: $successRate%" -ForegroundColor $(if ($successRate -ge 80) { "Green" } elseif ($successRate -ge 60) { "Yellow" } else { "Red" })

if ($failed -gt 0) {
    Write-Host "`n=== FAILED TESTS ===" -ForegroundColor Red
    $testResults | Where-Object { $_.Status -eq "FAIL" } | ForEach-Object {
        Write-Host "  ✗ $($_.Name): $($_.Details)" -ForegroundColor Red
    }
}

Write-Host "`n=== SERVICE ENDPOINTS ===" -ForegroundColor Cyan
Write-Host "Account Service API: http://localhost:8080/api" -ForegroundColor White
Write-Host "Account Service Actuator: http://localhost:8080/actuator (or :9001)" -ForegroundColor White
Write-Host "Transaction Service API: http://localhost:8081/api" -ForegroundColor White
Write-Host "Transaction Service Actuator: http://localhost:8081/actuator (or :9002)" -ForegroundColor White

if ($successRate -ge 80) {
    Write-Host "`n🎉 EXCELLENT! Most endpoints are working correctly!" -ForegroundColor Green
} elseif ($successRate -ge 60) {
    Write-Host "`n⚠️  GOOD! Most core functionality is working, some issues to address." -ForegroundColor Yellow
} else {
    Write-Host "`n❌ NEEDS ATTENTION! Several endpoints require fixes." -ForegroundColor Red
}

Write-Host "`n=== Testing Complete ===" -ForegroundColor Cyan