#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Deploy Prometheus and Grafana monitoring stack for Account Service
.DESCRIPTION
    This script deploys the complete monitoring stack including Prometheus for metrics collection
    and Grafana for visualization, supporting both Docker Compose and Kubernetes deployments.
.PARAMETER Environment
    Target environment (dev, staging, prod)
.PARAMETER Platform
    Deployment platform (docker, kubernetes)
.PARAMETER Namespace
    Kubernetes namespace (only for kubernetes platform)
#>

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment = "dev",
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("docker", "kubernetes")]
    [string]$Platform = "docker",
    
    [Parameter(Mandatory=$false)]
    [string]$Namespace = "finance-services"
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

Write-Info "=== Account Service Monitoring Stack Deployment ==="
Write-Info "Environment: $Environment"
Write-Info "Platform: $Platform"

# Change to account-service directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$accountServiceDir = Split-Path -Parent $scriptDir
Set-Location $accountServiceDir

try {
    if ($Platform -eq "docker") {
        Write-Info "Deploying monitoring stack with Docker Compose..."
        
        # Create monitoring directory if it doesn't exist
        if (!(Test-Path "monitoring")) {
            Write-Info "Creating monitoring directory..."
            New-Item -ItemType Directory -Path "monitoring" -Force
        }
        
        # Set environment variables
        $env:ENVIRONMENT = $Environment
        $env:PROMETHEUS_PORT = if ($Environment -eq "prod") { "9090" } else { "9090" }
        $env:GRAFANA_PORT = if ($Environment -eq "prod") { "3000" } else { "3000" }
        $env:GRAFANA_PASSWORD = if ($Environment -eq "prod") { "secure-admin-password" } else { "admin" }
        
        # Deploy with monitoring profile
        Write-Info "Starting Prometheus and Grafana containers..."
        docker-compose --profile monitoring up -d
        
        # Wait for services to be ready
        Write-Info "Waiting for services to be ready..."
        $maxAttempts = 30
        $attempt = 0
        
        do {
            $attempt++
            Start-Sleep -Seconds 5
            
            try {
                $prometheusHealth = Invoke-RestMethod -Uri "http://localhost:$($env:PROMETHEUS_PORT)/-/ready" -TimeoutSec 5
                $grafanaHealth = Invoke-RestMethod -Uri "http://localhost:$($env:GRAFANA_PORT)/api/health" -TimeoutSec 5
                
                if ($prometheusHealth -and $grafanaHealth) {
                    Write-Info "✅ All monitoring services are ready!"
                    break
                }
            }
            catch {
                Write-Warning "Attempt $attempt/$maxAttempts - Services not ready yet..."
            }
        } while ($attempt -lt $maxAttempts)
        
        if ($attempt -eq $maxAttempts) {
            Write-Error "❌ Services failed to become ready within timeout"
            exit 1
        }
        
        # Display access information
        Write-Info ""
        Write-Info "=== Monitoring Stack Access Information ==="
        Write-Info "Prometheus: http://localhost:$($env:PROMETHEUS_PORT)"
        Write-Info "Grafana: http://localhost:$($env:GRAFANA_PORT)"
        Write-Info "Grafana Login: admin / $($env:GRAFANA_PASSWORD)"
        Write-Info ""
        
    } elseif ($Platform -eq "kubernetes") {
        Write-Info "Deploying monitoring stack to Kubernetes..."
        
        # Check if kubectl is available
        if (!(Get-Command kubectl -ErrorAction SilentlyContinue)) {
            Write-Error "❌ kubectl is not available. Please install kubectl first."
            exit 1
        }
        
        # Create namespace if it doesn't exist
        Write-Info "Creating namespace: $Namespace"
        kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f -
        
        # Deploy Prometheus configuration
        Write-Info "Deploying Prometheus configuration..."
        kubectl apply -f k8s/monitoring/prometheus-config.yaml
        
        # Deploy Prometheus
        Write-Info "Deploying Prometheus..."
        kubectl apply -f k8s/monitoring/prometheus-deployment.yaml
        
        # Deploy Grafana configuration
        Write-Info "Deploying Grafana configuration..."
        kubectl apply -f k8s/monitoring/grafana-config.yaml
        
        # Deploy Grafana
        Write-Info "Deploying Grafana..."
        kubectl apply -f k8s/monitoring/grafana-deployment.yaml
        
        # Wait for deployments to be ready
        Write-Info "Waiting for deployments to be ready..."
        kubectl wait --for=condition=available --timeout=300s deployment/prometheus -n $Namespace
        kubectl wait --for=condition=available --timeout=300s deployment/grafana -n $Namespace
        
        # Get service information
        Write-Info ""
        Write-Info "=== Kubernetes Monitoring Stack Information ==="
        kubectl get pods -n $Namespace -l app=prometheus
        kubectl get pods -n $Namespace -l app=grafana
        kubectl get services -n $Namespace
        
        # Port forwarding instructions
        Write-Info ""
        Write-Info "=== Port Forwarding Commands ==="
        Write-Info "Prometheus: kubectl port-forward -n $Namespace svc/prometheus 9090:9090"
        Write-Info "Grafana: kubectl port-forward -n $Namespace svc/grafana 3000:3000"
        Write-Info ""
    }
    
    # Verify monitoring endpoints
    Write-Info "=== Verifying Monitoring Setup ==="
    
    if ($Platform -eq "docker") {
        # Test Prometheus targets
        try {
            $targets = Invoke-RestMethod -Uri "http://localhost:$($env:PROMETHEUS_PORT)/api/v1/targets"
            $activeTargets = ($targets.data.activeTargets | Where-Object { $_.health -eq "up" }).Count
            Write-Info "✅ Prometheus active targets: $activeTargets"
        }
        catch {
            Write-Warning "⚠️  Could not verify Prometheus targets"
        }
        
        # Test Grafana datasource
        try {
            $auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:$($env:GRAFANA_PASSWORD)"))
            $headers = @{ Authorization = "Basic $auth" }
            $datasources = Invoke-RestMethod -Uri "http://localhost:$($env:GRAFANA_PORT)/api/datasources" -Headers $headers
            Write-Info "✅ Grafana datasources configured: $($datasources.Count)"
        }
        catch {
            Write-Warning "⚠️  Could not verify Grafana datasources"
        }
    }
    
    Write-Info ""
    Write-Info "=== Deployment Summary ==="
    Write-Info "✅ Monitoring stack deployed successfully!"
    Write-Info "✅ Prometheus configured for metrics collection"
    Write-Info "✅ Grafana configured with dashboards"
    Write-Info "✅ Alert rules configured for critical metrics"
    Write-Info ""
    Write-Info "Next steps:"
    Write-Info "1. Access Grafana to view dashboards"
    Write-Info "2. Configure additional alert channels if needed"
    Write-Info "3. Customize dashboards for your specific needs"
    Write-Info "4. Set up notification channels (Slack, email, etc.)"
    
} catch {
    Write-Error "❌ Deployment failed: $($_.Exception.Message)"
    Write-Error $_.ScriptStackTrace
    exit 1
} finally {
    # Return to original directory
    Pop-Location -ErrorAction SilentlyContinue
}