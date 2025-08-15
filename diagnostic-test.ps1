# Diagnostic E2E Test for Financial Backend Services
$AccountServiceUrl = "http://localhost:8083"
$TransactionServiceUrl = "http://localhost:8082"

Write-Host "=== DIAGNOSTIC E2E TESTING ===" -ForegroundColor Blue

# Test 1: Detailed User Registration
Write-Host "`n--- Testing User Registration (Detailed) ---" -ForegroundColor Cyan
try {
    $userData = @{
        username = "diagnostic-user-$(Get-Date -Format 'yyyyMMddHHmmss')"
        password = "TestPassword123!"
    }
    
    Write-Host "Sending registration request..." -ForegroundColor Gray
    $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/auth/register" `
        -Method Post -Body ($userData | ConvertTo-Json) -ContentType "application/json" -TimeoutSec 10
    
    Write-Host "Registration Response:" -ForegroundColor Green
    Write-Host ($response | ConvertTo-Json -Depth 3) -ForegroundColor White
    
    # Check if registration was successful (different response format)
    if ($response.username -like "*successfully*" -or $response.roles) {
        Write-Host "PASS: User Registration" -ForegroundColor Green
        $script:TestUser = $userData
        $registrationSuccess = $true
    } else {
        Write-Host "FAIL: User Registration - Unexpected response format" -ForegroundColor Red
        $registrationSuccess = $false
    }
}
catch {
    Write-Host "ERROR: User Registration - $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    $registrationSuccess = $false
}

# Test 2: Detailed User Login
if ($registrationSuccess -and $script:TestUser) {
    Write-Host "`n--- Testing User Login (Detailed) ---" -ForegroundColor Cyan
    try {
        Write-Host "Sending login request..." -ForegroundColor Gray
        $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/auth/login" `
            -Method Post -Body ($script:TestUser | ConvertTo-Json) -ContentType "application/json" -TimeoutSec 10
        
        Write-Host "Login Response:" -ForegroundColor Green
        Write-Host ($response | ConvertTo-Json -Depth 3) -ForegroundColor White
        
        if ($response.token) {
            Write-Host "PASS: User Login - Token received" -ForegroundColor Green
            $script:AuthToken = $response.token
            $loginSuccess = $true
        } else {
            Write-Host "FAIL: User Login - No token in response" -ForegroundColor Red
            $loginSuccess = $false
        }
    }
    catch {
        Write-Host "ERROR: User Login - $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        $loginSuccess = $false
    }
} else {
    Write-Host "`n--- Skipping Login Test - Registration Failed ---" -ForegroundColor Yellow
    $loginSuccess = $false
}

# Test 3: Detailed Account Creation
if ($loginSuccess -and $script:AuthToken) {
    Write-Host "`n--- Testing Account Creation (Detailed) ---" -ForegroundColor Cyan
    try {
        $headers = @{
            "Authorization" = "Bearer $($script:AuthToken)"
            "Content-Type" = "application/json"
        }
        
        $accountData = @{
            ownerId = $script:TestUser.username
            accountType = "STANDARD"
            initialBalance = 1000.00
        }
        
        Write-Host "Sending account creation request..." -ForegroundColor Gray
        Write-Host "Account Data: $($accountData | ConvertTo-Json)" -ForegroundColor Gray
        
        $response = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts" `
            -Method Post -Headers $headers -Body ($accountData | ConvertTo-Json) -TimeoutSec 10
        
        Write-Host "Account Creation Response:" -ForegroundColor Green
        Write-Host ($response | ConvertTo-Json -Depth 3) -ForegroundColor White
        
        if ($response.id) {
            Write-Host "PASS: Account Creation - Account ID: $($response.id)" -ForegroundColor Green
            $script:TestAccountId = $response.id
            $accountSuccess = $true
        } else {
            Write-Host "FAIL: Account Creation - No account ID in response" -ForegroundColor Red
            $accountSuccess = $false
        }
    }
    catch {
        Write-Host "ERROR: Account Creation - $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        $accountSuccess = $false
    }
} else {
    Write-Host "`n--- Skipping Account Creation Test - Login Failed ---" -ForegroundColor Yellow
    $accountSuccess = $false
}

# Test 4: Detailed Transaction Limits
if ($loginSuccess -and $script:AuthToken) {
    Write-Host "`n--- Testing Transaction Limits (Detailed) ---" -ForegroundColor Cyan
    try {
        $headers = @{
            "Authorization" = "Bearer $($script:AuthToken)"
        }
        
        Write-Host "Sending transaction limits request..." -ForegroundColor Gray
        $response = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/limits" `
            -Method Get -Headers $headers -TimeoutSec 10
        
        Write-Host "Transaction Limits Response:" -ForegroundColor Green
        Write-Host ($response | ConvertTo-Json -Depth 3) -ForegroundColor White
        
        if ($response.dailyLimit) {
            Write-Host "PASS: Transaction Limits - Daily Limit: $($response.dailyLimit)" -ForegroundColor Green
            $limitsSuccess = $true
        } else {
            Write-Host "FAIL: Transaction Limits - No limits in response" -ForegroundColor Red
            $limitsSuccess = $false
        }
    }
    catch {
        Write-Host "ERROR: Transaction Limits - $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        $limitsSuccess = $false
    }
} else {
    Write-Host "`n--- Skipping Transaction Limits Test - Login Failed ---" -ForegroundColor Yellow
    $limitsSuccess = $false
}

# Test 5: Detailed Deposit Transaction
if ($accountSuccess -and $script:AuthToken -and $script:TestAccountId) {
    Write-Host "`n--- Testing Deposit Transaction (Detailed) ---" -ForegroundColor Cyan
    try {
        $headers = @{
            "Authorization" = "Bearer $($script:AuthToken)"
            "Content-Type" = "application/json"
        }
        
        $depositData = @{
            accountId = $script:TestAccountId.ToString()
            amount = 500.00
            description = "Diagnostic Test Deposit"
        }
        
        Write-Host "Sending deposit request..." -ForegroundColor Gray
        Write-Host "Deposit Data: $($depositData | ConvertTo-Json)" -ForegroundColor Gray
        
        $response = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/deposit" `
            -Method Post -Headers $headers -Body ($depositData | ConvertTo-Json) -TimeoutSec 10
        
        Write-Host "Deposit Response:" -ForegroundColor Green
        Write-Host ($response | ConvertTo-Json -Depth 3) -ForegroundColor White
        
        if ($response.transactionId) {
            Write-Host "PASS: Deposit Transaction - ID: $($response.transactionId)" -ForegroundColor Green
            $depositSuccess = $true
        } else {
            Write-Host "FAIL: Deposit Transaction - No transaction ID in response" -ForegroundColor Red
            $depositSuccess = $false
        }
    }
    catch {
        Write-Host "ERROR: Deposit Transaction - $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        $depositSuccess = $false
    }
} else {
    Write-Host "`n--- Skipping Deposit Test - Prerequisites Failed ---" -ForegroundColor Yellow
    $depositSuccess = $false
}

# Summary
Write-Host "`n=== DIAGNOSTIC SUMMARY ===" -ForegroundColor Blue
Write-Host "Registration: $(if($registrationSuccess){'PASS'}else{'FAIL'})" -ForegroundColor $(if($registrationSuccess){'Green'}else{'Red'})
Write-Host "Login: $(if($loginSuccess){'PASS'}else{'FAIL'})" -ForegroundColor $(if($loginSuccess){'Green'}else{'Red'})
Write-Host "Account Creation: $(if($accountSuccess){'PASS'}else{'FAIL'})" -ForegroundColor $(if($accountSuccess){'Green'}else{'Red'})
Write-Host "Transaction Limits: $(if($limitsSuccess){'PASS'}else{'FAIL'})" -ForegroundColor $(if($limitsSuccess){'Green'}else{'Red'})
Write-Host "Deposit Transaction: $(if($depositSuccess){'PASS'}else{'FAIL'})" -ForegroundColor $(if($depositSuccess){'Green'}else{'Red'})

if ($script:TestUser) {
    Write-Host "`nTest User: $($script:TestUser.username)" -ForegroundColor Gray
}
if ($script:TestAccountId) {
    Write-Host "Test Account ID: $($script:TestAccountId)" -ForegroundColor Gray
}

Write-Host "`nDiagnostic Complete!" -ForegroundColor Blue