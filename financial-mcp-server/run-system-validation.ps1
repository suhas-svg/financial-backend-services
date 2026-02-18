#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Run comprehensive system validation and integration tests for MCP Financial Server
.DESCRIPTION
    This script runs comprehensive system validation tests including:
    - End-to-end integration with financial services
    - JWT authentication flow validation
    - MCP client integration scenarios
    - Security testing and vulnerability assessment
    - Monitoring and alerting integration validation
.EXAMPLE
    .\run-system-validation.ps1
    .\run-system-validation.ps1 -Verbose -Coverage
    .\run-system-validation.ps1 -SecurityOnly
    .\run-system-validation.ps1 -IntegrationOnly
#>

param(
    [switch]$Verbose,
    [switch]$Coverage,
    [switch]$SecurityOnly,
    [switch]$IntegrationOnly,
    [switch]$FailFast,
    [switch]$GenerateReport,
    [string]$OutputDir = "system-validation-results",
    [string]$Environment = "test"
)

# Set error action preference
$ErrorActionPreference = "Stop"

# Colors for output
$Green = "`e[32m"
$Red = "`e[31m"
$Yellow = "`e[33m"
$Blue = "`e[34m"
$Cyan = "`e[36m"
$Magenta = "`e[35m"
$Reset = "`e[0m"

function Write-ColorOutput {
    param([string]$Message, [string]$Color = $Reset)
    Write-Host "${Color}${Message}${Reset}"
}

function Write-Header {
    param([string]$Title)
    Write-ColorOutput "`n$('=' * 60)" $Cyan
    Write-ColorOutput "  $Title" $Cyan
    Write-ColorOutput "$('=' * 60)" $Cyan
}

function Test-Prerequisites {
    Write-Header "Checking Prerequisites"
    
    # Check Python environment
    if (-not $env:VIRTUAL_ENV) {
        Write-ColorOutput "⚠️  Warning: Not in a virtual environment" $Yellow
        Write-ColorOutput "   Consider running: python -m venv venv && .\venv\Scripts\Activate.ps1" $Yellow
    } else {
        Write-ColorOutput "✅ Virtual environment active: $env:VIRTUAL_ENV" $Green
    }
    
    # Check Python version
    $pythonVersion = python --version 2>&1
    Write-ColorOutput "🐍 Python version: $pythonVersion" $Blue
    
    # Check required packages
    $requiredPackages = @("pytest", "pytest-asyncio", "pytest-cov", "httpx", "jwt")
    foreach ($package in $requiredPackages) {
        try {
            python -c "import $package" 2>$null
            Write-ColorOutput "✅ $package is installed" $Green
        } catch {
            Write-ColorOutput "❌ $package is missing. Installing..." $Red
            python -m pip install $package
        }
    }
    
    # Check if services are available (optional)
    Write-ColorOutput "🔍 Checking service availability..." $Blue
    
    $services = @(
        @{Name = "Account Service"; Url = "http://localhost:8080/actuator/health"; Port = 8080},
        @{Name = "Transaction Service"; Url = "http://localhost:8081/actuator/health"; Port = 8081}
    )
    
    foreach ($service in $services) {
        try {
            $response = Invoke-WebRequest -Uri $service.Url -TimeoutSec 5 -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) {
                Write-ColorOutput "✅ $($service.Name) is available" $Green
            } else {
                Write-ColorOutput "⚠️  $($service.Name) returned status $($response.StatusCode)" $Yellow
            }
        } catch {
            Write-ColorOutput "⚠️  $($service.Name) is not available (tests will use mocks)" $Yellow
        }
    }
}

function Initialize-TestEnvironment {
    Write-Header "Initializing Test Environment"
    
    # Create output directory
    if (-not (Test-Path $OutputDir)) {
        New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
        Write-ColorOutput "📁 Created output directory: $OutputDir" $Blue
    }
    
    # Set environment variables
    $env:ENVIRONMENT = $Environment
    $env:JWT_SECRET = "AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14="
    $env:ACCOUNT_SERVICE_URL = "http://localhost:8080"
    $env:TRANSACTION_SERVICE_URL = "http://localhost:8081"
    $env:LOG_LEVEL = "INFO"
    
    Write-ColorOutput "🔧 Environment configured for $Environment" $Blue
    
    # Install test dependencies
    Write-ColorOutput "📦 Installing test dependencies..." $Blue
    python -m pip install -r requirements.txt -q
    python -m pip install pytest-html pytest-json-report pytest-xdist -q
}

function Run-SystemValidationTests {
    Write-Header "Running System Validation Tests"
    
    $testSuites = @()
    
    if ($SecurityOnly) {
        $testSuites += @{
            Name = "Security Validation"
            Path = "tests/integration/test_security_validation.py"
            Description = "Security testing and vulnerability assessment"
        }
    } elseif ($IntegrationOnly) {
        $testSuites += @{
            Name = "System Integration"
            Path = "tests/integration/test_system_validation.py"
            Description = "End-to-end system integration tests"
        }
    } else {
        $testSuites += @{
            Name = "System Integration"
            Path = "tests/integration/test_system_validation.py"
            Description = "End-to-end system integration tests"
        }
        $testSuites += @{
            Name = "Security Validation"
            Path = "tests/integration/test_security_validation.py"
            Description = "Security testing and vulnerability assessment"
        }
        $testSuites += @{
            Name = "JWT Compatibility"
            Path = "tests/integration/test_jwt_compatibility.py"
            Description = "JWT authentication flow validation"
        }
        $testSuites += @{
            Name = "MCP Protocol Compliance"
            Path = "tests/e2e/test_mcp_protocol_compliance.py"
            Description = "MCP client integration scenarios"
        }
    }
    
    $allResults = @()
    
    foreach ($suite in $testSuites) {
        Write-ColorOutput "`n🧪 Running: $($suite.Name)" $Magenta
        Write-ColorOutput "   $($suite.Description)" $Blue
        
        # Build pytest command
        $pytestArgs = @(
            $suite.Path
            "--tb=short"
            "--strict-markers"
            "-v"
        )
        
        if ($Verbose) {
            $pytestArgs += "-s"
        }
        
        if ($Coverage) {
            $pytestArgs += @(
                "--cov=src/mcp_financial"
                "--cov-report=html:$OutputDir/coverage-$($suite.Name.Replace(' ', '-').ToLower())"
                "--cov-report=xml:$OutputDir/coverage-$($suite.Name.Replace(' ', '-').ToLower()).xml"
            )
        }
        
        if ($FailFast) {
            $pytestArgs += "-x"
        }
        
        if ($GenerateReport) {
            $reportName = $suite.Name.Replace(' ', '-').ToLower()
            $pytestArgs += @(
                "--html=$OutputDir/$reportName-report.html"
                "--self-contained-html"
                "--json-report"
                "--json-report-file=$OutputDir/$reportName-report.json"
            )
        }
        
        # Run the test suite
        $startTime = Get-Date
        Write-ColorOutput "   Command: python -m pytest $($pytestArgs -join ' ')" $Blue
        
        try {
            python -m pytest @pytestArgs
            $exitCode = $LASTEXITCODE
            $endTime = Get-Date
            $duration = $endTime - $startTime
            
            $result = @{
                Suite = $suite.Name
                ExitCode = $exitCode
                Duration = $duration
                Status = if ($exitCode -eq 0) { "PASSED" } else { "FAILED" }
            }
            
            if ($exitCode -eq 0) {
                Write-ColorOutput "   ✅ $($suite.Name): PASSED ($($duration.TotalSeconds.ToString('F2'))s)" $Green
            } else {
                Write-ColorOutput "   ❌ $($suite.Name): FAILED ($($duration.TotalSeconds.ToString('F2'))s)" $Red
            }
            
        } catch {
            $endTime = Get-Date
            $duration = $endTime - $startTime
            
            $result = @{
                Suite = $suite.Name
                ExitCode = 1
                Duration = $duration
                Status = "ERROR"
                Error = $_.Exception.Message
            }
            
            Write-ColorOutput "   💥 $($suite.Name): ERROR - $($_.Exception.Message)" $Red
        }
        
        $allResults += $result
    }
    
    return $allResults
}

function Run-SpecificValidationScenarios {
    Write-Header "Running Specific Validation Scenarios"
    
    $scenarios = @(
        @{
            Name = "End-to-End Financial Workflow"
            Pattern = "test_end_to_end_financial_workflow"
            Description = "Complete financial workflow validation"
        },
        @{
            Name = "JWT Authentication Flow"
            Pattern = "test_jwt_authentication_flow_validation"
            Description = "JWT authentication across all services"
        },
        @{
            Name = "MCP Client Integration"
            Pattern = "test_mcp_client_integration_scenarios"
            Description = "MCP protocol client integration"
        },
        @{
            Name = "Security Vulnerability Assessment"
            Pattern = "test_vulnerability_assessment"
            Description = "OWASP Top 10 vulnerability testing"
        },
        @{
            Name = "Monitoring Integration"
            Pattern = "test_monitoring_and_alerting_integration"
            Description = "Monitoring and alerting validation"
        },
        @{
            Name = "Disaster Recovery"
            Pattern = "test_disaster_recovery_scenarios"
            Description = "Disaster recovery and failover testing"
        }
    )
    
    $scenarioResults = @()
    
    foreach ($scenario in $scenarios) {
        Write-ColorOutput "`n🎯 Running: $($scenario.Name)" $Yellow
        Write-ColorOutput "   $($scenario.Description)" $Blue
        
        $startTime = Get-Date
        
        try {
            python -m pytest "tests/integration/" -k $scenario.Pattern -v --tb=short
            $exitCode = $LASTEXITCODE
            $endTime = Get-Date
            $duration = $endTime - $startTime
            
            $result = @{
                Scenario = $scenario.Name
                ExitCode = $exitCode
                Duration = $duration
                Status = if ($exitCode -eq 0) { "PASSED" } else { "FAILED" }
            }
            
            if ($exitCode -eq 0) {
                Write-ColorOutput "   ✅ PASSED ($($duration.TotalSeconds.ToString('F2'))s)" $Green
            } else {
                Write-ColorOutput "   ❌ FAILED ($($duration.TotalSeconds.ToString('F2'))s)" $Red
            }
            
        } catch {
            $endTime = Get-Date
            $duration = $endTime - $startTime
            
            $result = @{
                Scenario = $scenario.Name
                ExitCode = 1
                Duration = $duration
                Status = "ERROR"
                Error = $_.Exception.Message
            }
            
            Write-ColorOutput "   💥 ERROR - $($_.Exception.Message)" $Red
        }
        
        $scenarioResults += $result
    }
    
    return $scenarioResults
}

function Generate-ValidationReport {
    param([array]$TestResults, [array]$ScenarioResults = @())
    
    Write-Header "Generating Validation Report"
    
    $reportPath = "$OutputDir/system-validation-report.html"
    $jsonReportPath = "$OutputDir/system-validation-report.json"
    
    # Calculate summary statistics
    $totalTests = $TestResults.Count + $ScenarioResults.Count
    $passedTests = ($TestResults | Where-Object { $_.Status -eq "PASSED" }).Count + 
                   ($ScenarioResults | Where-Object { $_.Status -eq "PASSED" }).Count
    $failedTests = $totalTests - $passedTests
    $totalDuration = ($TestResults | Measure-Object -Property Duration -Sum).Sum.TotalSeconds +
                     ($ScenarioResults | Measure-Object -Property Duration -Sum).Sum.TotalSeconds
    
    $summary = @{
        Timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        Environment = $Environment
        TotalTests = $totalTests
        PassedTests = $passedTests
        FailedTests = $failedTests
        SuccessRate = if ($totalTests -gt 0) { [math]::Round(($passedTests / $totalTests) * 100, 2) } else { 0 }
        TotalDuration = [math]::Round($totalDuration, 2)
        TestSuites = $TestResults
        Scenarios = $ScenarioResults
    }
    
    # Generate JSON report
    $summary | ConvertTo-Json -Depth 10 | Out-File -FilePath $jsonReportPath -Encoding UTF8
    Write-ColorOutput "📄 JSON report: $jsonReportPath" $Blue
    
    # Generate HTML report
    $htmlContent = @"
<!DOCTYPE html>
<html>
<head>
    <title>MCP Financial Server - System Validation Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .header { text-align: center; color: #333; border-bottom: 2px solid #007acc; padding-bottom: 20px; margin-bottom: 30px; }
        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-bottom: 30px; }
        .metric { background: #f8f9fa; padding: 15px; border-radius: 6px; text-align: center; border-left: 4px solid #007acc; }
        .metric-value { font-size: 2em; font-weight: bold; color: #007acc; }
        .metric-label { color: #666; margin-top: 5px; }
        .section { margin-bottom: 30px; }
        .section h2 { color: #333; border-bottom: 1px solid #ddd; padding-bottom: 10px; }
        .test-grid { display: grid; gap: 15px; }
        .test-item { background: #f8f9fa; padding: 15px; border-radius: 6px; border-left: 4px solid #28a745; }
        .test-item.failed { border-left-color: #dc3545; }
        .test-item.error { border-left-color: #ffc107; }
        .test-name { font-weight: bold; color: #333; }
        .test-status { float: right; padding: 4px 8px; border-radius: 4px; color: white; font-size: 0.9em; }
        .status-passed { background-color: #28a745; }
        .status-failed { background-color: #dc3545; }
        .status-error { background-color: #ffc107; }
        .test-duration { color: #666; font-size: 0.9em; }
        .footer { text-align: center; color: #666; margin-top: 30px; padding-top: 20px; border-top: 1px solid #ddd; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🧪 MCP Financial Server</h1>
            <h2>System Validation Report</h2>
            <p>Generated on $($summary.Timestamp)</p>
        </div>
        
        <div class="summary">
            <div class="metric">
                <div class="metric-value">$($summary.TotalTests)</div>
                <div class="metric-label">Total Tests</div>
            </div>
            <div class="metric">
                <div class="metric-value">$($summary.PassedTests)</div>
                <div class="metric-label">Passed</div>
            </div>
            <div class="metric">
                <div class="metric-value">$($summary.FailedTests)</div>
                <div class="metric-label">Failed</div>
            </div>
            <div class="metric">
                <div class="metric-value">$($summary.SuccessRate)%</div>
                <div class="metric-label">Success Rate</div>
            </div>
            <div class="metric">
                <div class="metric-value">${($summary.TotalDuration)}s</div>
                <div class="metric-label">Total Duration</div>
            </div>
        </div>
        
        <div class="section">
            <h2>📋 Test Suites</h2>
            <div class="test-grid">
"@
    
    foreach ($result in $TestResults) {
        $statusClass = $result.Status.ToLower()
        $statusColor = switch ($result.Status) {
            "PASSED" { "status-passed" }
            "FAILED" { "status-failed" }
            default { "status-error" }
        }
        
        $htmlContent += @"
                <div class="test-item $statusClass">
                    <div class="test-name">$($result.Suite)</div>
                    <span class="test-status $statusColor">$($result.Status)</span>
                    <div class="test-duration">Duration: $($result.Duration.TotalSeconds.ToString('F2'))s</div>
                </div>
"@
    }
    
    $htmlContent += @"
            </div>
        </div>
        
        <div class="section">
            <h2>🎯 Validation Scenarios</h2>
            <div class="test-grid">
"@
    
    foreach ($result in $ScenarioResults) {
        $statusClass = $result.Status.ToLower()
        $statusColor = switch ($result.Status) {
            "PASSED" { "status-passed" }
            "FAILED" { "status-failed" }
            default { "status-error" }
        }
        
        $htmlContent += @"
                <div class="test-item $statusClass">
                    <div class="test-name">$($result.Scenario)</div>
                    <span class="test-status $statusColor">$($result.Status)</span>
                    <div class="test-duration">Duration: $($result.Duration.TotalSeconds.ToString('F2'))s</div>
                </div>
"@
    }
    
    $htmlContent += @"
            </div>
        </div>
        
        <div class="footer">
            <p>MCP Financial Server System Validation - Environment: $Environment</p>
        </div>
    </div>
</body>
</html>
"@
    
    $htmlContent | Out-File -FilePath $reportPath -Encoding UTF8
    Write-ColorOutput "📄 HTML report: $reportPath" $Blue
    
    return $summary
}

function Show-ValidationSummary {
    param([hashtable]$Summary)
    
    Write-Header "Validation Summary"
    
    Write-ColorOutput "📊 Test Results:" $Blue
    Write-ColorOutput "   Total Tests: $($Summary.TotalTests)" $Blue
    Write-ColorOutput "   Passed: $($Summary.PassedTests)" $Green
    Write-ColorOutput "   Failed: $($Summary.FailedTests)" $(if ($Summary.FailedTests -gt 0) { $Red } else { $Green })
    Write-ColorOutput "   Success Rate: $($Summary.SuccessRate)%" $(if ($Summary.SuccessRate -ge 80) { $Green } elseif ($Summary.SuccessRate -ge 60) { $Yellow } else { $Red })
    Write-ColorOutput "   Total Duration: $($Summary.TotalDuration)s" $Blue
    
    if ($Summary.FailedTests -eq 0) {
        Write-ColorOutput "`n🎉 All system validation tests passed!" $Green
        Write-ColorOutput "   The MCP Financial Server is ready for deployment." $Green
    } else {
        Write-ColorOutput "`n⚠️  Some validation tests failed." $Yellow
        Write-ColorOutput "   Please review the test results and fix issues before deployment." $Yellow
    }
    
    # Show output files
    Write-ColorOutput "`n📁 Output Files:" $Blue
    Get-ChildItem $OutputDir -File | ForEach-Object {
        Write-ColorOutput "   $($_.Name)" $Cyan
    }
}

# Main execution
try {
    Write-ColorOutput "🚀 MCP Financial Server - System Validation" $Magenta
    Write-ColorOutput "=============================================" $Magenta
    
    # Change to script directory
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    Set-Location $scriptDir
    
    # Check prerequisites
    Test-Prerequisites
    
    # Initialize test environment
    Initialize-TestEnvironment
    
    # Run validation tests
    $testResults = Run-SystemValidationTests
    
    # Run specific scenarios if not running specific test types
    $scenarioResults = @()
    if (-not $SecurityOnly -and -not $IntegrationOnly) {
        $scenarioResults = Run-SpecificValidationScenarios
    }
    
    # Generate report if requested
    if ($GenerateReport) {
        $summary = Generate-ValidationReport -TestResults $testResults -ScenarioResults $scenarioResults
    } else {
        $summary = @{
            TotalTests = $testResults.Count + $scenarioResults.Count
            PassedTests = ($testResults | Where-Object { $_.Status -eq "PASSED" }).Count + 
                         ($scenarioResults | Where-Object { $_.Status -eq "PASSED" }).Count
            FailedTests = ($testResults | Where-Object { $_.Status -ne "PASSED" }).Count + 
                         ($scenarioResults | Where-Object { $_.Status -ne "PASSED" }).Count
            SuccessRate = 0
            TotalDuration = ($testResults | Measure-Object -Property Duration -Sum).Sum.TotalSeconds +
                           ($scenarioResults | Measure-Object -Property Duration -Sum).Sum.TotalSeconds
        }
        
        if ($summary.TotalTests -gt 0) {
            $summary.SuccessRate = [math]::Round(($summary.PassedTests / $summary.TotalTests) * 100, 2)
        }
    }
    
    # Show summary
    Show-ValidationSummary -Summary $summary
    
    # Exit with appropriate code
    $exitCode = if ($summary.FailedTests -eq 0) { 0 } else { 1 }
    exit $exitCode
    
} catch {
    Write-ColorOutput "❌ Error during system validation: $_" $Red
    Write-ColorOutput "Stack trace: $($_.ScriptStackTrace)" $Red
    exit 1
}