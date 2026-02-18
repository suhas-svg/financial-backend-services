# Development Deployment Script for MCP Financial Server (PowerShell)
param(
    [string]$Action = "deploy"
)

# Configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$EnvFile = Join-Path $ProjectDir ".env.dev"

# Colors for output
function Write-Info { param($Message) Write-Host "[INFO] $Message" -ForegroundColor Blue }
function Write-Success { param($Message) Write-Host "[SUCCESS] $Message" -ForegroundColor Green }
function Write-Warning { param($Message) Write-Host "[WARNING] $Message" -ForegroundColor Yellow }
function Write-Error { param($Message) Write-Host "[ERROR] $Message" -ForegroundColor Red }

function Test-Prerequisites {
    Write-Info "Checking prerequisites..."
    
    # Check Docker
    try {
        $dockerVersion = docker --version
        Write-Info "Docker found: $dockerVersion"
    }
    catch {
        Write-Error "Docker is not installed or not in PATH"
        exit 1
    }
    
    # Check Docker Compose
    try {
        $composeVersion = docker-compose --version
        Write-Info "Docker Compose found: $composeVersion"
    }
    catch {
        Write-Error "Docker Compose is not installed or not in PATH"
        exit 1
    }
    
    # Check environment file
    if (-not (Test-Path $EnvFile)) {
        Write-Error "Environment file not found: $EnvFile"
        Write-Info "Copying from example..."
        Copy-Item (Join-Path $ProjectDir ".env.example") $EnvFile
        Write-Warning "Please review and update $EnvFile before running again"
        exit 1
    }
    
    Write-Success "Prerequisites check passed"
}

function Invoke-Cleanup {
    Write-Info "Cleaning up existing containers..."
    Set-Location $ProjectDir
    
    try {
        docker-compose -f docker-compose.dev.yml down --remove-orphans 2>$null
        docker system prune -f --volumes 2>$null
        Write-Success "Cleanup completed"
    }
    catch {
        Write-Warning "Cleanup had some issues, continuing..."
    }
}

function Invoke-Deploy {
    Write-Info "Building and starting development services..."
    Set-Location $ProjectDir
    
    try {
        # Build the application
        Write-Info "Building MCP Financial Server..."
        docker-compose -f docker-compose.dev.yml build --no-cache
        
        # Start services
        Write-Info "Starting services..."
        docker-compose -f docker-compose.dev.yml up -d
        
        Write-Success "Services started successfully"
    }
    catch {
        Write-Error "Deployment failed: $_"
        exit 1
    }
}

function Test-Health {
    Write-Info "Performing health checks..."
    
    $maxAttempts = 30
    $attempt = 1
    
    while ($attempt -le $maxAttempts) {
        Write-Info "Health check attempt $attempt/$maxAttempts..."
        
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8082/health" -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                Write-Success "MCP Financial Server is healthy!"
                return
            }
        }
        catch {
            # Continue trying
        }
        
        if ($attempt -eq $maxAttempts) {
            Write-Error "Health check failed after $maxAttempts attempts"
            Write-Info "Checking container logs..."
            docker-compose -f docker-compose.dev.yml logs mcp-financial-server
            exit 1
        }
        
        Start-Sleep -Seconds 5
        $attempt++
    }
}

function Show-Info {
    Write-Info "Development environment is ready!"
    Write-Host ""
    Write-Host "📊 Service URLs:" -ForegroundColor Cyan
    Write-Host "  • MCP Financial Server: http://localhost:8082"
    Write-Host "  • Health Check: http://localhost:8082/health"
    Write-Host "  • Metrics: http://localhost:9090/metrics"
    Write-Host "  • Prometheus: http://localhost:9091"
    Write-Host "  • Grafana: http://localhost:3000 (admin/admin)"
    Write-Host "  • PostgreSQL: localhost:5432"
    Write-Host "  • Redis: localhost:6379"
    Write-Host ""
    Write-Host "🔧 Useful commands:" -ForegroundColor Cyan
    Write-Host "  • View logs: docker-compose -f docker-compose.dev.yml logs -f"
    Write-Host "  • Stop services: docker-compose -f docker-compose.dev.yml down"
    Write-Host "  • Restart MCP server: docker-compose -f docker-compose.dev.yml restart mcp-financial-server"
    Write-Host ""
}

function Invoke-Main {
    Write-Info "MCP Financial Server - Development Deployment"
    Write-Host "=============================================="
    
    Test-Prerequisites
    Invoke-Cleanup
    Invoke-Deploy
    Test-Health
    Show-Info
    
    Write-Success "Development deployment completed successfully! 🎉"
}

# Handle script interruption
trap {
    Write-Error "Deployment interrupted"
    exit 1
}

# Execute based on action
switch ($Action.ToLower()) {
    "deploy" { Invoke-Main }
    "stop" {
        Set-Location $ProjectDir
        docker-compose -f docker-compose.dev.yml down
        Write-Success "Development environment stopped"
    }
    "logs" {
        Set-Location $ProjectDir
        docker-compose -f docker-compose.dev.yml logs -f
    }
    "restart" {
        Set-Location $ProjectDir
        docker-compose -f docker-compose.dev.yml restart mcp-financial-server
        Write-Success "MCP Financial Server restarted"
    }
    default {
        Write-Host "Usage: .\deploy-dev.ps1 [-Action {deploy|stop|logs|restart}]"
        exit 1
    }
}