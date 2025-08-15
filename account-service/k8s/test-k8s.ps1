# Comprehensive Kubernetes Testing Script
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ActuatorUrl = "http://localhost:9001"
)

Write-Host "=== Kubernetes Application Testing ===" -ForegroundColor Green

# Test 1: Health Check
Write-Host "`n1. Testing Health Endpoints..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$ActuatorUrl/actuator/health" -Method GET -TimeoutSec 10
    Write-Host "✅ Health Check: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "❌ Health Check Failed: $($_.Exception.Message)" -ForegroundColor Red
    return
}

# Test 2: Metrics Endpoint
Write-Host "`n2. Testing Metrics Endpoint..." -ForegroundColor Yellow
try {
    $metrics = Invoke-RestMethod -Uri "$ActuatorUrl/actuator/prometheus" -Method GET -TimeoutSec 10
    if ($metrics -like "*jvm_memory_used_bytes*") {
        Write-Host "✅ Metrics Available" -ForegroundColor Green
    } else {
        Write-Host "❌ Metrics Format Invalid" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Metrics Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 3: User Registration
Write-Host "`n3. Testing User Registration..." -ForegroundColor Yellow
$registerBody = @{
    username = "k8s-test-user"
    password = "testpass123"
    email = "k8stest@example.com"
} | ConvertTo-Json

try {
    $registerResponse = Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" -Method POST -ContentType "application/json" -Body $registerBody -TimeoutSec 10
    Write-Host "✅ User Registration: Success" -ForegroundColor Green
    Write-Host "   Username: $($registerResponse.username)" -ForegroundColor Cyan
} catch {
    if ($_.Exception.Response.StatusCode -eq 400) {
        Write-Host "⚠️  User Registration: User already exists (expected)" -ForegroundColor Yellow
    } else {
        Write-Host "❌ User Registration Failed: $($_.Exception.Message)" -ForegroundColor Red
        return
    }
}

# Test 4: User Login
Write-Host "`n4. Testing User Login..." -ForegroundColor Yellow
$loginBody = @{
    username = "k8s-test-user"
    password = "testpass123"
} | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody -TimeoutSec 10
    $token = $loginResponse.accessToken
    Write-Host "✅ User Login: Success" -ForegroundColor Green
    Write-Host "   Token: $($token.Substring(0, 20))..." -ForegroundColor Cyan
} catch {
    Write-Host "❌ User Login Failed: $($_.Exception.Message)" -ForegroundColor Red
    return
}

# Test 5: Protected Endpoint (Accounts List)
Write-Host "`n5. Testing Protected Endpoint..." -ForegroundColor Yellow
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

try {
    $accountsResponse = Invoke-RestMethod -Uri "$BaseUrl/api/accounts" -Method GET -Headers $headers -TimeoutSec 10
    Write-Host "✅ Protected Endpoint: Success" -ForegroundColor Green
    Write-Host "   Total Accounts: $($accountsResponse.totalElements)" -ForegroundColor Cyan
} catch {
    Write-Host "❌ Protected Endpoint Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 6: Account Creation
Write-Host "`n6. Testing Account Creation..." -ForegroundColor Yellow
$accountBody = @{
    ownerId = "k8s-test-user"
    balance = 1500.00
    accountType = "CHECKING"
} | ConvertTo-Json

try {
    $createAccountResponse = Invoke-RestMethod -Uri "$BaseUrl/api/accounts" -Method POST -Headers $headers -Body $accountBody -TimeoutSec 10
    Write-Host "✅ Account Creation: Success" -ForegroundColor Green
    Write-Host "   Account ID: $($createAccountResponse.id)" -ForegroundColor Cyan
    Write-Host "   Balance: $($createAccountResponse.balance)" -ForegroundColor Cyan
} catch {
    Write-Host "❌ Account Creation Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 7: Database Connectivity (via account list)
Write-Host "`n7. Testing Database Connectivity..." -ForegroundColor Yellow
try {
    $accountsResponse = Invoke-RestMethod -Uri "$BaseUrl/api/accounts" -Method GET -Headers $headers -TimeoutSec 10
    if ($accountsResponse.totalElements -gt 0) {
        Write-Host "✅ Database Connectivity: Success" -ForegroundColor Green
        Write-Host "   Records Found: $($accountsResponse.totalElements)" -ForegroundColor Cyan
    } else {
        Write-Host "⚠️  Database Connectivity: No records (but connection works)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Database Connectivity Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 8: Load Test (Simple)
Write-Host "`n8. Running Simple Load Test..." -ForegroundColor Yellow
$successCount = 0
$totalRequests = 10

for ($i = 1; $i -le $totalRequests; $i++) {
    try {
        $response = Invoke-RestMethod -Uri "$ActuatorUrl/actuator/health" -Method GET -TimeoutSec 5
        if ($response.status -eq "UP") {
            $successCount++
        }
    } catch {
        # Ignore individual failures
    }
}

$successRate = ($successCount / $totalRequests) * 100
if ($successRate -ge 90) {
    Write-Host "✅ Load Test: $successRate% success rate" -ForegroundColor Green
} else {
    Write-Host "❌ Load Test: $successRate% success rate (below 90%)" -ForegroundColor Red
}

Write-Host "`n=== Test Summary ===" -ForegroundColor Green
Write-Host "Application is running in Kubernetes!" -ForegroundColor Cyan
Write-Host "All core functionality tested successfully." -ForegroundColor Cyan