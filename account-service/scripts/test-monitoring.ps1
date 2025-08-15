#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Test monitoring and deployment tracking setup
.DESCRIPTION
    This script tests the monitoring stack and deployment tracking functionality
    to ensure everything is working correctly.
.PARAMETER Environment
    Target environment (dev, staging, prod)
.PARAMETER ServiceUrl
    Service URL to test (default: http://localhost:8080)
#>

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment = "dev",
    
    [Parameter(Mandatory=$false)]
    [string]$ServiceUrl = "http://localhost:8080"
)

# Set error action preference
$ErrorActionPreference = "Stop"

# Color functions for output
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    }
    else {
        $input | Write-Output
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

function Write-Info { Write-ColorOutput Green $args }
function Write-Warning { Write-ColorOutput Yellow $args }
function Write-Error { Write-ColorOutput Red $args }
function Write-Success { Write-ColorOutput Cyan $args }

Write-Info "=== Monitoring and Deployment Tracking Test ==="
Write-Info "Environment: $Environment"
Write-Info "Service URL: $ServiceUrl"

# Change to account-service directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$accountServiceDir = Split-Path -Parent $scriptDir
Set-Location $accountServiceDir

$testResults = @{
    "Application Health" = $false
    "Deployment Info" = $false
    "Metrics Endpoint" = $false
    "Custom Metrics" = $false
    "Prometheus Scraping" = $false
    "Grafana Dashboard" = $false
    "Deployment Tracking" = $false
}

try {
    Write-Info ""
    Write-Info "=== Testing Application Health ==="
    
    # Test application health endpoint
    try {
        $healthResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/health/status" -TimeoutSec 10
        if ($healthResponse.status -eq "UP") {
            Write-Success "✅ Application health check passed"
            Write-Info "   Health Score: $($healthResponse.deployment.healthScore)"
            Write-Info "   Version: $($healthResponse.deployment.version)"
            Write-Info "   Uptime: $($healthResponse.deployment.uptimeSeconds)s"
            $testResults["Application Health"] = $true
        } else {
            Write-Warning "⚠️  Application health check returned: $($healthResponse.status)"
        }
    }
    catch {
        Write-Error "❌ Application health check failed: $($_.Exception.Message)"
    }

    Write-Info ""
    Write-Info "=== Testing Deployment Information ==="
    
    # Test deployment info endpoint
    try {
        $deploymentResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/health/deployment" -TimeoutSec 10
        Write-Success "✅ Deployment info endpoint accessible"
        Write-Info "   Version: $($deploymentResponse.version)"
        Write-Info "   Environment: $($deploymentResponse.environment)"
        Write-Info "   Build Time: $($deploymentResponse.buildTime)"
        Write-Info "   Git Commit: $($deploymentResponse.gitCommit)"
        $testResults["Deployment Info"] = $true
    }
    catch {
        Write-Error "❌ Deployment info endpoint failed: $($_.Exception.Message)"
    }

    Write-Info ""
    Write-Info "=== Testing Metrics Endpoint ==="
    
    # Test Prometheus metrics endpoint
    try {
        $metricsResponse = Invoke-RestMethod -Uri "$ServiceUrl/actuator/prometheus" -TimeoutSec 10
        if ($metricsResponse -and $metricsResponse.Length -gt 0) {
            Write-Success "✅ Prometheus metrics endpoint accessible"
            
            # Check for custom metrics
            $customMetrics = @(
                "account_created_count",
                "auth_registration_total",
                "deployment_total",
                "application_uptime_seconds",
                "application_health_score"
            )
            
            $foundMetrics = 0
            foreach ($metric in $customMetrics) {
                if ($metricsResponse -match $metric) {
                    $foundMetrics++
                    Write-Info "   ✓ Found metric: $metric"
                } else {
                    Write-Warning "   ⚠️  Missing metric: $metric"
                }
            }
            
            if ($foundMetrics -gt 0) {
                Write-Success "✅ Custom metrics found ($foundMetrics/$($customMetrics.Count))"
                $testResults["Custom Metrics"] = $true
            }
            
            $testResults["Metrics Endpoint"] = $true
        }
    }
    catch {
        Write-Error "❌ Metrics endpoint failed: $($_.Exception.Message)"
    }

    Write-Info ""
    Write-Info "=== Testing Metrics Summary ==="
    
    # Test metrics summary endpoint
    try {
        $metricsSummaryResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/health/metrics" -TimeoutSec 10
        Write-Success "✅ Metrics summary endpoint accessible"
        Write-Info "   Deployment Total: $($metricsSummaryResponse.deployment_total)"
        Write-Info "   Health Check Total: $($metricsSummaryResponse.health_check_total)"
        Write-Info "   Application Uptime: $($metricsSummaryResponse.application_uptime_seconds)s"
    }
    catch {
        Write-Error "❌ Metrics summary endpoint failed: $($_.Exception.Message)"
    }

    Write-Info ""
    Write-Info "=== Testing Prometheus Integration ==="
    
    # Test Prometheus scraping
    $prometheusUrl = "http://localhost:9090"
    try {
        $prometheusTargets = Invoke-RestMethod -Uri "$prometheusUrl/api/v1/targets" -TimeoutSec 5
        $accountServiceTarget = $prometheusTargets.data.activeTargets | Where-Object { $_.labels.job -eq "account-service" }
        
        if ($accountServiceTarget -and $accountServiceTarget.health -eq "up") {
            Write-Success "✅ Prometheus is scraping account service"
            Write-Info "   Target Health: $($accountServiceTarget.health)"
            Write-Info "   Last Scrape: $($accountServiceTarget.lastScrape)"
            $testResults["Prometheus Scraping"] = $true
        } else {
            Write-Warning "⚠️  Prometheus target not healthy or not found"
        }
    }
    catch {
        Write-Warning "⚠️  Could not connect to Prometheus: $($_.Exception.Message)"
    }

    Write-Info ""
    Write-Info "=== Testing Grafana Integration ==="
    
    # Test Grafana dashboard
    $grafanaUrl = "http://localhost:3000"
    try {
        $grafanaHealth = Invoke-RestMethod -Uri "$grafanaUrl/api/health" -TimeoutSec 5
        if ($grafanaHealth.database -eq "ok") {
            Write-Success "✅ Grafana is accessible"
            
            # Test datasource
            try {
                $auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:admin"))
                $headers = @{ Authorization = "Basic $auth" }
                $datasources = Invoke-RestMethod -Uri "$grafanaUrl/api/datasources" -Headers $headers -TimeoutSec 5
                
                $prometheusDatasource = $datasources | Where-Object { $_.type -eq "prometheus" }
                if ($prometheusDatasource) {
                    Write-Success "✅ Prometheus datasource configured in Grafana"
                    $testResults["Grafana Dashboard"] = $true
                } else {
                    Write-Warning "⚠️  Prometheus datasource not found in Grafana"
                }
            }
            catch {
                Write-Warning "⚠️  Could not verify Grafana datasources: $($_.Exception.Message)"
            }
        }
    }
    catch {
        Write-Warning "⚠️  Could not connect to Grafana: $($_.Exception.Message)"
    }

    Write-Info ""
    Write-Info "=== Testing Deployment Tracking ==="
    
    # Test deployment tracking functionality
    try {
        # Record a test deployment event
        $deploymentData = @{
            status = "success"
            duration = 30000
        }
        
        $deploymentResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/health/deployment" -Method POST -Body $deploymentData -TimeoutSec 10
        
        if ($deploymentResponse.message -match "success") {
            Write-Success "✅ Deployment tracking working"
            Write-Info "   Response: $($deploymentResponse.message)"
            $testResults["Deployment Tracking"] = $true
        }
    }
    catch {
        Write-Error "❌ Deployment tracking failed: $($_.Exception.Message)"
    }

    Write-Info ""
    Write-Info "=== Testing Manual Health Check ==="
    
    # Trigger manual health check
    try {
        $healthCheckResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/health/check" -Method POST -TimeoutSec 10
        
        if ($healthCheckResponse.healthy) {
            Write-Success "✅ Manual health check passed"
            Write-Info "   Health Status: $($healthCheckResponse.healthy)"
        } else {
            Write-Warning "⚠️  Manual health check failed"
        }
    }
    catch {
        Write-Error "❌ Manual health check endpoint failed: $($_.Exception.Message)"
    }

    Write-Info ""
    Write-Info "=== Test Results Summary ==="
    
    $passedTests = 0
    $totalTests = $testResults.Count
    
    foreach ($test in $testResults.GetEnumerator()) {
        $status = if ($test.Value) { "✅ PASS" } else { "❌ FAIL" }
        Write-Info "$status - $($test.Key)"
        if ($test.Value) { $passedTests++ }
    }
    
    Write-Info ""
    Write-Info "=== Overall Results ==="
    Write-Info "Passed: $passedTests/$totalTests tests"
    
    $successRate = [math]::Round(($passedTests / $totalTests) * 100, 1)
    
    if ($successRate -ge 80) {
        Write-Success "🎉 Monitoring setup is working well! ($successRate% success rate)"
    } elseif ($successRate -ge 60) {
        Write-Warning "⚠️  Monitoring setup needs attention ($successRate% success rate)"
    } else {
        Write-Error "❌ Monitoring setup has significant issues ($successRate% success rate)"
    }
    
    Write-Info ""
    Write-Info "=== Next Steps ==="
    if ($testResults["Application Health"] -and $testResults["Metrics Endpoint"]) {
        Write-Info "✓ Core monitoring is working"
    } else {
        Write-Info "• Fix application health and metrics endpoints first"
    }
    
    if (-not $testResults["Prometheus Scraping"]) {
        Write-Info "• Start Prometheus with: docker-compose --profile monitoring up -d"
    }
    
    if (-not $testResults["Grafana Dashboard"]) {
        Write-Info "• Configure Grafana datasource and import dashboards"
    }
    
    Write-Info "• Access Grafana at: http://localhost:3000 (admin/admin)"
    Write-Info "• Access Prometheus at: http://localhost:9090"
    Write-Info "• View metrics at: $ServiceUrl/actuator/prometheus"

} catch {
    Write-Error "❌ Test execution failed: $($_.Exception.Message)"
    Write-Error $_.ScriptStackTrace
    exit 1
} finally {
    # Return to original directory
    Pop-Location -ErrorAction SilentlyContinue
}