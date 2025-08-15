# Final Comprehensive E2E Test with JWT Fix
$AccountServiceUrl = "http://localhost:8080"
$TransactionServiceUrl = "http://localhost:8081"

$TestResults = @{
    TotalTests = 0
    PassedTests = 0
    FailedTests = 0
    FixedIssues = @()
}

function Test-And-Report {
    param([string]$TestName, [scriptblock]$TestCode)
    
    $TestResults.TotalTests++
    Write-Host "`n--- Testing: $TestName ---" -ForegroundColor Cyan
    
    try {
        $result = & $TestCode
        if ($result) {
            Write-Host "PASS: $TestName" -ForegroundColor Green
            $TestResults.PassedTests++
            return $true
        } else {
            Write-Host "FAIL: $TestName" -ForegroundColor Red
            $TestResults.FailedTests++
            return $false
        }
    }
    catch {
        Write-Host "ERROR: $TestName - $($_.Exception.Message)" -ForegroundColor Red
        $TestResults.FailedTests++
        return $false
    }
}

Write-Host "=== FINAL COMPREHENSIVE E2E TEST WITH JWT FIX ===" -ForegroundColor Blue
Write-Host "Account Service: $AccountServiceUrl"
Write-Host "Transaction Service: $TransactionServiceUrl"
Write-Host "Note: Please restart both services before running this test" -ForegroundColor Yellow

# Test 1-2: Service Health Checks
Test-And-Report "Account Service Health" {
    $response = Invoke-RestMethod -Uri "$AccountServiceUrl/actuator/health" -Method Get -TimeoutSec 10
    Write-Host "  Status: $($response.status)" -ForegroundColor Gray
    return $response.status -eq "UP"
}

Test-And-Report "Transaction Service Health" {
    $response = Invoke-RestMethod -Uri "$TransactionServiceUrl/actuator/health" -Method Get -TimeoutSec 10
    Write-Host "  Status: $($response.status)" -ForegroundColor Gray
    return $response.status -eq "UP"
}

# Test 3: User Registration
Test-And-Report "User Registration" {
    $userData = @{
        username = "final-jwt-user-$(Get-Date -Format 'yyyyMMddHHmmss')"
        password = "TestPassword123!"
    }
    
    $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/auth/register" `
        -Method Post -Body ($userData | ConvertTo-Json) -ContentType "application/json" -TimeoutSec 10
    
    if ($response.username -like "*successfully*" -or $response.roles) {
        $script:TestUser = $userData
        Write-Host "  User created: $($userData.username)" -ForegroundColor Gray
        return $true
    }
    return $false
}

# Test 4: User Login
Test-And-Report "User Login" {
    if (-not $script:TestUser) { return $false }
    
    $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/auth/login" `
        -Method Post -Body ($script:TestUser | ConvertTo-Json) -ContentType "application/json" -TimeoutSec 10
    
    if ($response.accessToken) {
        $script:AuthToken = $response.accessToken
        Write-Host "  Token received with roles support" -ForegroundColor Gray
        return $true
    }
    return $false
}

# Test 5: Account Creation (FIXED)
Test-And-Report "Account Creation - CHECKING (FIXED)" {
    if (-not $script:AuthToken) { return $false }
    
    $headers = @{
        "Authorization" = "Bearer $($script:AuthToken)"
        "Content-Type" = "application/json"
    }
    
    $accountData = @{
        ownerId = $script:TestUser.username
        accountType = "CHECKING"
        balance = 1000.00
    }
    
    $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts" `
        -Method Post -Headers $headers -Body ($accountData | ConvertTo-Json) -TimeoutSec 10
    
    if ($response.id) {
        $script:TestAccountId = $response.id
        Write-Host "  Account created: ID=$($response.id), Balance=$($response.balance)" -ForegroundColor Gray
        return $true
    }
    return $false
}

# Test 6: Account Creation - SAVINGS
Test-And-Report "Account Creation - SAVINGS (FIXED)" {
    if (-not $script:AuthToken) { return $false }
    
    $headers = @{
        "Authorization" = "Bearer $($script:AuthToken)"
        "Content-Type" = "application/json"
    }
    
    $accountData = @{
        ownerId = $script:TestUser.username
        accountType = "SAVINGS"
        balance = 2000.00
    }
    
    $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts" `
        -Method Post -Headers $headers -Body ($accountData | ConvertTo-Json) -TimeoutSec 10
    
    if ($response.id) {
        $script:TestSavingsAccountId = $response.id
        Write-Host "  Savings account created: ID=$($response.id)" -ForegroundColor Gray
        return $true
    }
    return $false
}

# Test 7: Account Retrieval
Test-And-Report "Account Retrieval (FIXED)" {
    if (-not $script:AuthToken -or -not $script:TestAccountId) { return $false }
    
    $headers = @{ "Authorization" = "Bearer $($script:AuthToken)" }
    
    $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts/$($script:TestAccountId)" `
        -Method Get -Headers $headers -TimeoutSec 10
    
    if ($response.id -eq $script:TestAccountId) {
        Write-Host "  Retrieved account: Balance=$($response.balance)" -ForegroundColor Gray
        return $true
    }
    return $false
}

# Test 8: Account Listing
Test-And-Report "Account Listing (FIXED)" {
    if (-not $script:AuthToken) { return $false }
    
    $headers = @{ "Authorization" = "Bearer $($script:AuthToken)" }
    
    $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts" `
        -Method Get -Headers $headers -TimeoutSec 10
    
    if ($response.content -and $response.content.Count -gt 0) {
        Write-Host "  Found $($response.content.Count) accounts" -ForegroundColor Gray
        return $true
    }
    return $false
}

# Test 9: Account Filtering
Test-And-Report "Account Filtering by Type (FIXED)" {
    if (-not $script:AuthToken) { return $false }
    
    $headers = @{ "Authorization" = "Bearer $($script:AuthToken)" }
    
    $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts?accountType=CHECKING" `
        -Method Get -Headers $headers -TimeoutSec 10
    
    if ($response.content) {
        Write-Host "  Found $($response.content.Count) CHECKING accounts" -ForegroundColor Gray
        return $true
    }
    return $false
}

# Test 10: Transaction Limits (JWT FIX TEST)
Test-And-Report "Transaction Limits (JWT FIX)" {
    if (-not $script:AuthToken) { return $false }
    
    $headers = @{ "Authorization" = "Bearer $($script:AuthToken)" }
    
    try {
        $response = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/limits" `
            -Method Get -Headers $headers -TimeoutSec 10
        
        if ($response.dailyLimit) {
            Write-Host "  JWT FIX SUCCESSFUL! Daily limit: $($response.dailyLimit)" -ForegroundColor Gray
            $TestResults.FixedIssues += "JWT Authentication - Transaction Service now accepts Account Service tokens"
            return $true
        }
        return $false
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "  JWT issue persists (Status: $statusCode)" -ForegroundColor Yellow
        if ($_.Exception.Response) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $responseBody = $reader.ReadToEnd()
                Write-Host "  Error details: $responseBody" -ForegroundColor Yellow
            }
            catch { }
        }
        return $false
    }
}

# Test 11: Deposit Transaction (SHOULD NOW WORK)
Test-And-Report "Deposit Transaction (JWT FIX)" {
    if (-not $script:AuthToken -or -not $script:TestAccountId) { return $false }
    
    $headers = @{
        "Authorization" = "Bearer $($script:AuthToken)"
        "Content-Type" = "application/json"
    }
    
    $depositData = @{
        accountId = $script:TestAccountId.ToString()
        amount = 500.00
        description = "E2E Test Deposit - JWT Fixed"
    }
    
    try {
        $response = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/deposit" `
            -Method Post -Headers $headers -Body ($depositData | ConvertTo-Json) -TimeoutSec 10
        
        if ($response.transactionId) {
            Write-Host "  Deposit successful: ID=$($response.transactionId), Status=$($response.status)" -ForegroundColor Gray
            $script:DepositTransactionId = $response.transactionId
            $TestResults.FixedIssues += "Deposit Transaction - Working with JWT fix"
            return $true
        }
        return $false
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "  Deposit failed (Status: $statusCode)" -ForegroundColor Yellow
        return $false
    }
}

# Test 12: Withdrawal Transaction (SHOULD NOW WORK)
Test-And-Report "Withdrawal Transaction (JWT FIX)" {
    if (-not $script:AuthToken -or -not $script:TestAccountId) { return $false }
    
    $headers = @{
        "Authorization" = "Bearer $($script:AuthToken)"
        "Content-Type" = "application/json"
    }
    
    $withdrawalData = @{
        accountId = $script:TestAccountId.ToString()
        amount = 200.00
        description = "E2E Test Withdrawal - JWT Fixed"
    }
    
    try {
        $response = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/withdraw" `
            -Method Post -Headers $headers -Body ($withdrawalData | ConvertTo-Json) -TimeoutSec 10
        
        if ($response.transactionId) {
            Write-Host "  Withdrawal successful: ID=$($response.transactionId), Status=$($response.status)" -ForegroundColor Gray
            $TestResults.FixedIssues += "Withdrawal Transaction - Working with JWT fix"
            return $true
        }
        return $false
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "  Withdrawal failed (Status: $statusCode)" -ForegroundColor Yellow
        return $false
    }
}

# Test 13: Transaction History (SHOULD NOW WORK)
Test-And-Report "Transaction History (JWT FIX)" {
    if (-not $script:AuthToken -or -not $script:TestAccountId) { return $false }
    
    $headers = @{ "Authorization" = "Bearer $($script:AuthToken)" }
    
    try {
        $response = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/account/$($script:TestAccountId)" `
            -Method Get -Headers $headers -TimeoutSec 10
        
        if ($response.content) {
            Write-Host "  Found $($response.content.Count) transactions for account" -ForegroundColor Gray
            $TestResults.FixedIssues += "Transaction History - Working with JWT fix"
            return $true
        }
        return $false
    }
    catch {
        Write-Host "  Transaction history failed" -ForegroundColor Yellow
        return $false
    }
}

# Test 14: User Transaction History (SHOULD NOW WORK)
Test-And-Report "User Transaction History (JWT FIX)" {
    if (-not $script:AuthToken) { return $false }
    
    $headers = @{ "Authorization" = "Bearer $($script:AuthToken)" }
    
    try {
        $response = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions" `
            -Method Get -Headers $headers -TimeoutSec 10
        
        if ($response.content) {
            Write-Host "  Found $($response.content.Count) user transactions" -ForegroundColor Gray
            return $true
        }
        return $false
    }
    catch {
        Write-Host "  User transaction history failed" -ForegroundColor Yellow
        return $false
    }
}

# Test 15: Transaction Search (SHOULD NOW WORK)
Test-And-Report "Transaction Search (JWT FIX)" {
    if (-not $script:AuthToken) { return $false }
    
    $headers = @{ "Authorization" = "Bearer $($script:AuthToken)" }
    
    try {
        $response = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/search?type=DEPOSIT" `
            -Method Get -Headers $headers -TimeoutSec 10
        
        if ($response.content) {
            Write-Host "  Found $($response.content.Count) deposit transactions" -ForegroundColor Gray
            return $true
        }
        return $false
    }
    catch {
        Write-Host "  Transaction search failed" -ForegroundColor Yellow
        return $false
    }
}

# Test 16: Service Integration - Balance Update (SHOULD NOW WORK)
Test-And-Report "Service Integration - Balance Update (JWT FIX)" {
    if (-not $script:AuthToken -or -not $script:TestAccountId) { return $false }
    
    # Get initial balance
    $headers = @{ "Authorization" = "Bearer $($script:AuthToken)" }
    
    try {
        $initialAccount = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts/$($script:TestAccountId)" `
            -Method Get -Headers $headers -TimeoutSec 10
        $initialBalance = $initialAccount.balance
        
        # Perform a deposit via Transaction Service
        $headers["Content-Type"] = "application/json"
        $depositData = @{
            accountId = $script:TestAccountId.ToString()
            amount = 100.00
            description = "Integration Test Deposit"
        }
        
        $depositResponse = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/deposit" `
            -Method Post -Headers $headers -Body ($depositData | ConvertTo-Json) -TimeoutSec 10
        
        if ($depositResponse.transactionId) {
            # Wait for balance update
            Start-Sleep -Seconds 3
            
            # Check updated balance
            $updatedAccount = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts/$($script:TestAccountId)" `
                -Method Get -Headers @{ "Authorization" = "Bearer $($script:AuthToken)" } -TimeoutSec 10
            
            $expectedBalance = $initialBalance + 100.00
            $actualBalance = $updatedAccount.balance
            
            if ([math]::Abs($actualBalance - $expectedBalance) -lt 0.01) {
                Write-Host "  Integration successful: $initialBalance -> $actualBalance" -ForegroundColor Gray
                $TestResults.FixedIssues += "Service Integration - Cross-service balance updates working"
                return $true
            } else {
                Write-Host "  Balance update failed: Expected $expectedBalance, Got $actualBalance" -ForegroundColor Yellow
                return $false
            }
        }
        return $false
    }
    catch {
        Write-Host "  Integration test failed" -ForegroundColor Yellow
        return $false
    }
}

# Test 17-18: Error Handling (SHOULD STILL WORK)
Test-And-Report "Error Handling - Invalid Account Type" {
    if (-not $script:AuthToken) { return $false }
    
    $headers = @{
        "Authorization" = "Bearer $($script:AuthToken)"
        "Content-Type" = "application/json"
    }
    
    $invalidAccountData = @{
        ownerId = $script:TestUser.username
        accountType = "INVALID_TYPE"
        balance = 1000.00
    }
    
    try {
        $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts" `
            -Method Post -Headers $headers -Body ($invalidAccountData | ConvertTo-Json) -TimeoutSec 10
        return $false
    }
    catch {
        Write-Host "  Properly rejected invalid account type" -ForegroundColor Gray
        return $true
    }
}

Test-And-Report "Error Handling - Invalid Login" {
    $invalidLogin = @{
        username = "nonexistent-user"
        password = "wrongpassword"
    }
    
    try {
        $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/auth/login" `
            -Method Post -Body ($invalidLogin | ConvertTo-Json) -ContentType "application/json" -TimeoutSec 10
        return $false
    }
    catch {
        Write-Host "  Properly rejected invalid login" -ForegroundColor Gray
        return $true
    }
}

# Generate Final Report
Write-Host "`n=== FINAL COMPREHENSIVE TEST RESULTS ===" -ForegroundColor Blue

$successRate = if ($TestResults.TotalTests -gt 0) { 
    [math]::Round(($TestResults.PassedTests / $TestResults.TotalTests) * 100, 2) 
} else { 0 }

Write-Host "Total Tests: $($TestResults.TotalTests)" -ForegroundColor White
Write-Host "Passed: $($TestResults.PassedTests)" -ForegroundColor Green
Write-Host "Failed: $($TestResults.FailedTests)" -ForegroundColor Red
Write-Host "Success Rate: ${successRate}%" -ForegroundColor White

Write-Host "`n=== ISSUES FIXED ===" -ForegroundColor Blue
if ($TestResults.FixedIssues.Count -gt 0) {
    foreach ($fix in $TestResults.FixedIssues) {
        Write-Host "- $fix" -ForegroundColor Green
    }
} else {
    Write-Host "No new issues were fixed in this run" -ForegroundColor Yellow
}

Write-Host "`n=== SYSTEM ASSESSMENT ===" -ForegroundColor Blue
if ($successRate -ge 95) {
    Write-Host "EXCELLENT! System is working perfectly" -ForegroundColor Green
    Write-Host "Ready for production deployment" -ForegroundColor Green
} elseif ($successRate -ge 85) {
    Write-Host "VERY GOOD! System is working well" -ForegroundColor Green
    Write-Host "Minor issues may remain" -ForegroundColor Yellow
} elseif ($successRate -ge 70) {
    Write-Host "GOOD! Most functionality working" -ForegroundColor Yellow
    Write-Host "Some issues need attention" -ForegroundColor Yellow
} else {
    Write-Host "NEEDS IMPROVEMENT" -ForegroundColor Red
    Write-Host "Several issues require fixing" -ForegroundColor Red
}

if ($script:TestUser) {
    Write-Host "`n=== TEST DATA CREATED ===" -ForegroundColor Blue
    Write-Host "User: $($script:TestUser.username)" -ForegroundColor Gray
    if ($script:TestAccountId) {
        Write-Host "Checking Account ID: $($script:TestAccountId)" -ForegroundColor Gray
    }
    if ($script:TestSavingsAccountId) {
        Write-Host "Savings Account ID: $($script:TestSavingsAccountId)" -ForegroundColor Gray
    }
}

Write-Host "`nFinal E2E Testing with JWT Fix Complete!" -ForegroundColor Blue