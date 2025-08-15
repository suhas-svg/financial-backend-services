# Performance Testing Script for Account Service (PowerShell)
# This script runs all performance tests and generates reports

param(
    [string]$BaseUrl = "http://localhost:8080"
)

# Configuration
$ResultsDir = "results"
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"

Write-Host "=== Account Service Performance Testing ===" -ForegroundColor Blue
Write-Host "Base URL: $BaseUrl"
Write-Host "Timestamp: $Timestamp"
Write-Host ""

# Check if k6 is installed
try {
    $null = Get-Command k6 -ErrorAction Stop
    Write-Host "k6 found!" -ForegroundColor Green
} catch {
    Write-Host "Error: k6 is not installed. Please install k6 first." -ForegroundColor Red
    Write-Host "Visit: https://k6.io/docs/getting-started/installation/"
    exit 1
}

# Check if the service is running
Write-Host "Checking if service is running..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 10
    if ($response.StatusCode -eq 200) {
        Write-Host "Service is running!" -ForegroundColor Green
    } else {
        throw "Service returned status code: $($response.StatusCode)"
    }
} catch {
    Write-Host "Error: Service is not running at $BaseUrl" -ForegroundColor Red
    Write-Host "Please start the account service first."
    exit 1
}

Write-Host ""

# Create results directory
if (!(Test-Path $ResultsDir)) {
    New-Item -ItemType Directory -Path $ResultsDir | Out-Null
}

# Function to run a test
function Run-Test {
    param(
        [string]$TestName,
        [string]$TestFile,
        [string]$Description
    )
    
    Write-Host "Running $TestName..." -ForegroundColor Blue
    Write-Host "Description: $Description"
    Write-Host ""
    
    $ResultFile = "$ResultsDir\${TestName}_${Timestamp}.json"
    $SummaryFile = "$ResultsDir\${TestName}_${Timestamp}_summary.txt"
    
    # Run k6 test with JSON output
    try {
        $env:BASE_URL = $BaseUrl
        $output = & k6 run --out "json=$ResultFile" "$TestFile" 2>&1
        $output | Out-File -FilePath $SummaryFile -Encoding UTF8
        
        Write-Host "✓ $TestName completed successfully" -ForegroundColor Green
        
        # Extract key metrics from the output
        Write-Host "Key Metrics:" -ForegroundColor Yellow
        $output | Where-Object { $_ -match "(http_req_duration|http_req_failed|errors)" } | Select-Object -Last 10
        Write-Host ""
        
        return $true
    } catch {
        Write-Host "✗ $TestName failed" -ForegroundColor Red
        Write-Host "Error: $_"
        Write-Host ""
        return $false
    }
}

# Run Load Test
$loadTestSuccess = Run-Test -TestName "load-test" -TestFile "load-test.js" -Description "Standard load test with gradual ramp-up to 100 concurrent users"

# Wait between tests
Write-Host "Waiting 30 seconds before next test..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Run Stress Test
$stressTestSuccess = Run-Test -TestName "stress-test" -TestFile "stress-test.js" -Description "Stress test to find system breaking point (up to 500 users)"

# Wait between tests
Write-Host "Waiting 60 seconds before next test..." -ForegroundColor Yellow
Start-Sleep -Seconds 60

# Run Spike Test
$spikeTestSuccess = Run-Test -TestName "spike-test" -TestFile "spike-test.js" -Description "Spike test with sudden load increases to test system resilience"

# Generate summary report
Write-Host "=== Generating Summary Report ===" -ForegroundColor Blue
$ReportFile = "$ResultsDir\performance_report_${Timestamp}.md"

$reportContent = @"
# Performance Test Report

**Date:** $(Get-Date)
**Service:** Account Service
**Base URL:** $BaseUrl

## Test Summary

### Load Test
- **Purpose:** Validate system performance under expected load
- **Max Users:** 100 concurrent users
- **Duration:** ~6 minutes
- **Status:** $(if ($loadTestSuccess) { "✓ Passed" } else { "✗ Failed" })
- **Results:** See load-test_${Timestamp}_summary.txt

### Stress Test
- **Purpose:** Find system breaking point
- **Max Users:** 500 concurrent users
- **Duration:** ~15 minutes
- **Status:** $(if ($stressTestSuccess) { "✓ Passed" } else { "✗ Failed" })
- **Results:** See stress-test_${Timestamp}_summary.txt

### Spike Test
- **Purpose:** Test system resilience to sudden load spikes
- **Max Users:** 300 concurrent users (sudden spikes)
- **Duration:** ~3 minutes
- **Status:** $(if ($spikeTestSuccess) { "✓ Passed" } else { "✗ Failed" })
- **Results:** See spike-test_${Timestamp}_summary.txt

## Key Performance Indicators

### Response Time Thresholds
- Load Test: 95th percentile < 500ms
- Stress Test: 95th percentile < 1000ms
- Spike Test: 95th percentile < 2000ms

### Error Rate Thresholds
- Load Test: < 1%
- Stress Test: < 5%
- Spike Test: < 10%

## Recommendations

1. **Monitor Response Times:** Ensure 95th percentile response times stay within thresholds
2. **Error Rate Monitoring:** Set up alerts for error rates exceeding thresholds
3. **Resource Utilization:** Monitor CPU, memory, and database connections during peak load
4. **Database Performance:** Consider connection pooling optimization if database is the bottleneck
5. **Caching Strategy:** Implement caching for frequently accessed data to improve response times

## Files Generated
- Raw results: *_${Timestamp}.json
- Test summaries: *_${Timestamp}_summary.txt
- This report: performance_report_${Timestamp}.md

"@

$reportContent | Out-File -FilePath $ReportFile -Encoding UTF8

Write-Host "Performance testing completed!" -ForegroundColor Green
Write-Host "Results saved in: $ResultsDir" -ForegroundColor Blue
Write-Host "Summary report: $ReportFile" -ForegroundColor Blue
Write-Host ""

# Display final summary
Write-Host "=== Final Summary ===" -ForegroundColor Yellow
Write-Host "All performance tests have been executed."
Write-Host "Check the results directory for detailed reports and metrics."
Write-Host "Use the JSON files for detailed analysis and the summary files for quick overview."
Write-Host ""
Write-Host "Happy performance testing! 🚀" -ForegroundColor Green