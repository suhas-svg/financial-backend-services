# Comprehensive E2E Infrastructure Test Runner
# This script sets up the E2E infrastructure and runs the infrastructure validation tests

param(
    [switch]$SetupOnly,
    [switch]$TestOnly,
    [switch]$CleanupAfter,
    [switch]$UnitTestsOnly,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

function Write-Header {
    param([string]$Message)
    Write-Host "`n=== $Message ===" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Write-Info {
    param([string]$Message)
    Write-Host "ℹ️  $Message" -ForegroundColor Blue
}

function Setup-E2EInfrastructure {
    Write-Header "Setting up E2E Infrastructure"
    
    try {
        # Start E2E infrastructure
        Write-Info "Starting E2E infrastructure..."
        npm run e2e:start
        
        # Wait a bit for services to stabilize
        Write-Info "Waiting for services to stabilize..."
        Start-Sleep -Seconds 10
        
        # Validate infrastructure
        Write-Info "Validating E2E infrastructure..."
        npm run e2e:validate
        
        Write-Success "E2E Infrastructure setup completed"
    }
    catch {
        Write-Error "Failed to setup E2E infrastructure: $($_.Exception.Message)"
        throw
    }
}

function Run-InfrastructureTests {
    Write-Header "Running Infrastructure Validation Tests"
    
    try {
        if ($UnitTestsOnly) {
            Write-Info "Running unit tests only..."
            npm run test:e2e:unit
        }
        else {
            Write-Info "Running comprehensive infrastructure tests..."
            if ($Verbose) {
                npm run test:e2e -- --verbose
            }
            else {
                npm run test:e2e
            }
        }
        
        Write-Success "Infrastructure tests completed successfully"
    }
    catch {
        Write-Error "Infrastructure tests failed: $($_.Exception.Message)"
        throw
    }
}

function Cleanup-E2EInfrastructure {
    Write-Header "Cleaning up E2E Infrastructure"
    
    try {
        npm run e2e:stop
        Write-Success "E2E Infrastructure cleanup completed"
    }
    catch {
        Write-Error "Failed to cleanup E2E infrastructure: $($_.Exception.Message)"
        # Don't throw here, as cleanup is best effort
    }
}

function Show-Help {
    Write-Host @"
E2E Infrastructure Test Runner

This script sets up the E2E infrastructure and runs infrastructure validation tests.

Usage: .\run-infrastructure-tests.ps1 [OPTIONS]

Options:
  -SetupOnly      Only setup the E2E infrastructure (don't run tests)
  -TestOnly       Only run tests (assume infrastructure is already running)
  -CleanupAfter   Cleanup infrastructure after tests complete
  -UnitTestsOnly  Run only unit tests (no infrastructure required)
  -Verbose        Run tests with verbose output

Examples:
  .\run-infrastructure-tests.ps1                    # Full cycle: setup, test, keep running
  .\run-infrastructure-tests.ps1 -CleanupAfter      # Full cycle with cleanup
  .\run-infrastructure-tests.ps1 -SetupOnly         # Just setup infrastructure
  .\run-infrastructure-tests.ps1 -TestOnly          # Just run tests
  .\run-infrastructure-tests.ps1 -UnitTestsOnly     # Run unit tests only
  .\run-infrastructure-tests.ps1 -Verbose           # Run with verbose output

Prerequisites:
  - Docker and Docker Compose installed and running
  - Node.js and npm installed
  - All dependencies installed (npm install)

Infrastructure Services:
  - Account Service: http://localhost:8083
  - Transaction Service: http://localhost:8082
  - Account Database: localhost:5434
  - Transaction Database: localhost:5435
  - Redis: localhost:6380
"@
}

# Main execution logic
try {
    if ($UnitTestsOnly) {
        Write-Header "Running Unit Tests Only"
        Run-InfrastructureTests
        Write-Success "Unit tests completed successfully"
        exit 0
    }
    
    if ($SetupOnly) {
        Setup-E2EInfrastructure
        Write-Success "E2E Infrastructure is ready for testing"
        Write-Info "Run tests with: npm run test:e2e"
        Write-Info "Stop infrastructure with: npm run e2e:stop"
        exit 0
    }
    
    if (-not $TestOnly) {
        Setup-E2EInfrastructure
    }
    
    Run-InfrastructureTests
    
    if ($CleanupAfter) {
        Cleanup-E2EInfrastructure
    }
    else {
        Write-Info "E2E Infrastructure is still running"
        Write-Info "Stop it with: npm run e2e:stop"
        Write-Info "Check status with: npm run e2e:status"
    }
    
    Write-Success "Infrastructure test execution completed successfully"
}
catch {
    Write-Error "Infrastructure test execution failed: $($_.Exception.Message)"
    
    if ($CleanupAfter -and -not $TestOnly) {
        Write-Info "Attempting cleanup due to failure..."
        Cleanup-E2EInfrastructure
    }
    
    exit 1
}

if ($args.Count -eq 0 -and -not $SetupOnly -and -not $TestOnly -and -not $UnitTestsOnly) {
    Show-Help
}