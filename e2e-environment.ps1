# E2E Environment Management Script for Windows PowerShell
# This script provides orchestration for the E2E testing environment

param(
    [Parameter(Mandatory=$true, Position=0)]
    [ValidateSet("start", "stop", "restart", "reset", "cleanup", "status", "logs", "validate", "setup-data")]
    [string]$Command,
    
    [Parameter(Position=1)]
    [string]$ServiceName = "",
    
    [switch]$PruneVolumes
)

# Configuration
$ComposeFile = "docker-compose-e2e.yml"
$NetworkName = "e2e-network"
$MaxWaitTime = 300  # 5 minutes
$HealthCheckInterval = 10

# Colors for output
$Colors = @{
    Red = "Red"
    Green = "Green"
    Yellow = "Yellow"
    Blue = "Blue"
    White = "White"
}

# Logging functions
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor $Colors.Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor $Colors.Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor $Colors.Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor $Colors.Red
}

# Function to check if Docker is running
function Test-Docker {
    Write-Info "Checking Docker availability..."
    try {
        $null = docker info 2>$null
        Write-Success "Docker is available"
        return $true
    }
    catch {
        Write-Error "Docker is not running or not accessible"
        return $false
    }
}

# Function to check if Docker Compose is available
function Test-DockerCompose {
    Write-Info "Checking Docker Compose availability..."
    try {
        $null = docker compose version 2>$null
        Write-Success "Docker Compose is available"
        return $true
    }
    catch {
        Write-Error "Docker Compose is not available"
        return $false
    }
}

# Function to wait for service health
function Wait-ForServiceHealth {
    param([string]$ServiceName)
    
    $maxAttempts = [math]::Floor($MaxWaitTime / $HealthCheckInterval)
    $attempt = 1
    
    Write-Info "Waiting for $ServiceName to become healthy..."
    
    while ($attempt -le $maxAttempts) {
        try {
            $services = docker compose -f $ComposeFile ps --format json | ConvertFrom-Json
            $service = $services | Where-Object { $_.Service -eq $ServiceName }
            
            if ($service -and $service.Health -eq "healthy") {
                Write-Success "$ServiceName is healthy"
                return $true
            }
        }
        catch {
            # Continue waiting
        }
        
        Write-Info "Attempt $attempt/$maxAttempts`: $ServiceName not ready yet, waiting $($HealthCheckInterval)s..."
        Start-Sleep -Seconds $HealthCheckInterval
        $attempt++
    }
    
    Write-Error "$ServiceName failed to become healthy within $($MaxWaitTime)s"
    return $false
}

# Function to wait for all core services
function Wait-ForCoreServices {
    Write-Info "Waiting for core infrastructure services..."
    
    # Wait for databases
    if (-not (Wait-ForServiceHealth "postgres-account-e2e")) { return $false }
    if (-not (Wait-ForServiceHealth "postgres-transaction-e2e")) { return $false }
    
    # Wait for cache
    if (-not (Wait-ForServiceHealth "redis-e2e")) { return $false }
    
    Write-Info "Waiting for application services..."
    
    # Wait for applications
    if (-not (Wait-ForServiceHealth "account-service-e2e")) { return $false }
    if (-not (Wait-ForServiceHealth "transaction-service-e2e")) { return $false }
    
    Write-Success "All core services are healthy"
    return $true
}

# Function to validate environment
function Test-Environment {
    Write-Info "Validating E2E environment..."
    
    try {
        $result = docker compose -f $ComposeFile --profile validate up --exit-code-from e2e-validator e2e-validator
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Environment validation passed"
            return $true
        }
        else {
            Write-Error "Environment validation failed"
            return $false
        }
    }
    catch {
        Write-Error "Environment validation failed: $($_.Exception.Message)"
        return $false
    }
}

# Function to setup test data
function Set-TestData {
    Write-Info "Setting up E2E test data..."
    
    try {
        $result = docker compose -f $ComposeFile --profile setup-data up --exit-code-from e2e-data-setup e2e-data-setup
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Test data setup completed"
        }
        else {
            Write-Warning "Test data setup failed or no test data files found"
        }
        return $true  # Non-critical failure
    }
    catch {
        Write-Warning "Test data setup failed: $($_.Exception.Message)"
        return $true  # Non-critical failure
    }
}

# Function to cleanup environment
function Clear-Environment {
    param([bool]$PruneVolumes = $false)
    
    Write-Info "Cleaning up E2E environment..."
    
    # Run cleanup service
    try {
        docker compose -f $ComposeFile --profile cleanup up --exit-code-from e2e-cleanup e2e-cleanup 2>$null
    }
    catch {
        # Ignore cleanup errors
    }
    
    # Stop and remove containers
    try {
        docker compose -f $ComposeFile down --volumes --remove-orphans 2>$null
    }
    catch {
        # Ignore errors
    }
    
    # Remove custom network if it exists
    try {
        docker network rm $NetworkName 2>$null
    }
    catch {
        # Ignore if network doesn't exist
    }
    
    # Prune unused volumes (with confirmation)
    if ($PruneVolumes) {
        Write-Warning "Pruning all unused Docker volumes..."
        docker volume prune -f
    }
    
    Write-Success "Environment cleanup completed"
}

# Function to show service status
function Show-Status {
    Write-Info "E2E Environment Status:"
    Write-Host ""
    
    try {
        docker compose -f $ComposeFile ps --format table
        Write-Host ""
        Write-Info "Service Health Status:"
        
        $services = docker compose -f $ComposeFile ps --format json | ConvertFrom-Json
        foreach ($service in $services) {
            $health = if ($service.Health) { $service.Health } else { "no health check" }
            Write-Host "$($service.Service): $($service.State) ($health)"
        }
    }
    catch {
        Write-Warning "No services are currently running"
    }
}

# Function to show logs
function Show-Logs {
    param([string]$ServiceName = "")
    
    if ($ServiceName) {
        Write-Info "Showing logs for $ServiceName..."
        docker compose -f $ComposeFile logs -f $ServiceName
    }
    else {
        Write-Info "Showing logs for all services..."
        docker compose -f $ComposeFile logs -f
    }
}

# Function to start environment
function Start-Environment {
    Write-Info "Starting E2E testing environment..."
    
    # Check prerequisites
    if (-not (Test-Docker)) { exit 1 }
    if (-not (Test-DockerCompose)) { exit 1 }
    
    # Start core services
    Write-Info "Starting infrastructure services..."
    docker compose -f $ComposeFile up -d postgres-account-e2e postgres-transaction-e2e redis-e2e
    
    # Wait for infrastructure
    if (-not (Wait-ForServiceHealth "postgres-account-e2e")) { exit 1 }
    if (-not (Wait-ForServiceHealth "postgres-transaction-e2e")) { exit 1 }
    if (-not (Wait-ForServiceHealth "redis-e2e")) { exit 1 }
    
    # Start application services
    Write-Info "Starting application services..."
    docker compose -f $ComposeFile up -d account-service-e2e transaction-service-e2e
    
    # Wait for applications
    if (-not (Wait-ForServiceHealth "account-service-e2e")) { exit 1 }
    if (-not (Wait-ForServiceHealth "transaction-service-e2e")) { exit 1 }
    
    # Validate environment
    if (-not (Test-Environment)) { exit 1 }
    
    # Setup test data
    Set-TestData
    
    Write-Success "E2E environment is ready for testing!"
    Write-Host ""
    Write-Info "Service URLs:"
    Write-Host "  Account Service: http://localhost:8083"
    Write-Host "  Transaction Service: http://localhost:8082"
    Write-Host "  Account Database: localhost:5434"
    Write-Host "  Transaction Database: localhost:5435"
    Write-Host "  Redis Cache: localhost:6380"
}

# Function to stop environment
function Stop-Environment {
    Write-Info "Stopping E2E testing environment..."
    docker compose -f $ComposeFile down
    Write-Success "E2E environment stopped"
}

# Function to restart environment
function Restart-Environment {
    Write-Info "Restarting E2E testing environment..."
    Stop-Environment
    Start-Environment
}

# Function to reset environment (full cleanup and restart)
function Reset-Environment {
    Write-Info "Resetting E2E testing environment..."
    Clear-Environment
    Start-Environment
}

# Main script logic
switch ($Command) {
    "start" {
        Start-Environment
    }
    "stop" {
        Stop-Environment
    }
    "restart" {
        Restart-Environment
    }
    "reset" {
        Reset-Environment
    }
    "cleanup" {
        Clear-Environment -PruneVolumes $PruneVolumes
    }
    "status" {
        Show-Status
    }
    "logs" {
        Show-Logs -ServiceName $ServiceName
    }
    "validate" {
        Test-Environment
    }
    "setup-data" {
        Set-TestData
    }
    default {
        Write-Host "Usage: .\e2e-environment.ps1 <command> [options]"
        Write-Host ""
        Write-Host "Commands:"
        Write-Host "  start       - Start the E2E testing environment"
        Write-Host "  stop        - Stop the E2E testing environment"
        Write-Host "  restart     - Restart the E2E testing environment"
        Write-Host "  reset       - Full reset (cleanup + start)"
        Write-Host "  cleanup     - Clean up environment and resources"
        Write-Host "              - Use -PruneVolumes to also remove unused volumes"
        Write-Host "  status      - Show current status of all services"
        Write-Host "  logs        - Show logs for all services"
        Write-Host "              - Use 'logs <service-name>' for specific service"
        Write-Host "  validate    - Validate environment readiness"
        Write-Host "  setup-data  - Setup test data"
        Write-Host ""
        Write-Host "Examples:"
        Write-Host "  .\e2e-environment.ps1 start"
        Write-Host "  .\e2e-environment.ps1 logs account-service-e2e"
        Write-Host "  .\e2e-environment.ps1 cleanup -PruneVolumes"
        exit 1
    }
}