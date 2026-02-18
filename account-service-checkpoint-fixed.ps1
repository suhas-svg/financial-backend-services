# Account Service Comprehensive Checkpoint Script (Fixed)
Write-Host "Account Service Comprehensive Checkpoint Analysis" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green

# FIXED: Actuator is on same port 8080 (not 9001)
$baseUrl    = "http://localhost:8080"
$actuatorUrl = "http://localhost:8080"
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
            TimeoutSec      = 10
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
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        Write-Host "FAIL $Name - Status: $statusCode / Error: $($_.Exception.Message)" -ForegroundColor Red
        return @{ Success = $false; Error = $_.Exception.Message; StatusCode = $statusCode }
    }
}

# ─── 1. CONNECTIVITY ────────────────────────────────────────────────────────
Write-Host "`nTesting Service Availability..." -ForegroundColor Yellow

$connectivityTests = @(
    @{ Name = "Application Port 8080 (Health)"; Url = "$baseUrl/api/health/status" },
    @{ Name = "Actuator Health (port 8080)";    Url = "$actuatorUrl/actuator/health" }
)
foreach ($test in $connectivityTests) {
    $result = Test-Endpoint $test.Name $test.Url
    if ($result.Success) {
        Add-CheckResult "Connectivity" $test.Name "PASS" "Service accessible" ""
    } else {
        Add-CheckResult "Connectivity" $test.Name "FAIL" "Service not accessible" "Check if service is running"
    }
}

# ─── 2. AUTHENTICATION ──────────────────────────────────────────────────────
Write-Host "`nTesting Authentication..." -ForegroundColor Yellow

$username = "checkpointuser$(Get-Random)"
$registerBody = @{ username = $username; password = "testpass123" } | ConvertTo-Json
$registerResult = Test-Endpoint "User Registration" "$baseUrl/api/auth/register" "POST" @{} $registerBody 201

$token = $null
if ($registerResult.Success) {
    Add-CheckResult "Authentication" "User Registration" "PASS" "Registration successful" ""

    $loginBody   = @{ username = $username; password = "testpass123" } | ConvertTo-Json
    $loginResult = Test-Endpoint "User Login" "$baseUrl/api/auth/login" "POST" @{} $loginBody 200

    if ($loginResult.Success) {
        Add-CheckResult "Authentication" "User Login" "PASS" "Login successful" ""
        try {
            $loginData = $loginResult.Response.Content | ConvertFrom-Json
            # FIXED: token field is 'accessToken', not 'token'
            $token = $loginData.accessToken
            if ($token) {
                Add-CheckResult "Authentication" "JWT Token" "PASS" "Token generated (accessToken field)" ""
            } else {
                Add-CheckResult "Authentication" "JWT Token" "FAIL" "No accessToken in response. Fields: $($loginData | Get-Member -MemberType NoteProperty | Select -Expand Name)" "Check JWT configuration"
            }
        } catch {
            Add-CheckResult "Authentication" "JWT Token" "FAIL" "Cannot parse response: $_" "Check response format"
        }
    } else {
        Add-CheckResult "Authentication" "User Login" "FAIL" "Login failed" "Check authentication"
    }
} else {
    Add-CheckResult "Authentication" "User Registration" "FAIL" "Registration failed" "Check registration endpoint"
}

# ─── 3. ACCOUNT OPERATIONS ──────────────────────────────────────────────────
Write-Host "`nTesting Account Operations..." -ForegroundColor Yellow

if ($token) {
    $authHeaders = @{ "Authorization" = "Bearer $token" }

    $accountBody = @{
        accountNumber = "CHK$(Get-Random -Minimum 100000 -Maximum 999999)"
        accountType   = "CHECKING"
        balance       = 1000.00
        ownerId       = $username
        currency      = "USD"
    } | ConvertTo-Json

    $createResult = Test-Endpoint "Create Account" "$baseUrl/api/accounts" "POST" $authHeaders $accountBody 201
    if ($createResult.Success) {
        Add-CheckResult "Account Operations" "Account Creation" "PASS" "Account created" ""
        try {
            $accountData = $createResult.Response.Content | ConvertFrom-Json
            $accountId   = $accountData.id

            $getResult = Test-Endpoint "Get Account" "$baseUrl/api/accounts/$accountId" "GET" $authHeaders
            if ($getResult.Success) {
                Add-CheckResult "Account Operations" "Account Retrieval" "PASS" "Account retrieved" ""
            } else {
                Add-CheckResult "Account Operations" "Account Retrieval" "FAIL" "Cannot retrieve account" "Check retrieval logic"
            }

            $listResult = Test-Endpoint "List Accounts" "$baseUrl/api/accounts" "GET" $authHeaders
            if ($listResult.Success) {
                Add-CheckResult "Account Operations" "Account Listing" "PASS" "Accounts listed" ""
            } else {
                Add-CheckResult "Account Operations" "Account Listing" "FAIL" "Cannot list accounts" "Check listing logic"
            }

            $updateBody = @{
                accountNumber = $accountData.accountNumber
                accountType   = "CHECKING"
                balance       = 1500.00
                ownerId       = $accountData.ownerId
                currency      = "USD"
            } | ConvertTo-Json
            $updateResult = Test-Endpoint "Update Account" "$baseUrl/api/accounts/$accountId" "PUT" $authHeaders $updateBody
            if ($updateResult.Success) {
                Add-CheckResult "Account Operations" "Account Update" "PASS" "Account updated" ""
            } else {
                Add-CheckResult "Account Operations" "Account Update" "FAIL" "Cannot update account" "Check update logic"
            }

            $deleteResult = Test-Endpoint "Delete Account" "$baseUrl/api/accounts/$accountId" "DELETE" $authHeaders $null 204
            if ($deleteResult.Success -or $deleteResult.StatusCode -eq 204) {
                Add-CheckResult "Account Operations" "Account Deletion" "PASS" "Account deleted" ""
            } else {
                Add-CheckResult "Account Operations" "Account Deletion" "FAIL" "Cannot delete account" "Check deletion logic"
            }
        } catch {
            Add-CheckResult "Account Operations" "Data Processing" "FAIL" "Cannot process account data: $_" "Check response format"
        }
    } else {
        Add-CheckResult "Account Operations" "Account Creation" "FAIL" "Cannot create account" "Check creation logic"
    }
} else {
    Add-CheckResult "Account Operations" "All Operations" "SKIP" "No valid token" "Fix authentication first"
}

# ─── 4. HEALTH & MONITORING ─────────────────────────────────────────────────
Write-Host "`nTesting Health Endpoints..." -ForegroundColor Yellow

$healthEndpoints = @(
    @{ Name = "Custom Health Status";     Url = "$baseUrl/api/health/status" },
    @{ Name = "Health Deployment Info";   Url = "$baseUrl/api/health/deployment" },
    @{ Name = "Health Metrics";           Url = "$baseUrl/api/health/metrics" },
    @{ Name = "Actuator Health";          Url = "$actuatorUrl/actuator/health" },
    @{ Name = "Actuator Info";            Url = "$actuatorUrl/actuator/info" }
)
foreach ($endpoint in $healthEndpoints) {
    $result = Test-Endpoint $endpoint.Name $endpoint.Url
    if ($result.Success) {
        Add-CheckResult "Health Monitoring" $endpoint.Name "PASS" "Endpoint accessible" ""
    } else {
        Add-CheckResult "Health Monitoring" $endpoint.Name "FAIL" "Endpoint not accessible (Status: $($result.StatusCode))" "Check endpoint configuration"
    }
}

# Actuator metrics requires ADMIN/INTERNAL_SERVICE role — test with token
if ($token) {
    $authHeaders = @{ "Authorization" = "Bearer $token" }
    $metricsResult = Test-Endpoint "Actuator Metrics (auth)" "$actuatorUrl/actuator/metrics" "GET" $authHeaders
    if ($metricsResult.Success) {
        Add-CheckResult "Health Monitoring" "Actuator Metrics" "PASS" "Metrics endpoint accessible" ""
    } else {
        Add-CheckResult "Health Monitoring" "Actuator Metrics" "WARN" "Status $($metricsResult.StatusCode) - requires ADMIN/INTERNAL_SERVICE role" "Expected - metrics secured by role"
    }
}

# ─── 5. SECURITY ────────────────────────────────────────────────────────────
Write-Host "`nTesting Security..." -ForegroundColor Yellow

# FIXED: Spring Security without custom entry point returns 403 (not 401) for unauthenticated
$protectedResult = Test-Endpoint "Protected Endpoint (No Auth)" "$baseUrl/api/accounts" "GET" @{} $null 403
if ($protectedResult.StatusCode -eq 403) {
    Add-CheckResult "Security" "Protected Endpoints (403)" "PASS" "Properly secured - returns 403 without auth" ""
} elseif ($protectedResult.StatusCode -eq 401) {
    Add-CheckResult "Security" "Protected Endpoints (401)" "PASS" "Properly secured - returns 401 without auth" ""
} else {
    Add-CheckResult "Security" "Protected Endpoints" "FAIL" "Unexpected status: $($protectedResult.StatusCode)" "Check security configuration"
}

# ─── 6. ERROR HANDLING ──────────────────────────────────────────────────────
Write-Host "`nTesting Error Handling..." -ForegroundColor Yellow

# Unknown paths return 403 when unauthenticated (security filter intercepts before routing).
# Use auth token if available to get a true 404; otherwise accept 403 as expected security behaviour.
if ($token) {
    $authHeaders404 = @{ "Authorization" = "Bearer $token" }
    $invalidResult  = Test-Endpoint "Invalid Endpoint (404 with auth)" "$baseUrl/api/nonexistent-endpoint-xyz" "GET" $authHeaders404 $null 404
    if ($invalidResult.StatusCode -eq 404) {
        Add-CheckResult "Error Handling" "404 Not Found" "PASS" "Returns 404 for invalid authenticated endpoints" ""
    } else {
        Add-CheckResult "Error Handling" "404 Not Found" "WARN" "Status: $($invalidResult.StatusCode) - may be handled differently" "Check routing configuration"
    }
} else {
    # Without auth, security returns 403 before routing — both 403 and 404 are acceptable
    $invalidResult = Test-Endpoint "Invalid Endpoint (no auth)" "$baseUrl/api/nonexistent-endpoint-xyz" "GET" @{} $null 403
    if ($invalidResult.StatusCode -in @(403, 404)) {
        Add-CheckResult "Error Handling" "404/403 Not Found" "PASS" "Returns $($invalidResult.StatusCode) for invalid endpoints (security intercepts before routing)" ""
    } else {
        Add-CheckResult "Error Handling" "404 Not Found" "FAIL" "Unexpected status: $($invalidResult.StatusCode)" "Check routing configuration"
    }
}

# Test error endpoint with auth if token available
if ($token) {
    $authHeaders = @{ "Authorization" = "Bearer $token" }
    $errorResult = Test-Endpoint "Error Endpoint (with auth)" "$baseUrl/api/accounts/test/error" "GET" $authHeaders $null 500
    if ($errorResult.StatusCode -eq 500) {
        Add-CheckResult "Error Handling" "Error Endpoint" "PASS" "Returns 500 as expected" ""
    } else {
        Add-CheckResult "Error Handling" "Error Endpoint" "WARN" "Status: $($errorResult.StatusCode) - endpoint may not exist" "Check if /api/accounts/test/error is implemented"
    }
}

# ─── 7. DATA VALIDATION ─────────────────────────────────────────────────────
Write-Host "`nTesting Data Validation..." -ForegroundColor Yellow

$invalidRegisterBody = @{ username = ""; password = "123" } | ConvertTo-Json
$invalidRegResult = Test-Endpoint "Invalid Registration Data" "$baseUrl/api/auth/register" "POST" @{} $invalidRegisterBody 400
if ($invalidRegResult.StatusCode -eq 400) {
    Add-CheckResult "Data Validation" "Registration Validation" "PASS" "Properly validates registration data" ""
} else {
    Add-CheckResult "Data Validation" "Registration Validation" "FAIL" "Status: $($invalidRegResult.StatusCode)" "Check validation annotations"
}

# ─── 8. PERFORMANCE ─────────────────────────────────────────────────────────
Write-Host "`nTesting Performance..." -ForegroundColor Yellow

$startTime    = Get-Date
$perfResult   = Test-Endpoint "Performance Test" "$baseUrl/api/health/status"
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

# ─── SUMMARY ────────────────────────────────────────────────────────────────
Write-Host "`nCHECKPOINT SUMMARY REPORT" -ForegroundColor Green
Write-Host "=========================" -ForegroundColor Green

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

$denominator  = $totalCount - $skipCount
$successRate  = if ($denominator -gt 0) { [math]::Round(($passCount / $denominator) * 100, 2) } else { 0 }
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
        if ($_.Details)         { Write-Host "    Details: $($_.Details)" -ForegroundColor Gray }
        if ($_.Recommendation)  { Write-Host "    Fix: $($_.Recommendation)" -ForegroundColor Yellow }
    }
}

if ($warnCount -gt 0) {
    Write-Host "`nWarnings:" -ForegroundColor Yellow
    $checkResults | Where-Object { $_.Status -eq "WARN" } | ForEach-Object {
        Write-Host "  - $($_.Category): $($_.Check)" -ForegroundColor Yellow
        if ($_.Details)         { Write-Host "    Details: $($_.Details)" -ForegroundColor Gray }
        if ($_.Recommendation)  { Write-Host "    Fix: $($_.Recommendation)" -ForegroundColor Cyan }
    }
}

$checkResults | Export-Csv -Path "account-service-checkpoint-results.csv" -NoTypeInformation
Write-Host "`nDetailed results exported to: account-service-checkpoint-results.csv" -ForegroundColor Cyan

Write-Host "`nFinal Assessment:" -ForegroundColor Green
if ($successRate -ge 95) {
    Write-Host "EXCELLENT - Account Service is production ready!" -ForegroundColor Green
} elseif ($successRate -ge 85) {
    Write-Host "GOOD - Account Service is mostly ready, minor issues to address" -ForegroundColor Green
} elseif ($successRate -ge 70) {
    Write-Host "NEEDS ATTENTION - Several issues need to be fixed" -ForegroundColor Yellow
} else {
    Write-Host "CRITICAL ISSUES - Major problems need immediate attention" -ForegroundColor Red
}

Write-Host "`nCheckpoint Analysis Complete!" -ForegroundColor Green