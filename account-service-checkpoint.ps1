# Account Service Comprehensive Checkpoint Script
Write-Host "🔍 Account Service Comprehensive Checkpoint Analysis" -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Green

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
            Write-Host "✅ $Name - Status: $($response.StatusCode)" -ForegroundColor Green
            return @{ Success = $true; Response = $response; StatusCode = $response.StatusCode }
        } else {
            Write-Host "⚠️ $Name - Unexpected Status: $($response.StatusCode) (Expected: $ExpectedStatus)" -ForegroundColor Yellow
            return @{ Success = $false; Response = $response; StatusCode = $response.StatusCode }
        }
    }
    catch {
        Write-Host "❌ $Name - Error: $($_.Exception.Message)" -ForegroundColor Red
        return @{ Success = $false; Error = $_.Exception.Message; StatusCode = $null }
    }
}

function Test-DatabaseConnection {
    try {
        Write-Host "`n🗄️ Testing Database Connection..." -ForegroundColor Cyan
        
        # Test via actuator health endpoint
        $healthResponse = Test-Endpoint "Database Health Check" "$actuatorUrl/actuator/health"
        
        if ($healthResponse.Success) {
            $healthData = $healthResponse.Response.Content | ConvertFrom-Json
            
            if ($healthData.components.db.status -eq "UP") {
                Add-CheckResult "Database" "Connection" "PASS" "Database connection is healthy" ""
                return $true
            } else {
                Add-CheckResult "Database" "Connection" "FAIL" "Database status: $($healthData.components.db.status)" "Check database configuration and connectivity"
                return $false
            }
        } else {
            Add-CheckResult "Database" "Connection" "FAIL" "Cannot reach health endpoint" "Ensure application is running"
            return $false
        }
    }
    catch {
        Add-CheckResult "Database" "Connection" "FAIL" $_.Exception.Message "Check database service and configuration"
        return $false
    }
}

function Test-SecurityConfiguration {
    Write-Host "`n🔐 Testing Security Configuration..." -ForegroundColor Cyan
    
    # Test public endpoints (should work without auth)
    $publicEndpoints = @(
        @{ Name = "Health Check"; Url = "$baseUrl/api/health/status"; Expected = 200 },
        @{ Name = "Actuator Health"; Url = "$actuatorUrl/actuator/health"; Expected = 200 }
    )
    
    foreach ($endpoint in $publicEndpoints) {
        $result = Test-Endpoint $endpoint.Name $endpoint.Url "GET" @{} $null $endpoint.Expected
        if ($result.Success) {
            Add-CheckResult "Security" "Public Endpoint: $($endpoint.Name)" "PASS" "Accessible without authentication" ""
        } else {
            Add-CheckResult "Security" "Public Endpoint: $($endpoint.Name)" "FAIL" "Status: $($result.StatusCode)" "Check security configuration"
        }
    }
    
    # Test protected endpoints (should require auth)
    $protectedResult = Test-Endpoint "Protected Endpoint (No Auth)" "$baseUrl/api/accounts" "GET" @{} $null 401
    if ($protectedResult.StatusCode -eq 401) {
        Add-CheckResult "Security" "Protected Endpoints" "PASS" "Properly secured - returns 401 without auth" ""
    } else {
        Add-CheckResult "Security" "Protected Endpoints" "FAIL" "Status: $($protectedResult.StatusCode)" "Security may not be properly configured"
    }
}

function Test-AuthenticationFlow {
    Write-Host "`n🔑 Testing Authentication Flow..." -ForegroundColor Cyan
    
    # Test user registration
    $registerBody = @{
        username = "checkpointuser$(Get-Random)"
        password = "testpass123"
    } | ConvertTo-Json
    
    $registerResult = Test-Endpoint "User Registration" "$baseUrl/api/auth/register" "POST" @{} $registerBody 201
    
    if ($registerResult.Success) {
        Add-CheckResult "Authentication" "User Registration" "PASS" "User registration successful" ""
        
        # Test user login
        $loginBody = @{
            username = ($registerBody | ConvertFrom-Json).username
            password = ($registerBody | ConvertFrom-Json).password
        } | ConvertTo-Json
        
        $loginResult = Test-Endpoint "User Login" "$baseUrl/api/auth/login" "POST" @{} $loginBody 200
        
        if ($loginResult.Success) {
            Add-CheckResult "Authentication" "User Login" "PASS" "User login successful" ""
            
            try {
                $loginData = $loginResult.Response.Content | ConvertFrom-Json
                if ($loginData.token) {
                    Add-CheckResult "Authentication" "JWT Token Generation" "PASS" "JWT token generated successfully" ""
                    return $loginData.token
                } else {
                    Add-CheckResult "Authentication" "JWT Token Generation" "FAIL" "No token in response" "Check JWT configuration"
                }
            } catch {
                Add-CheckResult "Authentication" "JWT Token Generation" "FAIL" "Cannot parse login response" "Check response format"
            }
        } else {
            Add-CheckResult "Authentication" "User Login" "FAIL" "Login failed" "Check authentication configuration"
        }
    } else {
        Add-CheckResult "Authentication" "User Registration" "FAIL" "Registration failed" "Check user registration endpoint"
    }
    
    return $null
}

function Test-AccountOperations {
    param([string]$Token)
    
    Write-Host "`n💰 Testing Account Operations..." -ForegroundColor Cyan
    
    if (-not $Token) {
        Add-CheckResult "Account Operations" "Authentication Required" "SKIP" "No valid token available" "Fix authentication first"
        return
    }
    
    $authHeaders = @{ "Authorization" = "Bearer $Token" }
    
    # Test account creation
    $accountBody = @{
        accountNumber = "CHK$(Get-Random -Minimum 100000 -Maximum 999999)"
        accountType = "CHECKING"
        balance = 1000.00
        ownerId = "checkpointuser"
        currency = "USD"
    } | ConvertTo-Json
    
    $createResult = Test-Endpoint "Create Account" "$baseUrl/api/accounts" "POST" $authHeaders $accountBody 201
    
    if ($createResult.Success) {
        Add-CheckResult "Account Operations" "Account Creation" "PASS" "Account created successfully" ""
        
        try {
            $accountData = $createResult.Response.Content | ConvertFrom-Json
            $accountId = $accountData.id
            
            # Test account retrieval
            $getResult = Test-Endpoint "Get Account" "$baseUrl/api/accounts/$accountId" "GET" $authHeaders
            if ($getResult.Success) {
                Add-CheckResult "Account Operations" "Account Retrieval" "PASS" "Account retrieved successfully" ""
            } else {
                Add-CheckResult "Account Operations" "Account Retrieval" "FAIL" "Cannot retrieve created account" "Check account retrieval logic"
            }
            
            # Test account listing
            $listResult = Test-Endpoint "List Accounts" "$baseUrl/api/accounts" "GET" $authHeaders
            if ($listResult.Success) {
                Add-CheckResult "Account Operations" "Account Listing" "PASS" "Account listing successful" ""
            } else {
                Add-CheckResult "Account Operations" "Account Listing" "FAIL" "Cannot list accounts" "Check account listing logic"
            }
            
            # Test account update
            $updateBody = @{
                accountNumber = $accountData.accountNumber
                accountType = "CHECKING"
                balance = 1500.00
                ownerId = $accountData.ownerId
                currency = "USD"
            } | ConvertTo-Json
            
            $updateResult = Test-Endpoint "Update Account" "$baseUrl/api/accounts/$accountId" "PUT" $authHeaders $updateBody
            if ($updateResult.Success) {
                Add-CheckResult "Account Operations" "Account Update" "PASS" "Account updated successfully" ""
            } else {
                Add-CheckResult "Account Operations" "Account Update" "FAIL" "Cannot update account" "Check account update logic"
            }
            
            # Test account deletion
            $deleteResult = Test-Endpoint "Delete Account" "$baseUrl/api/accounts/$accountId" "DELETE" $authHeaders $null 204
            if ($deleteResult.Success -or $deleteResult.StatusCode -eq 204) {
                Add-CheckResult "Account Operations" "Account Deletion" "PASS" "Account deleted successfully" ""
            } else {
                Add-CheckResult "Account Operations" "Account Deletion" "FAIL" "Cannot delete account" "Check account deletion logic"
            }
            
        } catch {
            Add-CheckResult "Account Operations" "Account Data Processing" "FAIL" "Cannot process account data" "Check response format and data structure"
        }
    } else {
        Add-CheckResult "Account Operations" "Account Creation" "FAIL" "Cannot create account" "Check account creation logic and validation"
    }
}

function Test-HealthAndMonitoring {
    Write-Host "`n📊 Testing Health and Monitoring..." -ForegroundColor Cyan
    
    # Test custom health endpoints
    $healthEndpoints = @(
        @{ Name = "Custom Health Status"; Url = "$baseUrl/api/health/status" },
        @{ Name = "Health Deployment Info"; Url = "$baseUrl/api/health/deployment" },
        @{ Name = "Health Metrics"; Url = "$baseUrl/api/health/metrics" }
    )
    
    foreach ($endpoint in $healthEndpoints) {
        $result = Test-Endpoint $endpoint.Name $endpoint.Url
        if ($result.Success) {
            Add-CheckResult "Health & Monitoring" $endpoint.Name "PASS" "Endpoint accessible and responding" ""
        } else {
            Add-CheckResult "Health & Monitoring" $endpoint.Name "FAIL" "Endpoint not accessible" "Check health controller implementation"
        }
    }
    
    # Test actuator endpoints
    $actuatorEndpoints = @(
        @{ Name = "Actuator Health"; Url = "$actuatorUrl/actuator/health" },
        @{ Name = "Actuator Info"; Url = "$actuatorUrl/actuator/info" },
        @{ Name = "Actuator Metrics"; Url = "$actuatorUrl/actuator/metrics" }
    )
    
    foreach ($endpoint in $actuatorEndpoints) {
        $result = Test-Endpoint $endpoint.Name $endpoint.Url
        if ($result.Success) {
            Add-CheckResult "Health & Monitoring" $endpoint.Name "PASS" "Actuator endpoint accessible" ""
        } else {
            Add-CheckResult "Health & Monitoring" $endpoint.Name "FAIL" "Actuator endpoint not accessible" "Check actuator configuration"
        }
    }
}

function Test-ErrorHandling {
    Write-Host "`n🚨 Testing Error Handling..." -ForegroundColor Cyan
    
    # Test error endpoint
    $errorResult = Test-Endpoint "Error Endpoint" "$baseUrl/api/accounts/test/error" "GET" @{} $null 500
    if ($errorResult.StatusCode -eq 500) {
        Add-CheckResult "Error Handling" "Error Endpoint" "PASS" "Error endpoint returns 500 as expected" ""
    } else {
        Add-CheckResult "Error Handling" "Error Endpoint" "FAIL" "Unexpected status: $($errorResult.StatusCode)" "Check error handling implementation"
    }
    
    # Test invalid endpoint
    $invalidResult = Test-Endpoint "Invalid Endpoint" "$baseUrl/api/invalid" "GET" @{} $null 404
    if ($invalidResult.StatusCode -eq 404) {
        Add-CheckResult "Error Handling" "Invalid Endpoint" "PASS" "Returns 404 for invalid endpoints" ""
    } else {
        Add-CheckResult "Error Handling" "Invalid Endpoint" "FAIL" "Unexpected status: $($invalidResult.StatusCode)" "Check routing configuration"
    }
}

function Test-DataValidation {
    Write-Host "`n✅ Testing Data Validation..." -ForegroundColor Cyan
    
    # Test invalid registration data
    $invalidRegisterBody = @{
        username = ""
        password = "123"
    } | ConvertTo-Json
    
    $invalidRegisterResult = Test-Endpoint "Invalid Registration Data" "$baseUrl/api/auth/register" "POST" @{} $invalidRegisterBody 400
    if ($invalidRegisterResult.StatusCode -eq 400) {
        Add-CheckResult "Data Validation" "Registration Validation" "PASS" "Properly validates registration data" ""
    } else {
        Add-CheckResult "Data Validation" "Registration Validation" "FAIL" "Does not validate registration data properly" "Check validation annotations"
    }
}

function Test-Performance {
    Write-Host "`n⚡ Testing Performance..." -ForegroundColor Cyan
    
    $startTime = Get-Date
    $healthResult = Test-Endpoint "Performance Test" "$baseUrl/api/health/status"
    $endTime = Get-Date
    $responseTime = ($endTime - $startTime).TotalMilliseconds
    
    if ($healthResult.Success) {
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
}

function Test-Configuration {
    Write-Host "`n⚙️ Testing Configuration..." -ForegroundColor Cyan
    
    # Test if service is running on correct ports
    try {
        $appPortTest = Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue
        if ($appPortTest.TcpTestSucceeded) {
            Add-CheckResult "Configuration" "Application Port (8080)" "PASS" "Service accessible on port 8080" ""
        } else {
            Add-CheckResult "Configuration" "Application Port (8080)" "FAIL" "Service not accessible on port 8080" "Check if service is running"
        }
    } catch {
        Add-CheckResult "Configuration" "Application Port (8080)" "FAIL" "Cannot test port connectivity" "Check network configuration"
    }
    
    try {
        $actuatorPortTest = Test-NetConnection -ComputerName localhost -Port 9001 -WarningAction SilentlyContinue
        if ($actuatorPortTest.TcpTestSucceeded) {
            Add-CheckResult "Configuration" "Actuator Port (9001)" "PASS" "Actuator accessible on port 9001" ""
        } else {
            Add-CheckResult "Configuration" "Actuator Port (9001)" "FAIL" "Actuator not accessible on port 9001" "Check actuator configuration"
        }
    } catch {
        Add-CheckResult "Configuration" "Actuator Port (9001)" "FAIL" "Cannot test actuator port" "Check actuator configuration"
    }
}

# Main execution
Write-Host "`n🚀 Starting Account Service Checkpoint Analysis..." -ForegroundColor Yellow

# Run all tests
Test-Configuration
$dbConnected = Test-DatabaseConnection
Test-SecurityConfiguration
$token = Test-AuthenticationFlow
Test-AccountOperations $token
Test-HealthAndMonitoring
Test-ErrorHandling
Test-DataValidation
Test-Performance

# Generate summary report
Write-Host "`n📋 CHECKPOINT SUMMARY REPORT" -ForegroundColor Green
Write-Host "============================" -ForegroundColor Green

$passCount = ($checkResults | Where-Object { $_.Status -eq "PASS" }).Count
$failCount = ($checkResults | Where-Object { $_.Status -eq "FAIL" }).Count
$warnCount = ($checkResults | Where-Object { $_.Status -eq "WARN" }).Count
$skipCount = ($checkResults | Where-Object { $_.Status -eq "SKIP" }).Count
$totalCount = $checkResults.Count

Write-Host "`nOverall Results:" -ForegroundColor White
Write-Host "Total Checks: $totalCount" -ForegroundColor White
Write-Host "✅ Passed: $passCount" -ForegroundColor Green
Write-Host "❌ Failed: $failCount" -ForegroundColor Red
Write-Host "⚠️ Warnings: $warnCount" -ForegroundColor Yellow
Write-Host "⏭️ Skipped: $skipCount" -ForegroundColor Gray

$successRate = [math]::Round(($passCount / ($totalCount - $skipCount)) * 100, 2)
Write-Host "`n🎯 Success Rate: $successRate%" -ForegroundColor $(if ($successRate -ge 90) { "Green" } elseif ($successRate -ge 75) { "Yellow" } else { "Red" })

# Detailed results by category
Write-Host "`n📊 Results by Category:" -ForegroundColor White
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

# Failed checks details
if ($failCount -gt 0) {
    Write-Host "`n❌ Failed Checks Details:" -ForegroundColor Red
    $failedChecks = $checkResults | Where-Object { $_.Status -eq "FAIL" }
    foreach ($check in $failedChecks) {
        Write-Host "  • $($check.Category) - $($check.Check)" -ForegroundColor Red
        if ($check.Details) {
            Write-Host "    Details: $($check.Details)" -ForegroundColor Gray
        }
        if ($check.Recommendation) {
            Write-Host "    Recommendation: $($check.Recommendation)" -ForegroundColor Yellow
        }
    }
}

# Warnings details
if ($warnCount -gt 0) {
    Write-Host "`n⚠️ Warnings:" -ForegroundColor Yellow
    $warningChecks = $checkResults | Where-Object { $_.Status -eq "WARN" }
    foreach ($check in $warningChecks) {
        Write-Host "  • $($check.Category) - $($check.Check)" -ForegroundColor Yellow
        if ($check.Details) {
            Write-Host "    Details: $($check.Details)" -ForegroundColor Gray
        }
        if ($check.Recommendation) {
            Write-Host "    Recommendation: $($check.Recommendation)" -ForegroundColor Cyan
        }
    }
}

# Export detailed results
$checkResults | Export-Csv -Path "account-service-checkpoint-results.csv" -NoTypeInformation
Write-Host "`n📄 Detailed results exported to: account-service-checkpoint-results.csv" -ForegroundColor Cyan

# Final recommendations
Write-Host "`n🎯 Next Steps:" -ForegroundColor Green
if ($successRate -ge 95) {
    Write-Host "✅ Account Service is in excellent condition!" -ForegroundColor Green
    Write-Host "   Ready for production deployment." -ForegroundColor Green
} elseif ($successRate -ge 85) {
    Write-Host "✅ Account Service is in good condition." -ForegroundColor Green
    Write-Host "   Address warnings for optimal performance." -ForegroundColor Yellow
} elseif ($successRate -ge 70) {
    Write-Host "⚠️ Account Service needs attention." -ForegroundColor Yellow
    Write-Host "   Fix failed checks before production deployment." -ForegroundColor Yellow
} else {
    Write-Host "❌ Account Service has significant issues." -ForegroundColor Red
    Write-Host "   Critical fixes required before deployment." -ForegroundColor Red
}

Write-Host "`n🏁 Checkpoint Analysis Complete!" -ForegroundColor Green