#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Runs end-to-end workflow tests for Transaction Service
.DESCRIPTION
    This script executes the comprehensive E2E test suite including workflow tests,
    integration tests, and deployment validation.
.PARAMETER TestProfile
    Test profile to use (default: test)
.PARAMETER SkipIntegration
    Skip integration tests (default: false)
.PARAMETER GenerateReport
    Generate detailed test report (default: true)
.EXAMPLE
    ./run-e2e-tests.ps1 -TestProfile test -GenerateReport $true
#>

param(
    [string]$TestProfile = "test",
    [bool]$SkipIntegration = $false,
    [bool]$GenerateReport = $true,
    [string]$OutputDir = "test-results"
)

# Configuration
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# Colors for output
$Green = "`e[32m"
$Red = "`e[31m"
$Yellow = "`e[33m"
$Blue = "`e[34m"
$Reset = "`e[0m"

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

function Test-Prerequisites {
    Write-Header "Checking Prerequisites"
    
    # Check if Maven is available
    try {
        $mvnVersion = & ./mvnw --version 2>$null
        Write-Success "Maven wrapper found: $(($mvnVersion -split "`n")[0])"
    }
    catch {
        Write-Error "Maven wrapper not found or not executable"
        return $false
    }
    
    # Check if Docker is available (for Testcontainers)
    try {
        $dockerVersion = docker --version 2>$null
        Write-Success "Docker found: $dockerVersion"
    }
    catch {
        Write-Error "Docker not found - required for Testcontainers"
        return $false
    }
    
    # Check if Docker daemon is running
    try {
        docker info 2>$null | Out-Null
        Write-Success "Docker daemon is running"
    }
    catch {
        Write-Error "Docker daemon is not running"
        return $false
    }
    
    return $true
}

function Start-TestContainers {
    Write-Header "Starting Test Infrastructure"
    
    # The Testcontainers will be started automatically by the tests
    # But we can pre-pull the images to speed up the process
    Write-Host "Pre-pulling Docker images for faster test execution..."
    
    try {
        docker pull postgres:15-alpine 2>$null
        Write-Success "PostgreSQL image ready"
    }
    catch {
        Write-Warning "Could not pre-pull PostgreSQL image"
    }
    
    try {
        docker pull redis:7-alpine 2>$null
        Write-Success "Redis image ready"
    }
    catch {
        Write-Warning "Could not pre-pull Redis image"
    }
}

function Run-UnitTests {
    Write-Header "Running Unit Tests"
    
    try {
        $result = & ./mvnw test -Dspring.profiles.active=$TestProfile -Dtest="!**/*IntegrationTest,!**/*E2ETest" 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Unit tests passed"
            return $true
        } else {
            Write-Error "Unit tests failed"
            Write-Host $result
            return $false
        }
    }
    catch {
        Write-Error "Failed to run unit tests: $($_.Exception.Message)"
        return $false
    }
}

function Run-IntegrationTests {
    if ($SkipIntegration) {
        Write-Warning "Skipping integration tests as requested"
        return $true
    }
    
    Write-Header "Running Integration Tests"
    
    try {
        $result = & ./mvnw test -Dspring.profiles.active=$TestProfile -Dtest="**/*IntegrationTest" 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Integration tests passed"
            return $true
        } else {
            Write-Error "Integration tests failed"
            Write-Host $result
            return $false
        }
    }
    catch {
        Write-Error "Failed to run integration tests: $($_.Exception.Message)"
        return $false
    }
}

function Run-E2ETests {
    Write-Header "Running End-to-End Workflow Tests"
    
    try {
        $result = & ./mvnw test -Dspring.profiles.active=$TestProfile -Dtest="**/*E2ETest" 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "E2E workflow tests passed"
            return $true
        } else {
            Write-Error "E2E workflow tests failed"
            Write-Host $result
            return $false
        }
    }
    catch {
        Write-Error "Failed to run E2E tests: $($_.Exception.Message)"
        return $false
    }
}

function Run-SecurityTests {
    Write-Header "Running Security Tests"
    
    try {
        $result = & ./mvnw test -Dspring.profiles.active=$TestProfile -Dtest="**/*SecurityTest,**/*AuthenticationTest" 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Security tests passed"
            return $true
        } else {
            Write-Error "Security tests failed"
            Write-Host $result
            return $false
        }
    }
    catch {
        Write-Error "Failed to run security tests: $($_.Exception.Message)"
        return $false
    }
}

function Generate-TestReport {
    if (-not $GenerateReport) {
        return
    }
    
    Write-Header "Generating Test Report"
    
    # Create output directory
    if (-not (Test-Path $OutputDir)) {
        New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    }
    
    # Generate Surefire report
    try {
        & ./mvnw surefire-report:report -Dspring.profiles.active=$TestProfile 2>$null
        Write-Success "Surefire report generated"
    }
    catch {
        Write-Warning "Could not generate Surefire report"
    }
    
    # Generate JaCoCo coverage report
    try {
        & ./mvnw jacoco:report -Dspring.profiles.active=$TestProfile 2>$null
        Write-Success "JaCoCo coverage report generated"
    }
    catch {
        Write-Warning "Could not generate JaCoCo coverage report"
    }
    
    # Copy reports to output directory
    if (Test-Path "target/site/surefire-report.html") {
        Copy-Item "target/site/surefire-report.html" "$OutputDir/" -Force
        Write-Success "Test report copied to $OutputDir/surefire-report.html"
    }
    
    if (Test-Path "target/site/jacoco/index.html") {
        Copy-Item "target/site/jacoco" "$OutputDir/" -Recurse -Force
        Write-Success "Coverage report copied to $OutputDir/jacoco/"
    }
    
    # Generate summary report
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $summary = @"
# Transaction Service E2E Test Report

**Generated:** $timestamp
**Test Profile:** $TestProfile
**Skip Integration:** $SkipIntegration

## Test Results

- ✅ Unit Tests: Completed
- ✅ Integration Tests: $(if ($SkipIntegration) { "Skipped" } else { "Completed" })
- ✅ E2E Workflow Tests: Completed
- ✅ Security Tests: Completed

## Reports Generated

- Surefire Report: [surefire-report.html](./surefire-report.html)
- Coverage Report: [jacoco/index.html](./jacoco/index.html)

## Test Coverage

See the JaCoCo coverage report for detailed coverage metrics.

## Next Steps

1. Review any failed tests in the Surefire report
2. Check coverage metrics and improve if needed
3. Run deployment validation script before deploying

"@
    
    $summary | Out-File "$OutputDir/README.md" -Encoding UTF8
    Write-Success "Summary report generated: $OutputDir/README.md"
}

function Cleanup-TestResources {
    Write-Header "Cleaning Up Test Resources"
    
    # Clean up any remaining Docker containers
    try {
        $containers = docker ps -a --filter "label=org.testcontainers" --format "{{.ID}}" 2>$null
        if ($containers) {
            docker rm -f $containers 2>$null
            Write-Success "Cleaned up Testcontainer resources"
        }
    }
    catch {
        Write-Warning "Could not clean up Docker containers"
    }
    
    # Clean up any test networks
    try {
        $networks = docker network ls --filter "label=org.testcontainers" --format "{{.ID}}" 2>$null
        if ($networks) {
            docker network rm $networks 2>$null
            Write-Success "Cleaned up test networks"
        }
    }
    catch {
        Write-Warning "Could not clean up Docker networks"
    }
}

# Main execution
try {
    Write-Host "${Blue}Starting Transaction Service E2E Test Suite...${Reset}"
    Write-Host "Test Profile: $TestProfile"
    Write-Host "Skip Integration: $SkipIntegration"
    Write-Host "Generate Report: $GenerateReport"
    Write-Host "Output Directory: $OutputDir"
    Write-Host ""
    
    # Change to transaction-service directory
    if (Test-Path "transaction-service") {
        Set-Location "transaction-service"
    }
    
    # Check prerequisites
    if (-not (Test-Prerequisites)) {
        Write-Error "Prerequisites check failed"
        exit 1
    }
    
    # Start test infrastructure
    Start-TestContainers
    
    # Track test results
    $testResults = @{
        UnitTests = $false
        IntegrationTests = $false
        E2ETests = $false
        SecurityTests = $false
    }
    
    # Run test suites
    $testResults.UnitTests = Run-UnitTests
    $testResults.IntegrationTests = Run-IntegrationTests
    $testResults.E2ETests = Run-E2ETests
    $testResults.SecurityTests = Run-SecurityTests
    
    # Generate reports
    Generate-TestReport
    
    # Calculate overall success
    $allPassed = $testResults.UnitTests -and 
                 ($SkipIntegration -or $testResults.IntegrationTests) -and 
                 $testResults.E2ETests -and 
                 $testResults.SecurityTests
    
    Write-Header "Test Execution Summary"
    
    if ($testResults.UnitTests) {
        Write-Success "Unit Tests: PASSED"
    } else {
        Write-Error "Unit Tests: FAILED"
    }
    
    if ($SkipIntegration) {
        Write-Warning "Integration Tests: SKIPPED"
    } elseif ($testResults.IntegrationTests) {
        Write-Success "Integration Tests: PASSED"
    } else {
        Write-Error "Integration Tests: FAILED"
    }
    
    if ($testResults.E2ETests) {
        Write-Success "E2E Workflow Tests: PASSED"
    } else {
        Write-Error "E2E Workflow Tests: FAILED"
    }
    
    if ($testResults.SecurityTests) {
        Write-Success "Security Tests: PASSED"
    } else {
        Write-Error "Security Tests: FAILED"
    }
    
    if ($allPassed) {
        Write-Host "`n${Green}🎉 All E2E workflow tests completed successfully!${Reset}"
        Write-Host "Reports available in: $OutputDir"
        exit 0
    } else {
        Write-Host "`n${Red}❌ Some tests failed. Check the reports for details.${Reset}"
        exit 1
    }
}
catch {
    Write-Error "Fatal error during test execution: $($_.Exception.Message)"
    exit 1
}
finally {
    # Always cleanup
    Cleanup-TestResources
}