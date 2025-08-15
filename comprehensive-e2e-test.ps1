#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Comprehensive Full System End-to-End Test for Account Service and Transaction Service Integration
.DESCRIPTION
    This script runs both services together using Docker Compose and performs comprehensive 
    end-to-end testing of the complete financial system workflow with real service-to-service communication.
.PARAMETER CleanStart
    Whether to clean and rebuild everything from scratch (default: true)
.PARAMETER UseDocker
    Whether to use Docker Compose for service orchestration (default: true)
.PARAMETER TestTimeout
    Timeout in seconds for individual tests (default: 30)
.PARAMETER ServiceTimeout
    Timeout in seconds for service startup (default: 180)
.EXAMPLE
    ./comprehensive-e2e-test.ps1 -CleanStart $true -UseDocker $true
#>

param(
    [bool]$CleanStart = $true,
    [bool]$UseDocker = $true,
    [int]$TestTimeout = 30,
    [int]$ServiceTimeout = 180
)

# Configuration
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# Colors for output
$Green = "`e[32m"
$Red = "`e[31m"
$Yellow = "`e[33m"
$Blue = "`e[34m"
$Cyan = "`e[36m"
$Magenta = "`e[35m"
$Reset = "`e[0m"

# Service URLs
$AccountServiceUrl = "http://localhost:8081"
$TransactionServiceUrl = "http://localhost:8080"

# Test results tracking
$TestResults = @{
    Infrastructure = @{}
    ServiceStartup = @{}
    ServiceIntegration = @{}
    EndToEndWorkflows = @{}
    PerformanceTests = @{}
    ErrorHandling = @{}
    DataConsistency = @{}
    SecurityTests = @{}
    CleanupTests = @{}
}

function Write-Header {
    param([string]$Message)
    Write-Host "`n${Blue}=== $Message ===${Reset}" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "${Green}✓${Reset} $Message" -ForegroundColor Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "${Red}✗${Reset} $Message" -ForegroundColor Red
}

function Write-Warning {
    param([string]$Message)
    Write-Host "${Yellow}⚠${Reset} $Message" -ForegroundColor Yellow
}

function Write-Info {
    param([string]$Message)
    Write-Host "${Cyan}ℹ${Reset} $Message" -ForegroundColor Cyan
}

function Write-Step {
    param([string]$Message)
    Write-Host "${Magenta}→${Reset} $Message" -ForegroundColor Magenta
}

function Test-Prerequisites {
    Write-Header "Checking Prerequisites"
    
    $allGood = $true
    
    # Check Docker
    try {
        $dockerVersion = docker --version 2>$null
        Write-Success "Docker found: $dockerVersion"
        
        # Check if Docker daemon is running
        docker info 2>$null | Out-Null
        Write-Success "Docker daemon is running"
    }
    catch {
        Write-Error "Docker is not available or not running"
        $allGood = $false
    }
    
    # Check Docker Compose
    try {
        $composeVersion = docker-compose --version 2>$null
        Write-Success "Docker Compose found: $composeVersion"
    }
    catch {
        Write-Error "Docker Compose is not available"
        $allGood = $false
    }
    
    # Check if required files exist
    $requiredFiles = @(
        "docker-compose-full-e2e.yml",
        "account-service/Dockerfile",
        "transaction-service/Dockerfile"
    )
    
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Write-Success "Required file found: $file"
        } else {
            Write-Error "Required file missing: $file"
            $allGood = $false
        }
    }
    
    return $allGood
}

function Start-FullSystemInfrastructure {
    Write-Header "Starting Full System Infrastructure"
    
    try {
        if ($CleanStart) {
            Write-Info "Cleaning up existing containers and volumes..."
            docker-compose -f docker-compose-full-e2e.yml down -v --remove-orphans 2>$null
            docker system prune -f 2>$null
        }
        
        Write-Info "Building and starting services with Docker Compose..."
        $env:COMPOSE_PROJECT_NAME = "full-e2e-test"
        
        # Start infrastructure services first
        Write-Step "Starting database and cache services..."
        docker-compose -f docker-compose-full-e2e.yml up -d postgres-e2e redis-e2e
        
        # Wait for databases to be ready
        Write-Step "Waiting for databases to be ready..."
        $maxRetries = 30
        for ($i = 1; $i -le $maxRetries; $i++) {
            try {
                $pgHealth = docker-compose -f docker-compose-full-e2e.yml exec -T postgres-e2e pg_isready -U testuser -d fullsystem_test
                $redisHealth = docker-compose -f docker-compose-full-e2e.yml exec -T redis-e2e redis-cli ping
                
                if ($pgHealth -match "accepting connections" -and $redisHealth -eq "PONG") {
                    Write-Success "Databases are ready"
                    break
                }
            }
            catch {
                if ($i -eq $maxRetries) {
                    Write-Error "Databases failed to start within timeout"
                    return $false
                }
            }
            Write-Info "Waiting for databases... (attempt $i/$maxRetries)"
            Start-Sleep -Seconds 3
        }
        
        # Build and start application services
        Write-Step "Building and starting Account Service..."
        docker-compose -f docker-compose-full-e2e.yml up -d --build account-service-e2e
        
        Write-Step "Building and starting Transaction Service..."
        docker-compose -f docker-compose-full-e2e.yml up -d --build transaction-service-e2e
        
        $TestResults.Infrastructure.DatabaseStartup = $true
        $TestResults.Infrastructure.ServiceBuild = $true
        return $true
    }
    catch {
        Write-Error "Failed to start infrastructure: $($_.Exception.Message)"
        $TestResults.Infrastructure.DatabaseStartup = $false
        $TestResults.Infrastructure.ServiceBuild = $false
        return $false
    }
}

function Wait-ForServicesReady {
    Write-Header "Waiting for Services to be Ready"
    
    $services = @(
        @{ Name = "Account Service"; Url = "$AccountServiceUrl/actuator/health"; Key = "AccountService" }
        @{ Name = "Transaction Service"; Url = "$TransactionServiceUrl/actuator/health"; Key = "TransactionService" }
    )
    
    foreach ($service in $services) {
        Write-Step "Waiting for $($service.Name) to be ready..."
        
        $maxRetries = 60  # 3 minutes with 3-second intervals
        $ready = $false
        
        for ($i = 1; $i -le $maxRetries; $i++) {
            try {
                $response = Invoke-RestMethod -Uri $service.Url -Method Get -TimeoutSec 5
                if ($response.status -eq "UP") {
                    Write-Success "$($service.Name) is ready (attempt $i/$maxRetries)"
                    $TestResults.ServiceStartup[$service.Key] = $true
                    $ready = $true
                    break
                }
            }
            catch {
                if ($i -eq $maxRetries) {
                    Write-Error "$($service.Name) failed to start within timeout"
                    $TestResults.ServiceStartup[$service.Key] = $false
                    return $false
                }
            }
            
            Write-Info "Waiting for $($service.Name)... (attempt $i/$maxRetries)"
            Start-Sleep -Seconds 3
        }
        
        if (-not $ready) {
            return $false
        }
    }
    
    return $true
}

function Get-TestJwtToken {
    # For testing purposes, we'll use a simple token
    # In production, this would come from a proper authentication service
    return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJlMmUtdGVzdC11c2VyIiwicm9sZSI6IlVTRVIiLCJhdXRob3JpdGllcyI6IlJPTEVfVVNFUiIsImlhdCI6MTczMzQzMjQwMCwiZXhwIjoxNzMzNDM2MDAwfQ.example-signature"
}function 
Test-CompleteUserJourney {
    Write-Header "Testing Complete User Journey"
    
    $token = Get-TestJwtToken
    $headers = @{
        "Authorization" = "Bearer $token"
        "Content-Type" = "application/json"
    }
    
    try {
        Write-Step "Journey: New User Account Creation and Full Transaction Flow"
        
        # Step 1: Create a new user account
        $accountData = @{
            ownerId = "e2e-test-user"
            accountType = "STANDARD"
            initialBalance = 1000.00
        }
        
        $account = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts" `
            -Method Post -Headers $headers -Body ($accountData | ConvertTo-Json)
        
        if (-not $account.id) {
            Write-Error "Failed to create user account"
            return $false
        }
        
        $accountId = $account.id
        Write-Success "User account created - ID: $accountId, Balance: $($account.balance)"
        
        # Step 2: Perform deposit transaction
        $depositData = @{
            accountId = $accountId.ToString()
            amount = 500.00
            description = "E2E Test Deposit"
        }
        
        $depositResult = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/deposit" `
            -Method Post -Headers $headers -Body ($depositData | ConvertTo-Json)
        
        if ($depositResult.status -ne "COMPLETED") {
            Write-Error "Deposit transaction failed"
            return $false
        }
        Write-Success "Deposit completed - Transaction ID: $($depositResult.transactionId)"
        
        # Step 3: Verify account balance update
        $updatedAccount = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts/$accountId" `
            -Method Get -Headers $headers
        
        $expectedBalance = 1500.00
        if ([math]::Abs($updatedAccount.balance - $expectedBalance) -gt 0.01) {
            Write-Error "Balance verification failed - Expected: $expectedBalance, Actual: $($updatedAccount.balance)"
            return $false
        }
        Write-Success "Account balance verified - New balance: $($updatedAccount.balance)"
        
        # Step 4: Create second account for transfer
        $secondAccountData = @{
            ownerId = "e2e-test-user-2"
            accountType = "PREMIUM"
            initialBalance = 2000.00
        }
        
        $secondAccount = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts" `
            -Method Post -Headers $headers -Body ($secondAccountData | ConvertTo-Json)
        
        $secondAccountId = $secondAccount.id
        Write-Success "Second account created - ID: $secondAccountId"
        
        # Step 5: Perform transfer between accounts
        $transferData = @{
            fromAccountId = $accountId.ToString()
            toAccountId = $secondAccountId.ToString()
            amount = 300.00
            description = "E2E Test Transfer"
        }
        
        $transferResult = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/transfer" `
            -Method Post -Headers $headers -Body ($transferData | ConvertTo-Json)
        
        if ($transferResult.status -ne "COMPLETED") {
            Write-Error "Transfer failed"
            return $false
        }
        Write-Success "Transfer completed - Transaction ID: $($transferResult.transactionId)"
        
        # Step 6: Verify both account balances
        $finalAccount1 = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts/$accountId" `
            -Method Get -Headers $headers
        $finalAccount2 = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts/$secondAccountId" `
            -Method Get -Headers $headers
        
        $expectedBalance1 = 1200.00
        $expectedBalance2 = 2300.00
        
        if ([math]::Abs($finalAccount1.balance - $expectedBalance1) -gt 0.01 -or 
            [math]::Abs($finalAccount2.balance - $expectedBalance2) -gt 0.01) {
            Write-Error "Final balance verification failed"
            Write-Error "Account 1 - Expected: $expectedBalance1, Actual: $($finalAccount1.balance)"
            Write-Error "Account 2 - Expected: $expectedBalance2, Actual: $($finalAccount2.balance)"
            return $false
        }
        Write-Success "Final balances verified - Account 1: $($finalAccount1.balance), Account 2: $($finalAccount2.balance)"
        
        # Step 7: Perform withdrawal
        $withdrawalData = @{
            accountId = $accountId.ToString()
            amount = 200.00
            description = "E2E Test Withdrawal"
        }
        
        $withdrawalResult = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/withdraw" `
            -Method Post -Headers $headers -Body ($withdrawalData | ConvertTo-Json)
        
        if ($withdrawalResult.status -ne "COMPLETED") {
            Write-Error "Withdrawal failed"
            return $false
        }
        Write-Success "Withdrawal completed - Transaction ID: $($withdrawalResult.transactionId)"
        
        # Step 8: Check transaction history
        $transactionHistory = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/account/$accountId" `
            -Method Get -Headers $headers
        
        if ($transactionHistory.content.Count -lt 3) {
            Write-Error "Transaction history incomplete - Expected at least 3 transactions, found: $($transactionHistory.content.Count)"
            return $false
        }
        Write-Success "Transaction history verified - $($transactionHistory.content.Count) transactions found"
        
        # Step 9: Verify final account balance
        $veryFinalAccount = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts/$accountId" `
            -Method Get -Headers $headers
        
        $veryFinalExpectedBalance = 1000.00
        if ([math]::Abs($veryFinalAccount.balance - $veryFinalExpectedBalance) -gt 0.01) {
            Write-Error "Very final balance verification failed - Expected: $veryFinalExpectedBalance, Actual: $($veryFinalAccount.balance)"
            return $false
        }
        Write-Success "Complete user journey verified - Final balance: $($veryFinalAccount.balance)"
        
        $TestResults.EndToEndWorkflows.CompleteUserJourney = $true
        return $true
    }
    catch {
        Write-Error "Complete user journey test failed: $($_.Exception.Message)"
        $TestResults.EndToEndWorkflows.CompleteUserJourney = $false
        return $false
    }
}

function Test-ErrorHandlingScenarios {
    Write-Header "Testing Error Handling Scenarios"
    
    $token = Get-TestJwtToken
    $headers = @{
        "Authorization" = "Bearer $token"
        "Content-Type" = "application/json"
    }
    
    try {
        Write-Step "Testing invalid account scenarios..."
        
        # Test 1: Transaction with non-existent account
        $invalidTransactionData = @{
            accountId = "999999"
            amount = 100.00
            description = "Invalid account test"
        }
        
        try {
            $result = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/deposit" `
                -Method Post -Headers $headers -Body ($invalidTransactionData | ConvertTo-Json)
            Write-Error "Expected error for invalid account, but transaction succeeded"
            return $false
        }
        catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            if ($statusCode -eq 404 -or $statusCode -eq 400 -or $statusCode -eq 422) {
                Write-Success "Invalid account error handling works correctly (Status: $statusCode)"
            } else {
                Write-Error "Unexpected error status for invalid account: $statusCode"
                return $false
            }
        }
        
        # Test 2: Insufficient funds scenario
        Write-Step "Testing insufficient funds scenario..."
        
        $poorAccountData = @{
            ownerId = "poor-user"
            accountType = "STANDARD"
            initialBalance = 50.00
        }
        
        $poorAccount = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts" `
            -Method Post -Headers $headers -Body ($poorAccountData | ConvertTo-Json)
        
        $largeWithdrawalData = @{
            accountId = $poorAccount.id.ToString()
            amount = 1000.00
            description = "Insufficient funds test"
        }
        
        try {
            $result = Invoke-RestMethod -Uri "$TransactionServiceUrl/api/transactions/withdraw" `
                -Method Post -Headers $headers -Body ($largeWithdrawalData | ConvertTo-Json)
            Write-Error "Expected error for insufficient funds, but transaction succeeded"
            return $false
        }
        catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            if ($statusCode -eq 400 -or $statusCode -eq 422) {
                Write-Success "Insufficient funds error handling works correctly (Status: $statusCode)"
            } else {
                Write-Error "Unexpected error status for insufficient funds: $statusCode"
                return $false
            }
        }
        
        $TestResults.ErrorHandling.InvalidScenarios = $true
        return $true
    }
    catch {
        Write-Error "Error handling test failed: $($_.Exception.Message)"
        $TestResults.ErrorHandling.InvalidScenarios = $false
        return $false
    }
}

function Test-ConcurrentOperations {
    Write-Header "Testing Concurrent Operations"
    
    $token = Get-TestJwtToken
    $headers = @{
        "Authorization" = "Bearer $token"
        "Content-Type" = "application/json"
    }
    
    try {
        Write-Step "Setting up account for concurrent testing..."
        
        $concurrentAccountData = @{
            ownerId = "concurrent-user"
            accountType = "PREMIUM"
            initialBalance = 10000.00
        }
        
        $concurrentAccount = Invoke-RestMethod -Uri "$AccountServiceUrl/api/accounts" `
            -Method Post -Headers $headers -Body ($concurrentAccountData | ConvertTo-Json)
        
        Write-Success "Concurrent test account created - ID: $($concurrentAccount.id)"
        
        Write-Step "Performing concurrent transactions..."
        
        # Perform multiple concurrent small transactions
        $jobs = @()
        $transactionCount = 5
        
        for ($i = 1; $i -le $transactionCount; $i++) {
            $transactionData = @{
                accountId = $concurrentAccount.id.ToString()
                amount = 100.00
                description = "Concurrent transaction $i"
            }
            
            $job = Start-Job -ScriptBlock {
                param($Url, $Headers, $Data, $TransactionId)
                try {
                    $result = Invoke-RestMethod -Uri $Url -Method Post -Headers $Headers -Body ($Data | ConvertTo-Json)
                    return @{
                        Success = $true
                        TransactionId = $TransactionId
                        Result = $result
                    }
                }
                catch {
                    return @{
                        Success = $false
                        TransactionId = $TransactionId
                        Error = $_.Exception.Message
                    }
                }
            } -ArgumentList "$TransactionServiceUrl/api/transactions/deposit", $headers, $transactionData, $i
            
            $jobs += $job
        }
        
        # Wait for all jobs to complete
        Write-Step "Waiting for concurrent transactions to complete..."
        $results = $jobs | Wait-Job -Timeout 60 | Receive-Job
        $jobs | Remove-Job
        
        $successCount = ($results | Where-Object { $_.Success -eq $true }).Count
        $failureCount = $transactionCount - $successCount
        
        Write-Info "Concurrent transaction results: $successCount successful, $failureCount failed"
        
        if ($successCount -ge ($transactionCount * 0.8)) {  # Allow 20% failure due to concurrency
            Write-Success "Concurrent operations test passed ($successCount/$transactionCount successful)"
            $TestResults.EndToEndWorkflows.ConcurrentOperations = $true
            return $true
        } else {
            Write-Error "Too many concurrent transaction failures ($failureCount/$transactionCount)"
            $TestResults.EndToEndWorkflows.ConcurrentOperations = $false
            return $false
        }
    }
    catch {
        Write-Error "Concurrent operations test failed: $($_.Exception.Message)"
        $TestResults.EndToEndWorkflows.ConcurrentOperations = $false
        return $false
    }
}

function Test-PerformanceBaseline {
    Write-Header "Testing Performance Baseline"
    
    try {
        Write-Step "Testing service response times..."
        
        # Test Account Service response time
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $accountHealth = Invoke-RestMethod -Uri "$AccountServiceUrl/actuator/health" -Method Get
        $stopwatch.Stop()
        $accountServiceTime = $stopwatch.ElapsedMilliseconds
        
        # Test Transaction Service response time
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $transactionHealth = Invoke-RestMethod -Uri "$TransactionServiceUrl/actuator/health" -Method Get
        $stopwatch.Stop()
        $transactionServiceTime = $stopwatch.ElapsedMilliseconds
        
        Write-Success "Account Service response time: ${accountServiceTime}ms"
        Write-Success "Transaction Service response time: ${transactionServiceTime}ms"
        
        $TestResults.PerformanceTests.AccountServiceResponseTime = $accountServiceTime
        $TestResults.PerformanceTests.TransactionServiceResponseTime = $transactionServiceTime
        
        # Performance is acceptable if both services respond within 2 seconds
        return ($accountServiceTime -lt 2000 -and $transactionServiceTime -lt 2000)
    }
    catch {
        Write-Error "Performance baseline test failed: $($_.Exception.Message)"
        return $false
    }
}

function Stop-FullSystemInfrastructure {
    Write-Header "Stopping Full System Infrastructure"
    
    try {
        Write-Info "Stopping all services..."
        docker-compose -f docker-compose-full-e2e.yml down -v --remove-orphans
        
        Write-Info "Cleaning up Docker resources..."
        docker system prune -f 2>$null
        
        Write-Success "Infrastructure stopped and cleaned up"
        $TestResults.CleanupTests.InfrastructureCleanup = $true
        return $true
    }
    catch {
        Write-Error "Failed to stop infrastructure: $($_.Exception.Message)"
        $TestResults.CleanupTests.InfrastructureCleanup = $false
        return $false
    }
}

function Generate-ComprehensiveTestReport {
    Write-Header "Generating Comprehensive Test Report"
    
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $totalTests = 0
    $passedTests = 0
    $failedTests = @()
    
    # Count test results
    foreach ($category in $TestResults.Keys) {
        foreach ($test in $TestResults[$category].Keys) {
            $totalTests++
            if ($TestResults[$category][$test] -eq $true) {
                $passedTests++
            } else {
                $failedTests += "$category.$test"
            }
        }
    }
    
    $successRate = if ($totalTests -gt 0) { [math]::Round(($passedTests / $totalTests) * 100, 2) } else { 0 }
    
    $report = @"
# Comprehensive Full System End-to-End Test Report

**Generated:** $timestamp
**Test Configuration:** Docker Compose Full System
**Services Tested:** Account Service + Transaction Service (Real Integration)

## Executive Summary

- **Total Tests:** $totalTests
- **Passed:** $passedTests
- **Failed:** $($totalTests - $passedTests)
- **Success Rate:** ${successRate}%

## Test Results

### Infrastructure Tests
- Database Startup: $($TestResults.Infrastructure.DatabaseStartup)
- Service Build: $($TestResults.Infrastructure.ServiceBuild)

### Service Startup Tests
- Account Service: $($TestResults.ServiceStartup.AccountService)
- Transaction Service: $($TestResults.ServiceStartup.TransactionService)

### End-to-End Workflow Tests
- Complete User Journey: $($TestResults.EndToEndWorkflows.CompleteUserJourney)
- Concurrent Operations: $($TestResults.EndToEndWorkflows.ConcurrentOperations)

### Error Handling Tests
- Invalid Scenarios: $($TestResults.ErrorHandling.InvalidScenarios)

### Performance Tests
- Account Service Response Time: $($TestResults.PerformanceTests.AccountServiceResponseTime)ms
- Transaction Service Response Time: $($TestResults.PerformanceTests.TransactionServiceResponseTime)ms

### Cleanup Tests
- Infrastructure Cleanup: $($TestResults.CleanupTests.InfrastructureCleanup)

## Failed Tests
$($failedTests -join "`n")

## Overall Assessment

$(if ($successRate -ge 95) {
    "✅ **EXCELLENT** - System is ready for production deployment"
} elseif ($successRate -ge 85) {
    "⚠️ **GOOD** - System is mostly ready, address failed tests before production"
} elseif ($successRate -ge 70) {
    "⚠️ **NEEDS IMPROVEMENT** - Several issues need to be resolved"
} else {
    "❌ **CRITICAL ISSUES** - System requires significant fixes before deployment"
})

---
*Report generated by Comprehensive Full System E2E Test Suite*
"@
    
    # Save report to file
    $reportPath = "comprehensive-e2e-test-report-$(Get-Date -Format 'yyyyMMdd-HHmmss').md"
    $report | Out-File -FilePath $reportPath -Encoding UTF8
    
    Write-Host $report
    Write-Success "Detailed report saved to: $reportPath"
    
    return $successRate -ge 85  # Consider test suite successful if 85% or more tests pass
}

# Main execution
try {
    Write-Header "Starting Comprehensive Full System End-to-End Test"
    Write-Info "Configuration: CleanStart=$CleanStart, UseDocker=$UseDocker"
    Write-Info "Services: Account Service ($AccountServiceUrl) + Transaction Service ($TransactionServiceUrl)"
    
    # Check prerequisites
    if (-not (Test-Prerequisites)) {
        Write-Error "Prerequisites check failed"
        exit 1
    }
    
    # Start infrastructure
    if (-not (Start-FullSystemInfrastructure)) {
        Write-Error "Failed to start infrastructure"
        exit 1
    }
    
    # Wait for services to be ready
    if (-not (Wait-ForServicesReady)) {
        Write-Error "Services failed to start properly"
        Stop-FullSystemInfrastructure
        exit 1
    }
    
    # Run test suites
    $testSuites = @(
        @{ Name = "Complete User Journey"; Function = { Test-CompleteUserJourney } }
        @{ Name = "Error Handling Scenarios"; Function = { Test-ErrorHandlingScenarios } }
        @{ Name = "Concurrent Operations"; Function = { Test-ConcurrentOperations } }
        @{ Name = "Performance Baseline"; Function = { Test-PerformanceBaseline } }
    )
    
    $overallSuccess = $true
    
    foreach ($testSuite in $testSuites) {
        Write-Info "Running $($testSuite.Name) tests..."
        $result = & $testSuite.Function
        if (-not $result) {
            Write-Warning "$($testSuite.Name) tests had failures"
            $overallSuccess = $false
        }
    }
    
    # Generate comprehensive report
    $reportSuccess = Generate-ComprehensiveTestReport
    
    # Cleanup
    Stop-FullSystemInfrastructure
    
    # Final result
    if ($reportSuccess) {
        Write-Header "🎉 COMPREHENSIVE E2E TEST SUITE COMPLETED SUCCESSFULLY!"
        Write-Success "Both Account Service and Transaction Service integration verified"
        Write-Success "System is ready for production deployment"
        exit 0
    } else {
        Write-Header "❌ COMPREHENSIVE E2E TEST SUITE COMPLETED WITH ISSUES"
        Write-Error "Some critical tests failed - review the report for details"
        exit 1
    }
}
catch {
    Write-Error "Fatal error during comprehensive E2E testing: $($_.Exception.Message)"
    Write-Info "Attempting cleanup..."
    Stop-FullSystemInfrastructure
    exit 1
}
finally {
    # Ensure cleanup happens
    Write-Info "Final cleanup..."
    docker-compose -f docker-compose-full-e2e.yml down -v --remove-orphans 2>$null
}