# Account Service Comprehensive Checkpoint Script
Write-Host "Account Service Comprehensive Checkpoint Analysis" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green

$baseUrl = "http://localhost:8080"
$actuatorUrl = "http://localhost:9001"
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
        Category = $Category
        Check = $Check
        Status = $Status
        Details = $Details
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
            Uri = $Url
            Method = $Method
            TimeoutSec = 10
            Headers = $Headers
        }
        
        if ($Body) {
            $params.Body = $Body
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
    }
    catch {
        Write-Host "FAIL $Name - Error: $($_.Exception.Message)" -ForegroundColor Red
        return @{ Success = $false; Error = $_.Exception.Message; StatusCode = $null }
    }
}

Write-Host "`nTesting Service Availability..." -ForegroundColor Yellow

# Test basic connectivity
$connectivityTests = @(
    @{ Name = "Application Port 8080"; Url = "$baseUrl/api/health/status" },
    @{ Name = "Actuator Port 9001"; Url = "$actuatorUrl/actuator/health" }
)

foreach ($test in $connectivityTests) {
    $result = Test-Endpoint $test.Name $test.Url
    if ($result.Success) {
        Add-CheckResult "Connectivity" $test.Name "PASS" "Service accessible" ""
    } else {
        Add-CheckResult "Connectivity" $test.Name "FAIL" "Service not accessible" "Check if service is running"
    }
}

Write-Host "`nTesting Authentication..." -ForegroundColor Yellow

# Test user registration and login
$registerBody = @{
    username = "checkpointuser$(Get-Random)"
    password = "testpass123"
} | ConvertTo-Json

$registerResult = Test-Endpoint "User Registration" "$baseUrl/api/auth/register" "POST" @{} $registerBody 201

if ($registerResult.Success) {
    Add-CheckResult "Authentication" "User Registration" "PASS" "Registration successful" ""
    
    $loginBody = @{
        username = ($registerBody | ConvertFrom-Json).username
        password = ($registerBody | ConvertFrom-Json).password
    } | ConvertTo-Json
    
    $loginResult = Test-Endpoint "User Login" "$baseUrl/api/auth/login" "POST" @{} $loginBody 200
    
    if ($loginResult.Success) {
        Add-CheckResult "Authentication" "User Login" "PASS" "Login successful" ""
        
        try {
            $loginData = $loginResult.Response.Content | ConvertFrom-Json
            if ($loginData.token) {
                Add-CheckResult "Authentication" "JWT Token" "PASS" "Token generated" ""
                $token = $loginData.token
            } else {
                Add-CheckResult "Authentication" "JWT Token" "FAIL" "No token in response" "Check JWT configuration"
                $token = $null
            }
        } catch {
            Add-CheckResult "Authentication" "JWT Token" "FAIL" "Cannot parse response" "Check response format"
            $token = $null
        }
    } else {
        Add-CheckResult "Authentication" "User Login" "FAIL" "Login failed" "Check authentication"
        $token = $null
    }
} else {
    Add-CheckResult "Authentication" "User Registration" "FAIL" "Registration failed" "Check registration endpoint"
    $token = $null
}

Write-Host "`nTesting Account Operations..." -ForegroundColor Yellow

if ($token) {
    $authHeaders = @{ "Authorization" = "Bearer $token" }
    
    # Test account operations
    $accountBody = @{
        accountNumber = "CHK$(Get-Random -Minimum 100000 -Maximum 999999)"
        accountType = "CHECKING"
        balance = 1000.00
        ownerId = "checkpointuser"
        currency = "USD"
    } | ConvertTo-Json
    
    $createResult = Test-Endpoint "Create Account" "$baseUrl/api/accounts" "POST" $authHeaders $accountBody 201
    
    if ($createResult.Success) {
        Add-CheckResult "Account Operations" "Account Creation" "PASS" "Account created" ""
        
        try {
            $accountData = $createResult.Response.Content | ConvertFrom-Json
            $accountId = $accountData.id
            
            # Test other operations
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
            
        } catch {
            Add-CheckResult "Account Operations" "Data Processing" "FAIL" "Cannot process account data" "Check response format"
        }
    } else {
        Add-CheckResult "Account Operations" "Account Creation" "FAIL" "Cannot create account" "Check creation logic"
    }
} else {
    Add-CheckResult "Account Operations" "All Operations" "SKIP" "No valid token" "Fix authentication first"
}

Write-Host "`nTesting Health Endpoints..." -ForegroundColor Yellow

$healthEndpoints = @(
    @{ Name = "Custom Health Status"; Url = "$baseUrl/api/health/status" },
    @{ Name = "Health Deployment Info"; Url = "$baseUrl/api/health/deployment" },
    @{ Name = "Health Metrics"; Url = "$baseUrl/api/health/metrics" },
    @{ Name = "Actuator Health"; Url = "$actuatorUrl/actuator/health" },
    @{ Name = "Actuator Info"; Url = "$actuatorUrl/actuator/info" },
    @{ Name = "Actuator Metrics"; Url = "$actuatorUrl/actuator/metrics" }
)

foreach ($endpoint in $healthEndpoints) {
    $result = Test-Endpoint $endpoint.Name $endpoint.Url
    if ($result.Success) {
        Add-CheckResult "Health Monitoring" $endpoint.Name "PASS" "Endpoint accessible" ""
    } else {
        Add-CheckResult "Health Monitoring" $endpoint.Name "FAIL" "Endpoint not accessible" "Check endpoint configuration"
    }
}

Write-Host "`nTesting Error Handling..." -ForegroundColor Yellow

$errorResult = Test-Endpoint "Error Endpoint" "$baseUrl/api/accounts/test/error" "GET" @{} $null 500
if ($errorResult.StatusCode -eq 500) {
    Add-CheckResult "Error Handling" "Error Endpoint" "PASS" "Returns 500 as expected" ""
} else {
    Add-CheckResult "Error Handling" "Error Endpoint" "FAIL" "Unexpected status" "Check error handling"
}

Write-Host "`nTesting Security..." -ForegroundColor Yellow

$protectedResult = Test-Endpoint "Protected Endpoint (No Auth)" "$baseUrl/api/accounts" "GET" @{} $null 401
if ($protectedResult.StatusCode -eq 401) {
    Add-CheckResult "Security" "Protected Endpoints" "PASS" "Properly secured" ""
} else {
    Add-CheckResult "Security" "Protected Endpoints" "FAIL" "Not properly secured" "Check security configuration"
}

# Generate summary
Write-Host "`nCHECKPOINT SUMMARY REPORT" -ForegroundColor Green
Write-Host "=========================" -ForegroundColor Green

$passCount = ($checkResults | Where-Object { $_.Status -eq "PASS" }).Count
$failCount = ($checkResults | Where-Object { $_.Status -eq "FAIL" }).Count
$skipCount = ($checkResults | Where-Object { $_.Status -eq "SKIP" }).Count
$totalCount = $checkResults.Count

Write-Host "`nOverall Results:" -ForegroundColor White
Write-Host "Total Checks: $totalCount" -ForegroundColor White
Write-Host "Passed: $passCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor Red
Write-Host "Skipped: $skipCount" -ForegroundColor Gray

$successRate = if (($totalCount - $skipCount) -gt 0) { 
    [math]::Round(($passCount / ($totalCount - $skipCount)) * 100, 2) 
} else { 
    0 
}

Write-Host "`nSuccess Rate: $successRate%" -ForegroundColor $(
    if ($successRate -ge 90) { "Green" } 
    elseif ($successRate -ge 75) { "Yellow" } 
    else { "Red" }
)

# Results by category
Write-Host "`nResults by Category:" -ForegroundColor White
$categories = $checkResults | Group-Object Category
foreach ($category in $categories) {
    $categoryPass = ($category.Group | Where-Object { $_.Status -eq "PASS" }).Count
    $categoryTotal = ($category.Group | Where-Object { $_.Status -ne "SKIP" }).Count
    $categoryRate = if ($categoryTotal -gt 0) { [math]::Round(($categoryPass / $categoryTotal) * 100, 1) } else { 0 }
    
    Write-Host "  $($category.Name): $categoryPass/$categoryTotal ($categoryRate%)" -ForegroundColor $(
        if ($categoryRate -eq 100) { "Green" } 
        elseif ($categoryRate -ge 75) { "Yellow" } 
        else { "Red" }
    )
}

# Failed checks
if ($failCount -gt 0) {
    Write-Host "`nFailed Checks:" -ForegroundColor Red
    $failedChecks = $checkResults | Where-Object { $_.Status -eq "FAIL" }
    foreach ($check in $failedChecks) {
        Write-Host "  - $($check.Category): $($check.Check)" -ForegroundColor Red
        if ($check.Details) {
            Write-Host "    Details: $($check.Details)" -ForegroundColor Gray
        }
        if ($check.Recommendation) {
            Write-Host "    Fix: $($check.Recommendation)" -ForegroundColor Yellow
        }
    }
}

# Export results
$checkResults | Export-Csv -Path "account-service-checkpoint-results.csv" -NoTypeInformation
Write-Host "`nDetailed results exported to: account-service-checkpoint-results.csv" -ForegroundColor Cyan

# Final assessment
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