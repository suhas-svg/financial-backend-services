#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Test health monitoring endpoints
.DESCRIPTION
    Simple script to test the health monitoring endpoints are accessible
#>

param(
    [Parameter(Mandatory=$false)]
    [string]$ServiceUrl = "http://localhost:8080"
)

function Write-Info { Write-Host "[INFO] $args" -ForegroundColor Green }
function Write-Error { Write-Host "[ERROR] $args" -ForegroundColor Red }
function Write-Success { Write-Host "[SUCCESS] $args" -ForegroundColor Cyan }

Write-Info "Testing health endpoints at: $ServiceUrl"

# Test basic health endpoint
Write-Info "Testing /actuator/health..."
try {
    $response = Invoke-RestMethod -Uri "$ServiceUrl/actuator/health" -TimeoutSec 10
    Write-Success "✅ /actuator/health - Status: $($response.status)"
}
catch {
    Write-Error "❌ /actuator/health failed: $($_.Exception.Message)"
}

# Test Prometheus metrics endpoint
Write-Info "Testing /actuator/prometheus..."
try {
    $response = Invoke-RestMethod -Uri "$ServiceUrl/actuator/prometheus" -TimeoutSec 10
    if ($response -and $response.Length -gt 0) {
        Write-Success "✅ /actuator/prometheus - Metrics available"
    }
}
catch {
    Write-Error "❌ /actuator/prometheus failed: $($_.Exception.Message)"
}

# Test custom health status endpoint
Write-Info "Testing /api/health/status..."
try {
    $response = Invoke-RestMethod -Uri "$ServiceUrl/api/health/status" -TimeoutSec 10
    Write-Success "✅ /api/health/status - Status: $($response.status)"
}
catch {
    Write-Error "❌ /api/health/status failed: $($_.Exception.Message)"
}

# Test deployment info endpoint
Write-Info "Testing /api/health/deployment..."
try {
    $response = Invoke-RestMethod -Uri "$ServiceUrl/api/health/deployment" -TimeoutSec 10
    Write-Success "✅ /api/health/deployment - Version: $($response.version)"
}
catch {
    Write-Error "❌ /api/health/deployment failed: $($_.Exception.Message)"
}

# Test metrics summary endpoint
Write-Info "Testing /api/health/metrics..."
try {
    $response = Invoke-RestMethod -Uri "$ServiceUrl/api/health/metrics" -TimeoutSec 10
    Write-Success "✅ /api/health/metrics - Available"
}
catch {
    Write-Error "❌ /api/health/metrics failed: $($_.Exception.Message)"
}

# Test deployment tracking endpoint (POST)
Write-Info "Testing POST /api/health/deployment..."
try {
    $body = "status=success&duration=5000"
    $response = Invoke-RestMethod -Uri "$ServiceUrl/api/health/deployment" -Method POST -Body $body -ContentType "application/x-www-form-urlencoded" -TimeoutSec 10
    Write-Success "✅ POST /api/health/deployment - Response: $($response.message)"
}
catch {
    Write-Error "❌ POST /api/health/deployment failed: $($_.Exception.Message)"
}

# Test manual health check endpoint (POST)
Write-Info "Testing POST /api/health/check..."
try {
    $response = Invoke-RestMethod -Uri "$ServiceUrl/api/health/check" -Method POST -TimeoutSec 10
    Write-Success "✅ POST /api/health/check - Healthy: $($response.healthy)"
}
catch {
    Write-Error "❌ POST /api/health/check failed: $($_.Exception.Message)"
}

Write-Info "Health endpoint testing completed!"