# Docker Build Script for Account Service (PowerShell)
# Usage: .\build-docker.ps1 [environment] [version] [options]

param(
    [string]$Environment = "dev",
    [string]$Version = "latest",
    [string]$Registry = $(if ($env:REGISTRY) { $env:REGISTRY } else { "ghcr.io" }),
    [string]$ImageName = $(if ($env:IMAGE_NAME) { $env:IMAGE_NAME } else { "account-service" }),
    [switch]$Push = $false,
    [string]$Platforms = $(if ($env:PLATFORMS) { $env:PLATFORMS } else { "linux/amd64" }),
    [switch]$NoCache = $false,
    [switch]$Help = $false
)

# Function to show usage
function Show-Usage {
    Write-Host "Usage: .\build-docker.ps1 [options]" -ForegroundColor Blue
    Write-Host ""
    Write-Host "Parameters:" -ForegroundColor Yellow
    Write-Host "  -Environment    Build environment (dev, staging, prod) [default: dev]"
    Write-Host "  -Version        Image version tag [default: latest]"
    Write-Host "  -Registry       Container registry [default: ghcr.io]"
    Write-Host "  -ImageName      Image name [default: account-service]"
    Write-Host "  -Push           Push to registry"
    Write-Host "  -Platforms      Target platforms [default: linux/amd64]"
    Write-Host "  -NoCache        Build without cache"
    Write-Host "  -Help           Show this help message"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Green
    Write-Host "  .\build-docker.ps1 -Environment dev -Version v1.0.0"
    Write-Host "  .\build-docker.ps1 -Environment prod -Version v1.2.3 -Push"
    Write-Host "  .\build-docker.ps1 -Platforms 'linux/amd64,linux/arm64' -Environment staging"
}

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

# Show help if requested
if ($Help) {
    Show-Usage
    exit 0
}

# Validate environment
$BuildEnv = ""
$Dockerfile = ""

switch ($Environment.ToLower()) {
    { $_ -in @("dev", "development") } {
        $Dockerfile = "Dockerfile.dev"
        $BuildEnv = "dev"
    }
    { $_ -in @("staging", "stage") } {
        $Dockerfile = "Dockerfile"
        $BuildEnv = "staging"
    }
    { $_ -in @("prod", "production") } {
        $Dockerfile = "Dockerfile"
        $BuildEnv = "prod"
    }
    default {
        Write-Error "Invalid environment: $Environment"
        Write-Error "Valid environments: dev, staging, prod"
        exit 1
    }
}

# Set image tags
$FullImageName = "$Registry/$ImageName"
$ImageTag = "${FullImageName}:${Version}"
$EnvTag = "${FullImageName}:${BuildEnv}-latest"

Write-Status "Building Docker image for Account Service"
Write-Status "Environment: $BuildEnv"
Write-Status "Version: $Version"
Write-Status "Dockerfile: $Dockerfile"
Write-Status "Image: $ImageTag"
Write-Status "Platforms: $Platforms"

# Check if Docker is running
try {
    docker info | Out-Null
} catch {
    Write-Error "Docker is not running. Please start Docker and try again."
    exit 1
}

# Check if buildx is available for multi-platform builds
$IsMultiPlatform = $Platforms.Contains(",")
if ($IsMultiPlatform) {
    try {
        docker buildx version | Out-Null
    } catch {
        Write-Error "Docker Buildx is required for multi-platform builds"
        exit 1
    }
    
    # Create builder instance if it doesn't exist
    $builderExists = docker buildx inspect multiarch 2>$null
    if (-not $builderExists) {
        Write-Status "Creating multi-platform builder..."
        docker buildx create --name multiarch --driver docker-container --use
        docker buildx inspect --bootstrap
    } else {
        docker buildx use multiarch
    }
}

# Generate build arguments
$BuildDate = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$VcsRef = try { git rev-parse HEAD } catch { "unknown" }

$BuildArgs = @(
    "--build-arg", "BUILD_ENV=$BuildEnv",
    "--build-arg", "APP_VERSION=$Version",
    "--build-arg", "BUILD_DATE=$BuildDate",
    "--build-arg", "VCS_REF=$VcsRef",
    "--label", "org.opencontainers.image.title=Account Service",
    "--label", "org.opencontainers.image.description=Financial Account Service Microservice",
    "--label", "org.opencontainers.image.version=$Version",
    "--label", "org.opencontainers.image.created=$BuildDate",
    "--label", "org.opencontainers.image.revision=$VcsRef",
    "--label", "environment=$BuildEnv",
    "-t", $ImageTag,
    "-t", $EnvTag,
    "-f", $Dockerfile
)

if ($NoCache) {
    $BuildArgs += "--no-cache"
}

# Build command
if ($IsMultiPlatform) {
    $BuildCmd = "docker", "buildx", "build"
    $BuildArgs += "--platform", $Platforms
    if ($Push) {
        $BuildArgs += "--push"
    } else {
        $BuildArgs += "--load"
        Write-Warning "Multi-platform builds cannot be loaded locally. Use -Push to push to registry."
    }
} else {
    $BuildCmd = "docker", "build"
}

# Change to the account-service directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AccountServiceDir = Split-Path -Parent $ScriptDir
Push-Location $AccountServiceDir

try {
    # Execute build
    Write-Status "Executing build command..."
    $FullCommand = $BuildCmd + $BuildArgs + "."
    Write-Host "Command: $($FullCommand -join ' ')" -ForegroundColor Cyan
    
    & $BuildCmd @BuildArgs .
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Docker image built successfully!"
        
        # Show image information
        if (-not $IsMultiPlatform) {
            Write-Status "Image information:"
            docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}" | Select-String $ImageName | Select-Object -First 5
        }
        
        # Push to registry if requested and not multi-platform
        if ($Push -and -not $IsMultiPlatform) {
            Write-Status "Pushing image to registry..."
            docker push $ImageTag
            docker push $EnvTag
            Write-Success "Image pushed successfully!"
        }
        
        # Security scan if trivy is available
        if (Get-Command trivy -ErrorAction SilentlyContinue) {
            Write-Status "Running security scan..."
            trivy image --severity HIGH,CRITICAL $ImageTag
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "Security scan found issues"
            }
        }
        
        Write-Success "Build process completed!"
        Write-Host ""
        Write-Host "Image tags:" -ForegroundColor Yellow
        Write-Host "  - $ImageTag"
        Write-Host "  - $EnvTag"
        
    } else {
        Write-Error "Docker build failed!"
        exit 1
    }
} finally {
    Pop-Location
}