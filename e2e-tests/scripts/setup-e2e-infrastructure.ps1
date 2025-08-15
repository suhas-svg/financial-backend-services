# E2E Infrastructure Setup Script
# This script sets up the complete E2E testing infrastructure using Docker Compose

param(
    [switch]$Start,
    [switch]$Stop,
    [switch]$Restart,
    [switch]$Status,
    [switch]$Logs,
    [switch]$Clean,
    [switch]$Validate,
    [string]$Service = ""
)

$ErrorActionPreference = "Stop"

# Configuration
$E2E_COMPOSE_FILE = "../docker-compose-e2e.yml"
$E2E_ENV_FILE = ".env.e2e"
$PROJECT_NAME = "e2e-financial-services"

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

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor Yellow
}

function Write-Info {
    param([string]$Message)
    Write-Host "ℹ️  $Message" -ForegroundColor Blue
}

function Test-DockerCompose {
    try {
        docker-compose --version | Out-Null
        return $true
    }
    catch {
        return $false
    }
}

function Test-Docker {
    try {
        docker --version | Out-Null
        docker info | Out-Null
        return $true
    }
    catch {
        return $false
    }
}

function Start-E2EInfrastructure {
    Write-Header "Starting E2E Infrastructure"
    
    # Check prerequisites
    if (-not (Test-Docker)) {
        Write-Error "Docker is not running or not installed"
        exit 1
    }
    
    if (-not (Test-DockerCompose)) {
        Write-Error "Docker Compose is not installed"
        exit 1
    }
    
    # Check if compose file exists
    if (-not (Test-Path $E2E_COMPOSE_FILE)) {
        Write-Error "E2E Docker Compose file not found: $E2E_COMPOSE_FILE"
        exit 1
    }
    
    Write-Info "Using Docker Compose file: $E2E_COMPOSE_FILE"
    Write-Info "Using project name: $PROJECT_NAME"
    
    try {
        # Start the infrastructure
        Write-Info "Starting E2E infrastructure services..."
        docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME up -d --build
        
        Write-Info "Waiting for services to be healthy..."
        Start-Sleep -Seconds 10
        
        # Wait for databases to be ready
        Write-Info "Waiting for PostgreSQL databases..."
        $maxAttempts = 30
        $attempt = 0
        
        do {
            $attempt++
            Write-Host "." -NoNewline
            
            $accountDbReady = docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME exec -T postgres-account-e2e pg_isready -U e2e_user -d account_db_e2e 2>$null
            $transactionDbReady = docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME exec -T postgres-transaction-e2e pg_isready -U e2e_user -d transaction_db_e2e 2>$null
            
            if ($accountDbReady -and $transactionDbReady) {
                Write-Host ""
                Write-Success "PostgreSQL databases are ready"
                break
            }
            
            Start-Sleep -Seconds 2
        } while ($attempt -lt $maxAttempts)
        
        if ($attempt -ge $maxAttempts) {
            Write-Warning "Databases may not be fully ready, but continuing..."
        }
        
        # Wait for Redis
        Write-Info "Waiting for Redis..."
        $attempt = 0
        do {
            $attempt++
            Write-Host "." -NoNewline
            
            $redisReady = docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME exec -T redis-e2e redis-cli -a e2e_redis_password ping 2>$null
            
            if ($redisReady -eq "PONG") {
                Write-Host ""
                Write-Success "Redis is ready"
                break
            }
            
            Start-Sleep -Seconds 2
        } while ($attempt -lt 15)
        
        # Wait for services
        Write-Info "Waiting for application services..."
        Start-Sleep -Seconds 30
        
        Write-Success "E2E Infrastructure started successfully"
        Write-Info "Services are available at:"
        Write-Info "  Account Service: http://localhost:8083"
        Write-Info "  Transaction Service: http://localhost:8082"
        Write-Info "  Account Database: localhost:5434"
        Write-Info "  Transaction Database: localhost:5435"
        Write-Info "  Redis: localhost:6380"
        
    }
    catch {
        Write-Error "Failed to start E2E infrastructure: $($_.Exception.Message)"
        exit 1
    }
}

function Stop-E2EInfrastructure {
    Write-Header "Stopping E2E Infrastructure"
    
    try {
        docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME down
        Write-Success "E2E Infrastructure stopped successfully"
    }
    catch {
        Write-Error "Failed to stop E2E infrastructure: $($_.Exception.Message)"
        exit 1
    }
}

function Restart-E2EInfrastructure {
    Write-Header "Restarting E2E Infrastructure"
    Stop-E2EInfrastructure
    Start-Sleep -Seconds 5
    Start-E2EInfrastructure
}

function Get-E2EStatus {
    Write-Header "E2E Infrastructure Status"
    
    try {
        docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME ps
    }
    catch {
        Write-Error "Failed to get E2E infrastructure status: $($_.Exception.Message)"
        exit 1
    }
}

function Show-E2ELogs {
    Write-Header "E2E Infrastructure Logs"
    
    try {
        if ($Service) {
            docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME logs -f $Service
        }
        else {
            docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME logs -f
        }
    }
    catch {
        Write-Error "Failed to show E2E infrastructure logs: $($_.Exception.Message)"
        exit 1
    }
}

function Clean-E2EInfrastructure {
    Write-Header "Cleaning E2E Infrastructure"
    
    try {
        # Stop and remove containers, networks, and volumes
        docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME down -v --remove-orphans
        
        # Remove images
        Write-Info "Removing E2E images..."
        docker image rm account-service:e2e-latest 2>$null
        docker image rm transaction-service:e2e-latest 2>$null
        
        # Prune unused volumes and networks
        docker volume prune -f
        docker network prune -f
        
        Write-Success "E2E Infrastructure cleaned successfully"
    }
    catch {
        Write-Error "Failed to clean E2E infrastructure: $($_.Exception.Message)"
        exit 1
    }
}

function Test-E2EInfrastructure {
    Write-Header "Validating E2E Infrastructure"
    
    try {
        # Test Account Service
        Write-Info "Testing Account Service health..."
        $accountHealth = Invoke-RestMethod -Uri "http://localhost:8083/actuator/health" -TimeoutSec 10
        if ($accountHealth.status -eq "UP") {
            Write-Success "Account Service is healthy"
        }
        else {
            Write-Error "Account Service is not healthy: $($accountHealth.status)"
        }
        
        # Test Transaction Service
        Write-Info "Testing Transaction Service health..."
        $transactionHealth = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -TimeoutSec 10
        if ($transactionHealth.status -eq "UP") {
            Write-Success "Transaction Service is healthy"
        }
        else {
            Write-Error "Transaction Service is not healthy: $($transactionHealth.status)"
        }
        
        # Test Database connectivity
        Write-Info "Testing database connectivity..."
        $accountDbTest = docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME exec -T postgres-account-e2e psql -U e2e_user -d account_db_e2e -c "SELECT 1;" 2>$null
        if ($accountDbTest) {
            Write-Success "Account Database is accessible"
        }
        else {
            Write-Error "Account Database is not accessible"
        }
        
        $transactionDbTest = docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME exec -T postgres-transaction-e2e psql -U e2e_user -d transaction_db_e2e -c "SELECT 1;" 2>$null
        if ($transactionDbTest) {
            Write-Success "Transaction Database is accessible"
        }
        else {
            Write-Error "Transaction Database is not accessible"
        }
        
        # Test Redis connectivity
        Write-Info "Testing Redis connectivity..."
        $redisTest = docker-compose -f $E2E_COMPOSE_FILE -p $PROJECT_NAME exec -T redis-e2e redis-cli -a e2e_redis_password ping 2>$null
        if ($redisTest -eq "PONG") {
            Write-Success "Redis is accessible"
        }
        else {
            Write-Error "Redis is not accessible"
        }
        
        Write-Success "E2E Infrastructure validation completed"
        
    }
    catch {
        Write-Error "Failed to validate E2E infrastructure: $($_.Exception.Message)"
        exit 1
    }
}

function Show-Help {
    Write-Host @"
E2E Infrastructure Setup Script

Usage: .\setup-e2e-infrastructure.ps1 [OPTIONS]

Options:
  -Start      Start the E2E infrastructure
  -Stop       Stop the E2E infrastructure
  -Restart    Restart the E2E infrastructure
  -Status     Show the status of E2E services
  -Logs       Show logs from E2E services
  -Clean      Clean up E2E infrastructure (removes containers, volumes, images)
  -Validate   Validate that E2E infrastructure is working correctly
  -Service    Specify a specific service for logs (use with -Logs)

Examples:
  .\setup-e2e-infrastructure.ps1 -Start
  .\setup-e2e-infrastructure.ps1 -Status
  .\setup-e2e-infrastructure.ps1 -Logs -Service account-service-e2e
  .\setup-e2e-infrastructure.ps1 -Validate
  .\setup-e2e-infrastructure.ps1 -Clean

Services:
  - postgres-account-e2e (Account Database)
  - postgres-transaction-e2e (Transaction Database)
  - redis-e2e (Redis Cache)
  - account-service-e2e (Account Service)
  - transaction-service-e2e (Transaction Service)

Ports:
  - Account Service: http://localhost:8083
  - Transaction Service: http://localhost:8082
  - Account Database: localhost:5434
  - Transaction Database: localhost:5435
  - Redis: localhost:6380
"@
}

# Main script logic
if ($Start) {
    Start-E2EInfrastructure
}
elseif ($Stop) {
    Stop-E2EInfrastructure
}
elseif ($Restart) {
    Restart-E2EInfrastructure
}
elseif ($Status) {
    Get-E2EStatus
}
elseif ($Logs) {
    Show-E2ELogs
}
elseif ($Clean) {
    Clean-E2EInfrastructure
}
elseif ($Validate) {
    Test-E2EInfrastructure
}
else {
    Show-Help
}