# Docker Management Script for Account Service (PowerShell)
# Usage: .\docker-manage.ps1 [command] [environment] [options]

param(
    [string]$Command = "help",
    [string]$Environment = "dev",
    [string[]]$Services = @(),
    [switch]$Follow = $false,
    [switch]$Help = $false
)

$ComposeProjectName = "account-service"

# Function to write colored output
function Write-Status {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Function to show usage
function Show-Usage {
    Write-Host "Docker Management Script for Account Service" -ForegroundColor Blue
    Write-Host ""
    Write-Host "Usage: .\docker-manage.ps1 [options]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Parameters:" -ForegroundColor Yellow
    Write-Host "  -Command        Command to execute (up, down, restart, build, logs, status, clean, shell, db-shell, test, help)"
    Write-Host "  -Environment    Environment (dev, staging, prod) [default: dev]"
    Write-Host "  -Services       Specific services to target"
    Write-Host "  -Follow         Follow logs (for logs command)"
    Write-Host "  -Help           Show this help message"
    Write-Host ""
    Write-Host "Commands:" -ForegroundColor Green
    Write-Host "  up          Start services"
    Write-Host "  down        Stop services"
    Write-Host "  restart     Restart services"
    Write-Host "  build       Build images"
    Write-Host "  logs        Show logs"
    Write-Host "  status      Show service status"
    Write-Host "  clean       Clean up containers, images, and volumes"
    Write-Host "  shell       Open shell in app container"
    Write-Host "  db-shell    Open database shell"
    Write-Host "  test        Run tests in container"
    Write-Host "  help        Show this help message"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Cyan
    Write-Host "  .\docker-manage.ps1 -Command up -Environment dev"
    Write-Host "  .\docker-manage.ps1 -Command build -Environment staging"
    Write-Host "  .\docker-manage.ps1 -Command logs -Environment dev -Services app -Follow"
    Write-Host "  .\docker-manage.ps1 -Command clean -Environment dev"
}

# Function to get Docker Compose files
function Get-ComposeFiles {
    param([string]$env)
    
    $files = @("-f", "docker-compose.yml")
    
    switch ($env.ToLower()) {
        { $_ -in @("dev", "development") } {
            $files += @("-f", "docker-compose.dev.yml")
        }
        { $_ -in @("staging", "stage") } {
            $files += @("-f", "docker-compose.staging.yml")
        }
        { $_ -in @("prod", "production") } {
            # Production uses base compose file only
        }
        default {
            Write-Error "Invalid environment: $env"
            exit 1
        }
    }
    
    return $files
}

# Function to set environment variables
function Set-Environment {
    param([string]$env)
    
    $envFile = ""
    switch ($env.ToLower()) {
        { $_ -in @("dev", "development") } {
            $envFile = ".env.dev"
        }
        { $_ -in @("staging", "stage") } {
            $envFile = ".env.staging"
        }
        { $_ -in @("prod", "production") } {
            $envFile = ".env.prod"
        }
    }
    
    if ($envFile -and (Test-Path $envFile)) {
        Get-Content $envFile | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
            $key, $value = $_ -split '=', 2
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
        }
    } elseif ($envFile -eq ".env.prod") {
        Write-Warning "No .env.prod file found. Using default values."
    }
    
    [Environment]::SetEnvironmentVariable("ENVIRONMENT", $env, "Process")
    [Environment]::SetEnvironmentVariable("COMPOSE_PROJECT_NAME", "$ComposeProjectName-$env", "Process")
}

# Function to start services
function Start-Services {
    param([string]$env, [string[]]$services)
    
    Write-Status "Starting services for $env environment..."
    Set-Environment $env
    
    $composeFiles = Get-ComposeFiles $env
    
    if ($services.Count -gt 0) {
        & docker-compose @composeFiles up -d @services
    } else {
        & docker-compose @composeFiles up -d
    }
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Services started successfully!"
        & docker-compose @composeFiles ps
    } else {
        Write-Error "Failed to start services"
        exit 1
    }
}

# Function to stop services
function Stop-Services {
    param([string]$env)
    
    Write-Status "Stopping services for $env environment..."
    Set-Environment $env
    
    $composeFiles = Get-ComposeFiles $env
    & docker-compose @composeFiles down
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Services stopped successfully!"
    } else {
        Write-Error "Failed to stop services"
        exit 1
    }
}

# Function to restart services
function Restart-Services {
    param([string]$env, [string[]]$services)
    
    Write-Status "Restarting services for $env environment..."
    Set-Environment $env
    
    $composeFiles = Get-ComposeFiles $env
    
    if ($services.Count -gt 0) {
        & docker-compose @composeFiles restart @services
    } else {
        & docker-compose @composeFiles restart
    }
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Services restarted successfully!"
    } else {
        Write-Error "Failed to restart services"
        exit 1
    }
}

# Function to build images
function Build-Images {
    param([string]$env)
    
    Write-Status "Building images for $env environment..."
    Set-Environment $env
    
    $composeFiles = Get-ComposeFiles $env
    & docker-compose @composeFiles build --no-cache
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Images built successfully!"
    } else {
        Write-Error "Failed to build images"
        exit 1
    }
}

# Function to show logs
function Show-Logs {
    param([string]$env, [string[]]$services, [bool]$follow)
    
    Set-Environment $env
    $composeFiles = Get-ComposeFiles $env
    
    $logArgs = @()
    if ($follow) {
        $logArgs += "-f"
    } else {
        $logArgs += "--tail=100"
    }
    
    if ($services.Count -gt 0) {
        & docker-compose @composeFiles logs @logArgs @services
    } else {
        & docker-compose @composeFiles logs @logArgs
    }
}

# Function to show service status
function Show-Status {
    param([string]$env)
    
    Write-Status "Service status for $env environment:"
    Set-Environment $env
    
    $composeFiles = Get-ComposeFiles $env
    & docker-compose @composeFiles ps
    
    Write-Host ""
    Write-Status "Resource usage:"
    $containers = & docker-compose @composeFiles ps -q
    if ($containers) {
        & docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}" @containers
    }
}

# Function to clean up
function Invoke-Cleanup {
    param([string]$env)
    
    Write-Warning "This will remove all containers, images, and volumes for $env environment."
    $confirmation = Read-Host "Are you sure? (y/N)"
    
    if ($confirmation -match '^[Yy]$') {
        Set-Environment $env
        $composeFiles = Get-ComposeFiles $env
        
        Write-Status "Stopping and removing containers..."
        & docker-compose @composeFiles down -v --remove-orphans
        
        Write-Status "Removing images..."
        & docker-compose @composeFiles down --rmi all
        
        Write-Status "Pruning unused resources..."
        & docker system prune -f
        
        Write-Success "Cleanup completed!"
    } else {
        Write-Status "Cleanup cancelled."
    }
}

# Function to open shell in app container
function Open-Shell {
    param([string]$env)
    
    Set-Environment $env
    $containerName = "account-service-app-$env"
    
    $runningContainers = & docker ps --format '{{.Names}}'
    if ($runningContainers -contains $containerName) {
        Write-Status "Opening shell in $containerName..."
        & docker exec -it $containerName /bin/sh
    } else {
        Write-Error "Container $containerName is not running."
        exit 1
    }
}

# Function to open database shell
function Open-DbShell {
    param([string]$env)
    
    Set-Environment $env
    $containerName = "account-service-postgres-$env"
    
    $runningContainers = & docker ps --format '{{.Names}}'
    if ($runningContainers -contains $containerName) {
        Write-Status "Opening database shell in $containerName..."
        $postgresUser = if ([Environment]::GetEnvironmentVariable("POSTGRES_USER")) { [Environment]::GetEnvironmentVariable("POSTGRES_USER") } else { "postgres" }
        $postgresDb = if ([Environment]::GetEnvironmentVariable("POSTGRES_DB")) { [Environment]::GetEnvironmentVariable("POSTGRES_DB") } else { "myfirstdb" }
        & docker exec -it $containerName psql -U $postgresUser -d $postgresDb
    } else {
        Write-Error "Database container $containerName is not running."
        exit 1
    }
}

# Function to run tests
function Invoke-Tests {
    param([string]$env)
    
    Write-Status "Running tests in $env environment..."
    Set-Environment $env
    
    $composeFiles = Get-ComposeFiles $env
    & docker-compose @composeFiles exec app ./mvnw test
}

# Show help if requested
if ($Help) {
    Show-Usage
    exit 0
}

# Change to script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AccountServiceDir = Split-Path -Parent $ScriptDir
Push-Location $AccountServiceDir

try {
    # Main command processing
    switch ($Command.ToLower()) {
        { $_ -in @("up", "start") } {
            Start-Services $Environment $Services
        }
        { $_ -in @("down", "stop") } {
            Stop-Services $Environment
        }
        "restart" {
            Restart-Services $Environment $Services
        }
        "build" {
            Build-Images $Environment
        }
        "logs" {
            Show-Logs $Environment $Services $Follow
        }
        { $_ -in @("status", "ps") } {
            Show-Status $Environment
        }
        { $_ -in @("clean", "cleanup") } {
            Invoke-Cleanup $Environment
        }
        { $_ -in @("shell", "bash") } {
            Open-Shell $Environment
        }
        { $_ -in @("db-shell", "db") } {
            Open-DbShell $Environment
        }
        "test" {
            Invoke-Tests $Environment
        }
        { $_ -in @("help", "--help", "-h") } {
            Show-Usage
        }
        default {
            Write-Error "Unknown command: $Command"
            Show-Usage
            exit 1
        }
    }
} finally {
    Pop-Location
}