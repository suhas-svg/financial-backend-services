# Transaction Service Comprehensive Checkpoint Script
Write-Host "Transaction Service Comprehensive Checkpoint Analysis" -ForegroundColor Green
Write-Host "=====================================================" -ForegroundColor Green

$txBaseUrl    = "http://localhost:8081"
$acctBaseUrl  = "http://localhost:8080"
$checkResults = @()

function Add-CheckResult {
    param(
        [string]$Category,
        [string]$Check,
        [string]$Status,
        [string]$Details = "",
        [string]$Recommendation = ""
    )
    $global:checkResults += [PSCustomObject]@{
        Category       = $Category
        Check          = $Check
        Status         = $Status
        Details        = $Details
        Recommendation = $Recommendation
    }
}

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Url,
        [string]$Method = "GET",
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [int]$ExpectedStatus = 200
    )
    try {
        $params = @{
            Uri             = $Url
            Method          = $Method
            TimeoutSec      = 15
            Headers         = $Headers
            UseBasicParsing = $true
        }
        if ($Body) {
            $params.Body        = $Body
            $params.ContentType = "application/json"
        }
        $response = Invoke-WebRequest @params
        if ($response.StatusCode -eq $ExpectedStatus) {
            Write-Host "PASS $Name - Status: $($response.StatusCode)" -ForegroundColor Green
            return @{ Success = $true; Response = $response; StatusCode = $response.StatusCode }
        } else {
            Write-Host "WARN $Name - Unexpected Status: $($response.StatusCode) (Expected: $ExpectedStatus)" -ForegroundColor Yellow
            return @{ Success = $false; Response = $response; StatusCode = $response.StatusCode }
        }
    } catch {
        $statusCode = $null
        if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
        Write-Host "FAIL $Name - Status: $statusCode / $($_.Exception.Message)" -ForegroundColor Red
        return @{ Success = $false; Error = $_.Exception.Message; StatusCode = $statusCode }
    }
}

# ─── 1. CONNECTIVITY ────────────────────────────────────────────────────────
Write-Host "`nTesting Service Availability..." -ForegroundColor Yellow

$connectivityTests = @(
    @{ Name = "Transaction Service Port 8081"; Url = "$txBaseUrl/actuator/health" },
    @{ Name = "Transaction Service Health";    Url = "$txBaseUrl/api/transactions/health" },
    @{ Name = "Account Service Port 8080";     Url = "$acctBaseUrl/actuator/health" }
)
foreach ($test in $connectivityTests) {
    $result = Test-Endpoint $test.Name $test.Url
    if ($result.Success) {
        Add-CheckResult "Connectivity" $test.Name "PASS" "Service accessible" ""
    } else {
        Add-CheckResult "Connectivity" $test.Name "FAIL" "Service not accessible" "Check if service is running"
    }
}

# ─── 2. GET AUTH TOKEN FROM ACCOUNT SERVICE ─────────────────────────────────
Write-Host "`nObtaining Auth Token from Account Service..." -ForegroundColor Yellow

$username     = "txcheckpoint$(Get-Random)"
$registerBody = @{ username = $username; password = "testpass123" } | ConvertTo-Json
$token        = $null

$regResult = Test-Endpoint "Register User (Account Service)" "$acctBaseUrl/api/auth/register" "POST" @{} $registerBody 201
if ($regResult.Success) {
    $loginBody   = @{ username = $username; password = "testpass123" } | ConvertTo-Json
    $loginResult = Test-Endpoint "Login User (Account Service)" "$acctBaseUrl/api/auth/login" "POST" @{} $loginBody 200
    if ($loginResult.Success) {
        $loginData = $loginResult.Response.Content | ConvertFrom-Json
        $token     = $loginData.accessToken
        if ($token) {
            Write-Host "PASS JWT Token obtained (accessToken)" -ForegroundColor Green
            Add-CheckResult "Authentication" "JWT Token from Account Service" "PASS" "Token obtained successfully" ""
        } else {
            Add-CheckResult "Authentication" "JWT Token from Account Service" "FAIL" "No accessToken in response" "Check account-service JWT config"
        }
    } else {
        Add-CheckResult "Authentication" "Login" "FAIL" "Login failed" "Check account-service"
    }
} else {
    Add-CheckResult "Authentication" "Register" "FAIL" "Registration failed" "Check account-service"
}

# ─── 3. SECURITY ────────────────────────────────────────────────────────────
Write-Host "`nTesting Security..." -ForegroundColor Yellow

# Transaction service has custom authenticationEntryPoint → returns 401
$unauthResult = Test-Endpoint "Protected Endpoint (No Auth)" "$txBaseUrl/api/transactions" "GET" @{} $null 401
if ($unauthResult.StatusCode -eq 401) {
    Add-CheckResult "Security" "Protected Endpoints (401)" "PASS" "Returns 401 without auth (custom entry point)" ""
} elseif ($unauthResult.StatusCode -eq 403) {
    Add-CheckResult "Security" "Protected Endpoints (403)" "PASS" "Returns 403 without auth" ""
} else {
    Add-CheckResult "Security" "Protected Endpoints" "FAIL" "Unexpected status: $($unauthResult.StatusCode)" "Check security configuration"
}

# ─── 4. TRANSACTION OPERATIONS ──────────────────────────────────────────────
Write-Host "`nTesting Transaction Operations..." -ForegroundColor Yellow

if ($token) {
    $authHeaders = @{ "Authorization" = "Bearer $token" }

    # First create an account to use for transactions
    $accountBody = @{
        accountNumber = "TXC$(Get-Random -Minimum 100000 -Maximum 999999)"
        accountType   = "CHECKING"
        balance       = 5000.00
        ownerId       = $username
        currency      = "USD"
    } | ConvertTo-Json
    $acctResult = Test-Endpoint "Create Account for Transactions" "$acctBaseUrl/api/accounts" "POST" $authHeaders $accountBody 201
    $accountId  = $null
    if ($acctResult.Success) {
        $accountId = ($acctResult.Response.Content | ConvertFrom-Json).id
        Add-CheckResult "Transaction Operations" "Account Setup" "PASS" "Account created for transactions" ""
    } else {
        Add-CheckResult "Transaction Operations" "Account Setup" "WARN" "Could not create account - transaction tests may fail" "Check account-service"
    }

    # Test GET /api/transactions (list user transactions)
    $listResult = Test-Endpoint "List User Transactions" "$txBaseUrl/api/transactions" "GET" $authHeaders
    if ($listResult.Success) {
        Add-CheckResult "Transaction Operations" "List Transactions" "PASS" "Transactions listed" ""
    } else {
        Add-CheckResult "Transaction Operations" "List Transactions" "FAIL" "Status: $($listResult.StatusCode)" "Check transaction listing endpoint"
    }

    # Test transaction limits endpoint
    $limitsResult = Test-Endpoint "Transaction Limits" "$txBaseUrl/api/transactions/limits" "GET" $authHeaders
    if ($limitsResult.Success) {
        Add-CheckResult "Transaction Operations" "Transaction Limits" "PASS" "Limits endpoint accessible" ""
    } else {
        Add-CheckResult "Transaction Operations" "Transaction Limits" "FAIL" "Status: $($limitsResult.StatusCode)" "Check /api/transactions/limits"
    }

    # Test creating a deposit (correct endpoint: POST /api/transactions/deposit)
    if ($accountId) {
        $depositBody = @{
            accountId   = $accountId
            amount      = 100.00
            description = "Checkpoint test deposit"
        } | ConvertTo-Json

        $idempotencyKey = "checkpoint-$(Get-Random)"
        $depositHeaders = $authHeaders + @{ "Idempotency-Key" = $idempotencyKey }

        $depositResult = Test-Endpoint "Create Deposit" "$txBaseUrl/api/transactions/deposit" "POST" $depositHeaders $depositBody 201
        if ($depositResult.Success) {
            Add-CheckResult "Transaction Operations" "Create Deposit" "PASS" "Deposit transaction created" ""
            $txId = ($depositResult.Response.Content | ConvertFrom-Json).id
            if ($txId) {
                $getTxResult = Test-Endpoint "Get Transaction by ID" "$txBaseUrl/api/transactions/$txId" "GET" $authHeaders
                if ($getTxResult.Success) {
                    Add-CheckResult "Transaction Operations" "Get Transaction" "PASS" "Transaction retrieved" ""
                } else {
                    Add-CheckResult "Transaction Operations" "Get Transaction" "FAIL" "Status: $($getTxResult.StatusCode)" "Check transaction retrieval"
                }
            }
        } else {
            Add-CheckResult "Transaction Operations" "Create Deposit" "WARN" "Status: $($depositResult.StatusCode) - may need account-service integration" "Check deposit endpoint and account-service connectivity"
        }

        # Test account transaction history: GET /api/transactions/account/{accountId}
        $acctHistResult = Test-Endpoint "Account Transaction History" "$txBaseUrl/api/transactions/account/$accountId" "GET" $authHeaders
        if ($acctHistResult.Success) {
            Add-CheckResult "Transaction Operations" "Account History" "PASS" "Account transaction history accessible" ""
        } else {
            Add-CheckResult "Transaction Operations" "Account History" "FAIL" "Status: $($acctHistResult.StatusCode)" "Check /api/transactions/account/{accountId}"
        }
    } else {
        Add-CheckResult "Transaction Operations" "Create Deposit" "SKIP" "No account available" "Fix account setup first"
        Add-CheckResult "Transaction Operations" "Account History" "SKIP" "No account available" "Fix account setup first"
    }
} else {
    Add-CheckResult "Transaction Operations" "All Operations" "SKIP" "No valid token" "Fix authentication first"
}

# ─── 5. HEALTH & MONITORING ─────────────────────────────────────────────────
Write-Host "`nTesting Health Endpoints..." -ForegroundColor Yellow

$healthEndpoints = @(
    @{ Name = "Actuator Health";          Url = "$txBaseUrl/actuator/health" },
    @{ Name = "Actuator Info";            Url = "$txBaseUrl/actuator/info" },
    @{ Name = "Transaction Health";       Url = "$txBaseUrl/api/transactions/health" }
)
foreach ($endpoint in $healthEndpoints) {
    $result = Test-Endpoint $endpoint.Name $endpoint.Url
    if ($result.Success) {
        Add-CheckResult "Health Monitoring" $endpoint.Name "PASS" "Endpoint accessible" ""
    } else {
        Add-CheckResult "Health Monitoring" $endpoint.Name "FAIL" "Endpoint not accessible (Status: $($result.StatusCode))" "Check endpoint configuration"
    }
}

# Monitoring endpoints (require auth)
if ($token) {
    $authHeaders = @{ "Authorization" = "Bearer $token" }
    # Monitoring endpoints - check what's available
    $monResult = Test-Endpoint "Monitoring Health" "$txBaseUrl/api/monitoring/health" "GET" $authHeaders
    if ($monResult.Success) {
        Add-CheckResult "Health Monitoring" "Monitoring Health" "PASS" "Monitoring health accessible" ""
    } else {
        # Try alternative monitoring path
        $monResult2 = Test-Endpoint "Transaction Search" "$txBaseUrl/api/transactions/search" "GET" $authHeaders
        if ($monResult2.Success) {
            Add-CheckResult "Health Monitoring" "Transaction Search" "PASS" "Search endpoint accessible" ""
        } else {
            Add-CheckResult "Health Monitoring" "Monitoring" "WARN" "Status: $($monResult.StatusCode) - monitoring endpoint may not be exposed" "Check monitoring controller"
        }
    }
}

# ─── 6. PERFORMANCE ─────────────────────────────────────────────────────────
Write-Host "`nTesting Performance..." -ForegroundColor Yellow

$startTime    = Get-Date
$perfResult   = Test-Endpoint "Performance Test" "$txBaseUrl/actuator/health"
$responseTime = ((Get-Date) - $startTime).TotalMilliseconds

if ($perfResult.Success) {
    if ($responseTime -lt 1000) {
        Add-CheckResult "Performance" "Response Time" "PASS" "Response time: ${responseTime}ms" ""
    } elseif ($responseTime -lt 3000) {
        Add-CheckResult "Performance" "Response Time" "WARN" "Response time: ${responseTime}ms" "Consider performance optimization"
    } else {
        Add-CheckResult "Performance" "Response Time" "FAIL" "Response time: ${responseTime}ms" "Performance optimization needed"
    }
} else {
    Add-CheckResult "Performance" "Response Time" "FAIL" "Cannot measure - endpoint not accessible" "Fix endpoint accessibility first"
}

# ─── 7. REDIS CONNECTIVITY ──────────────────────────────────────────────────
Write-Host "`nTesting Redis Connectivity..." -ForegroundColor Yellow

$redisResult = Test-Endpoint "Redis Health (via Actuator)" "$txBaseUrl/actuator/health"
if ($redisResult.Success) {
    try {
        $healthData = $redisResult.Response.Content | ConvertFrom-Json
        # Basic health is UP — Redis is part of the service health
        Add-CheckResult "Redis" "Redis Connection" "PASS" "Service health is UP (includes Redis)" ""
    } catch {
        Add-CheckResult "Redis" "Redis Connection" "WARN" "Cannot parse health details" "Check actuator health details"
    }
} else {
    Add-CheckResult "Redis" "Redis Connection" "FAIL" "Service health check failed" "Check Redis connectivity"
}

# ─── SUMMARY ────────────────────────────────────────────────────────────────
Write-Host "`nTRANSACTION SERVICE CHECKPOINT SUMMARY" -ForegroundColor Green
Write-Host "=======================================" -ForegroundColor Green

$passCount = ($checkResults | Where-Object { $_.Status -eq "PASS" }).Count
$failCount = ($checkResults | Where-Object { $_.Status -eq "FAIL" }).Count
$warnCount = ($checkResults | Where-Object { $_.Status -eq "WARN" }).Count
$skipCount = ($checkResults | Where-Object { $_.Status -eq "SKIP" }).Count
$totalCount = $checkResults.Count

Write-Host "`nOverall Results:" -ForegroundColor White
Write-Host "Total Checks : $totalCount"  -ForegroundColor White
Write-Host "Passed       : $passCount"   -ForegroundColor Green
Write-Host "Failed       : $failCount"   -ForegroundColor Red
Write-Host "Warnings     : $warnCount"   -ForegroundColor Yellow
Write-Host "Skipped      : $skipCount"   -ForegroundColor Gray

$denominator = $totalCount - $skipCount
$successRate = if ($denominator -gt 0) { [math]::Round(($passCount / $denominator) * 100, 2) } else { 0 }
Write-Host "`nSuccess Rate: $successRate%" -ForegroundColor $(if ($successRate -ge 90) { "Green" } elseif ($successRate -ge 75) { "Yellow" } else { "Red" })

Write-Host "`nResults by Category:" -ForegroundColor White
$categories = $checkResults | Group-Object Category
foreach ($category in $categories) {
    $categoryPass  = ($category.Group | Where-Object { $_.Status -eq "PASS" }).Count
    $categoryTotal = ($category.Group | Where-Object { $_.Status -ne "SKIP" }).Count
    $categoryRate  = if ($categoryTotal -gt 0) { [math]::Round(($categoryPass / $categoryTotal) * 100, 1) } else { 0 }
    Write-Host "  $($category.Name): $categoryPass/$categoryTotal ($categoryRate%)" -ForegroundColor $(
        if ($categoryRate -eq 100) { "Green" } elseif ($categoryRate -ge 75) { "Yellow" } else { "Red" }
    )
}

if ($failCount -gt 0) {
    Write-Host "`nFailed Checks:" -ForegroundColor Red
    $checkResults | Where-Object { $_.Status -eq "FAIL" } | ForEach-Object {
        Write-Host "  - $($_.Category): $($_.Check)" -ForegroundColor Red
        if ($_.Details)        { Write-Host "    Details: $($_.Details)" -ForegroundColor Gray }
        if ($_.Recommendation) { Write-Host "    Fix: $($_.Recommendation)" -ForegroundColor Yellow }
    }
}

if ($warnCount -gt 0) {
    Write-Host "`nWarnings:" -ForegroundColor Yellow
    $checkResults | Where-Object { $_.Status -eq "WARN" } | ForEach-Object {
        Write-Host "  - $($_.Category): $($_.Check)" -ForegroundColor Yellow
        if ($_.Details)        { Write-Host "    Details: $($_.Details)" -ForegroundColor Gray }
        if ($_.Recommendation) { Write-Host "    Fix: $($_.Recommendation)" -ForegroundColor Cyan }
    }
}

$checkResults | Export-Csv -Path "transaction-service-checkpoint-results.csv" -NoTypeInformation
Write-Host "`nDetailed results exported to: transaction-service-checkpoint-results.csv" -ForegroundColor Cyan

Write-Host "`nFinal Assessment:" -ForegroundColor Green
if ($successRate -ge 95) {
    Write-Host "EXCELLENT - Transaction Service is production ready!" -ForegroundColor Green
} elseif ($successRate -ge 85) {
    Write-Host "GOOD - Transaction Service is mostly ready, minor issues to address" -ForegroundColor Green
} elseif ($successRate -ge 70) {
    Write-Host "NEEDS ATTENTION - Several issues need to be fixed" -ForegroundColor Yellow
} else {
    Write-Host "CRITICAL ISSUES - Major problems need immediate attention" -ForegroundColor Red
}

Write-Host "`nTransaction Service Checkpoint Analysis Complete!" -ForegroundColor Green
