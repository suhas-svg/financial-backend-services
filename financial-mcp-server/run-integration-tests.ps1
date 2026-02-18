#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Run integration tests for MCP Financial Server HTTP clients
.DESCRIPTION
    This script runs the integration tests for the HTTP service clients,
    including circuit breaker functionality and service communication tests.
.EXAMPLE
    .\run-integration-tests.ps1
    .\run-integration-tests.ps1 -Verbose
    .\run-integration-tests.ps1 -Coverage
#>

param(
    [switch]$Verbose,
    [switch]$Coverage,
    [switch]$FailFast,
    [string]$TestPattern = "",
    [string]$OutputDir = "test-results"
)

# Set error action preference
$ErrorActionPreference = "Stop"

# Colors for output
$Green = "`e[32m"
$Red = "`e[31m"
$Yellow = "`e[33m"
$Blue = "`e[34m"
$Reset = "`e[0m"

function Write-ColorOutput {
    param([string]$Message, [string]$Color = $Reset)
    Write-Host "${Color}${Message}${Reset}"
}

function Test-PythonEnvironment {
    Write-ColorOutput "🔍 Checking Python environment..." $Blue
    
    # Check if we're in a virtual environment
    if (-not $env:VIRTUAL_ENV) {
        Write-ColorOutput "⚠️  Warning: Not in a virtual environment" $Yellow
        Write-ColorOutput "   Consider running: python -m venv venv && .\venv\Scripts\Activate.ps1" $Yellow
    } else {
        Write-ColorOutput "✅ Virtual environment active: $env:VIRTUAL_ENV" $Green
    }
    
    # Check Python version
    $pythonVersion = python --version 2>&1
    Write-ColorOutput "🐍 Python version: $pythonVersion" $Blue
    
    # Check if pytest is installed
    try {
        $pytestVersion = python -m pytest --version 2>&1
        Write-ColorOutput "🧪 Pytest version: $pytestVersion" $Blue
    } catch {
        Write-ColorOutput "❌ Pytest not found. Installing..." $Red
        python -m pip install pytest pytest-asyncio pytest-cov pytest-mock
    }
}

function Install-Dependencies {
    Write-ColorOutput "📦 Installing test dependencies..." $Blue
    
    # Install requirements
    if (Test-Path "requirements.txt") {
        python -m pip install -r requirements.txt
    } else {
        Write-ColorOutput "❌ requirements.txt not found" $Red
        exit 1
    }
    
    # Install additional test dependencies
    python -m pip install pytest-xdist pytest-html pytest-json-report
}

function Run-IntegrationTests {
    Write-ColorOutput "🚀 Running integration tests..." $Blue
    
    # Create output directory
    if (-not (Test-Path $OutputDir)) {
        New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    }
    
    # Build pytest command
    $pytestArgs = @(
        "tests/integration/"
        "--tb=short"
        "--strict-markers"
        "--disable-warnings"
    )
    
    # Add verbose output if requested
    if ($Verbose) {
        $pytestArgs += "-v"
    }
    
    # Add coverage if requested
    if ($Coverage) {
        $pytestArgs += @(
            "--cov=src/mcp_financial/clients"
            "--cov-report=html:$OutputDir/coverage-html"
            "--cov-report=xml:$OutputDir/coverage.xml"
            "--cov-report=term-missing"
        )
    }
    
    # Add fail fast if requested
    if ($FailFast) {
        $pytestArgs += "-x"
    }
    
    # Add test pattern if specified
    if ($TestPattern) {
        $pytestArgs += "-k", $TestPattern
    }
    
    # Add output formats
    $pytestArgs += @(
        "--html=$OutputDir/integration-test-report.html"
        "--self-contained-html"
        "--json-report"
        "--json-report-file=$OutputDir/integration-test-report.json"
    )
    
    Write-ColorOutput "📋 Running: python -m pytest $($pytestArgs -join ' ')" $Blue
    
    # Run the tests
    $testResult = python -m pytest @pytestArgs
    $exitCode = $LASTEXITCODE
    
    return $exitCode
}

function Show-TestResults {
    param([int]$ExitCode)
    
    Write-ColorOutput "`n📊 Test Results Summary" $Blue
    Write-ColorOutput "========================" $Blue
    
    if ($ExitCode -eq 0) {
        Write-ColorOutput "✅ All integration tests passed!" $Green
    } else {
        Write-ColorOutput "❌ Some tests failed (exit code: $ExitCode)" $Red
    }
    
    # Show output files
    if (Test-Path "$OutputDir/integration-test-report.html") {
        Write-ColorOutput "📄 HTML Report: $OutputDir/integration-test-report.html" $Blue
    }
    
    if (Test-Path "$OutputDir/integration-test-report.json") {
        Write-ColorOutput "📄 JSON Report: $OutputDir/integration-test-report.json" $Blue
    }
    
    if ($Coverage -and (Test-Path "$OutputDir/coverage-html/index.html")) {
        Write-ColorOutput "📊 Coverage Report: $OutputDir/coverage-html/index.html" $Blue
    }
}

function Run-SpecificTests {
    Write-ColorOutput "🎯 Running specific integration test scenarios..." $Blue
    
    $testScenarios = @(
        @{
            Name = "HTTP Client Base Functionality"
            Pattern = "test_base_client or test_circuit_breaker_functionality"
            Description = "Tests base HTTP client and circuit breaker"
        },
        @{
            Name = "Account Service Integration"
            Pattern = "TestAccountServiceClient"
            Description = "Tests Account Service HTTP client integration"
        },
        @{
            Name = "Transaction Service Integration"
            Pattern = "TestTransactionServiceClient"
            Description = "Tests Transaction Service HTTP client integration"
        },
        @{
            Name = "Circuit Breaker Scenarios"
            Pattern = "TestCircuitBreakerIntegration"
            Description = "Tests circuit breaker behavior under various conditions"
        },
        @{
            Name = "Service Integration Flows"
            Pattern = "TestServiceIntegrationScenarios"
            Description = "Tests end-to-end service integration scenarios"
        }
    )
    
    foreach ($scenario in $testScenarios) {
        Write-ColorOutput "`n🔍 Running: $($scenario.Name)" $Yellow
        Write-ColorOutput "   $($scenario.Description)" $Blue
        
        $result = python -m pytest "tests/integration/" -k $scenario.Pattern -v --tb=short
        
        if ($LASTEXITCODE -eq 0) {
            Write-ColorOutput "   ✅ Passed" $Green
        } else {
            Write-ColorOutput "   ❌ Failed" $Red
        }
    }
}

# Main execution
try {
    Write-ColorOutput "🧪 MCP Financial Server - Integration Tests" $Blue
    Write-ColorOutput "===========================================" $Blue
    
    # Change to script directory
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    Set-Location $scriptDir
    
    # Check environment
    Test-PythonEnvironment
    
    # Install dependencies
    Install-Dependencies
    
    # Run tests based on parameters
    if ($TestPattern -eq "scenarios") {
        Run-SpecificTests
        $exitCode = 0
    } else {
        $exitCode = Run-IntegrationTests
    }
    
    # Show results
    Show-TestResults -ExitCode $exitCode
    
    # Exit with test result code
    exit $exitCode
    
} catch {
    Write-ColorOutput "❌ Error running integration tests: $_" $Red
    exit 1
}