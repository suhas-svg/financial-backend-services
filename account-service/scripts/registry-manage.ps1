# Container Registry Management Script for Account Service (PowerShell)
# Usage: .\registry-manage.ps1 [command] [options]

param(
    [string]$Command = "help",
    [string]$Registry = $(if ($env:REGISTRY) { $env:REGISTRY } else { "ghcr.io" }),
    [string]$Repository = $(if ($env:REPOSITORY) { $env:REPOSITORY } else { "account-service" }),
    [string]$Owner = $(if ($env:OWNER) { $env:OWNER } else { "unknown" }),
    [string]$ImageTag = "",
    [string]$LocalTag = "",
    [switch]$DryRun = $false,
    [switch]$Help = $false
)

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
    Write-Host "Container Registry Management Script for Account Service" -ForegroundColor Blue
    Write-Host ""
    Write-Host "Usage: .\registry-manage.ps1 [options]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Parameters:" -ForegroundColor Yellow
    Write-Host "  -Command       Command to execute (list, info, cleanup, sign, verify, push, pull, delete, policy, help)"
    Write-Host "  -Registry      Container registry [default: ghcr.io]"
    Write-Host "  -Repository    Repository name [default: account-service]"
    Write-Host "  -Owner         Repository owner [default: auto-detected]"
    Write-Host "  -ImageTag      Image tag for operations"
    Write-Host "  -LocalTag      Local tag for push operations"
    Write-Host "  -DryRun        Perform dry run (for cleanup)"
    Write-Host "  -Help          Show this help message"
    Write-Host ""
    Write-Host "Commands:" -ForegroundColor Green
    Write-Host "  list           List all images and tags"
    Write-Host "  info           Show registry information"
    Write-Host "  cleanup        Clean up old images"
    Write-Host "  sign           Sign an image"
    Write-Host "  verify         Verify image signature"
    Write-Host "  push           Push image to registry"
    Write-Host "  pull           Pull image from registry"
    Write-Host "  delete         Delete specific image/tag"
    Write-Host "  policy         Show registry policies"
    Write-Host "  help           Show this help message"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Cyan
    Write-Host "  .\registry-manage.ps1 -Command list"
    Write-Host "  .\registry-manage.ps1 -Command info"
    Write-Host "  .\registry-manage.ps1 -Command cleanup -DryRun"
    Write-Host "  .\registry-manage.ps1 -Command sign -ImageTag v1.0.0"
    Write-Host "  .\registry-manage.ps1 -Command verify -ImageTag v1.0.0"
}

# Function to check prerequisites
function Test-Prerequisites {
    $missingTools = @()
    
    # Check required tools
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        $missingTools += "docker"
    }
    
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        $missingTools += "gh (GitHub CLI)"
    }
    
    if ($missingTools.Count -gt 0) {
        Write-Error "Missing required tools: $($missingTools -join ', ')"
        Write-Error "Please install the missing tools and try again."
        return $false
    }
    
    # Check authentication
    try {
        gh auth status | Out-Null
    } catch {
        Write-Error "GitHub CLI not authenticated. Please run 'gh auth login' first."
        return $false
    }
    
    return $true
}

# Function to get full image name
function Get-FullImageName {
    param([string]$Tag = "latest")
    return "$Registry/$Owner/$Repository`:$Tag"
}

# Function to auto-detect owner
function Get-RepositoryOwner {
    try {
        $remoteUrl = git config --get remote.origin.url
        if ($remoteUrl -match "github\.com[:/]([^/]+)") {
            return $Matches[1]
        }
    } catch {
        # Ignore errors
    }
    return "unknown"
}

# Function to list images
function Get-Images {
    Write-Status "Listing images in registry..."
    
    try {
        $versions = gh api "/orgs/$Owner/packages/container/$Repository/versions" --jq '.[].metadata.container.tags[]' 2>$null
        
        if ($versions) {
            Write-Host "Available tags:" -ForegroundColor Yellow
            $versions | Sort-Object | ForEach-Object { Write-Host "  $_" }
            
            Write-Host ""
            Write-Host "Tag count: $($versions.Count)" -ForegroundColor Green
        } else {
            Write-Warning "No images found or unable to access registry"
        }
    } catch {
        Write-Warning "Unable to retrieve image list: $_"
    }
}

# Function to show registry info
function Show-RegistryInfo {
    Write-Status "Registry Information"
    Write-Host "Registry: $Registry" -ForegroundColor Cyan
    Write-Host "Owner: $Owner" -ForegroundColor Cyan
    Write-Host "Repository: $Repository" -ForegroundColor Cyan
    Write-Host "Full repository: $Owner/$Repository" -ForegroundColor Cyan
    Write-Host ""
    
    try {
        $packageInfo = gh api "/orgs/$Owner/packages/container/$Repository" 2>$null | ConvertFrom-Json
        
        if ($packageInfo) {
            Write-Host "Package Information:" -ForegroundColor Yellow
            Write-Host "Name: $($packageInfo.name)"
            Write-Host "Visibility: $($packageInfo.visibility)"
            Write-Host "Created: $($packageInfo.created_at)"
            Write-Host "Updated: $($packageInfo.updated_at)"
            Write-Host "Downloads: $($packageInfo.download_count)"
        }
    } catch {
        Write-Warning "Unable to retrieve package information: $_"
    }
}

# Function to cleanup old images
function Invoke-ImageCleanup {
    param([bool]$DryRun = $false)
    
    Write-Status "Cleaning up old images..."
    
    if ($DryRun) {
        Write-Warning "DRY RUN MODE - No images will be deleted"
    }
    
    try {
        $versions = gh api "/orgs/$Owner/packages/container/$Repository/versions" 2>$null | ConvertFrom-Json
        
        if (-not $versions) {
            Write-Warning "No versions found or unable to access registry"
            return
        }
        
        Write-Host "Cleanup strategies:" -ForegroundColor Yellow
        Write-Host "1. Keep last 10 development tags"
        Write-Host "2. Keep last 20 build tags"
        Write-Host "3. Keep all production/staging tags"
        Write-Host "4. Remove untagged images older than 30 days"
        
        # Get development tags (older than 10 most recent)
        $allTags = $versions | ForEach-Object { $_.metadata.container.tags } | Where-Object { $_ -ne $null }
        $devTags = $allTags | Where-Object { $_ -match '^(dev|feature|pr)-' } | Select-Object -Skip 10
        
        if ($devTags) {
            Write-Host ""
            Write-Host "Development tags to cleanup:" -ForegroundColor Yellow
            $devTags | ForEach-Object { Write-Host "  $_" }
            
            if (-not $DryRun) {
                Write-Warning "Cleanup implementation requires additional API permissions"
            }
        }
        
        # Get build tags (older than 20 most recent)
        $buildTags = $allTags | Where-Object { $_ -match '^build-' } | Select-Object -Skip 20
        
        if ($buildTags) {
            Write-Host ""
            Write-Host "Build tags to cleanup:" -ForegroundColor Yellow
            $buildTags | ForEach-Object { Write-Host "  $_" }
            
            if (-not $DryRun) {
                Write-Warning "Cleanup implementation requires additional API permissions"
            }
        }
        
        Write-Success "Cleanup analysis completed"
    } catch {
        Write-Error "Cleanup failed: $_"
    }
}

# Function to sign image
function Invoke-ImageSigning {
    param([string]$Tag)
    
    if (-not $Tag) {
        Write-Error "Image tag is required for signing"
        return $false
    }
    
    $fullImage = Get-FullImageName $Tag
    Write-Status "Signing image: $fullImage"
    
    # Check if cosign is installed
    if (-not (Get-Command cosign -ErrorAction SilentlyContinue)) {
        Write-Error "Cosign is not installed. Please install cosign first."
        return $false
    }
    
    try {
        # Sign the image
        & cosign sign --yes $fullImage
        Write-Success "Image signed successfully"
        return $true
    } catch {
        Write-Error "Image signing failed: $_"
        return $false
    }
}

# Function to verify image signature
function Test-ImageSignature {
    param([string]$Tag)
    
    if (-not $Tag) {
        Write-Error "Image tag is required for verification"
        return $false
    }
    
    $fullImage = Get-FullImageName $Tag
    Write-Status "Verifying image signature: $fullImage"
    
    # Check if cosign is installed
    if (-not (Get-Command cosign -ErrorAction SilentlyContinue)) {
        Write-Error "Cosign is not installed. Please install cosign first."
        return $false
    }
    
    try {
        # Verify the image signature
        & cosign verify $fullImage --certificate-identity-regexp=".*" --certificate-oidc-issuer-regexp=".*"
        Write-Success "Image signature verified successfully"
        return $true
    } catch {
        Write-Error "Image signature verification failed: $_"
        return $false
    }
}

# Function to push image
function Push-Image {
    param([string]$Tag, [string]$LocalTag = $Tag)
    
    if (-not $Tag) {
        Write-Error "Image tag is required for pushing"
        return $false
    }
    
    $fullImage = Get-FullImageName $Tag
    Write-Status "Pushing image: $LocalTag -> $fullImage"
    
    try {
        # Tag the local image
        & docker tag $LocalTag $fullImage
        
        # Push the image
        & docker push $fullImage
        
        Write-Success "Image pushed successfully"
        return $true
    } catch {
        Write-Error "Image push failed: $_"
        return $false
    }
}

# Function to pull image
function Pull-Image {
    param([string]$Tag)
    
    if (-not $Tag) {
        Write-Error "Image tag is required for pulling"
        return $false
    }
    
    $fullImage = Get-FullImageName $Tag
    Write-Status "Pulling image: $fullImage"
    
    try {
        # Pull the image
        & docker pull $fullImage
        Write-Success "Image pulled successfully"
        return $true
    } catch {
        Write-Error "Image pull failed: $_"
        return $false
    }
}

# Function to delete image
function Remove-Image {
    param([string]$Tag)
    
    if (-not $Tag) {
        Write-Error "Image tag is required for deletion"
        return $false
    }
    
    Write-Warning "This will permanently delete the image: $Tag"
    $confirmation = Read-Host "Are you sure? (y/N)"
    
    if ($confirmation -match '^[Yy]$') {
        Write-Status "Deleting image: $Tag"
        Write-Warning "Image deletion requires additional API permissions"
        Write-Warning "Use GitHub web interface or contact repository admin"
    } else {
        Write-Status "Deletion cancelled"
    }
    
    return $false
}

# Function to show registry policies
function Show-RegistryPolicies {
    Write-Status "Registry Policies"
    Write-Host ""
    Write-Host "Current policies for $Owner/$Repository`:" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "1. Image Retention:" -ForegroundColor Yellow
    Write-Host "   - Production tags: Keep indefinitely"
    Write-Host "   - Staging tags: Keep last 10"
    Write-Host "   - Development tags: Keep last 10"
    Write-Host "   - Build tags: Keep last 20"
    Write-Host "   - Untagged images: Delete after 30 days"
    Write-Host ""
    Write-Host "2. Security Policies:" -ForegroundColor Yellow
    Write-Host "   - All images must be signed"
    Write-Host "   - Vulnerability scanning required"
    Write-Host "   - SBOM generation required"
    Write-Host ""
    Write-Host "3. Access Policies:" -ForegroundColor Yellow
    Write-Host "   - Read access: Public"
    Write-Host "   - Write access: Repository collaborators"
    Write-Host "   - Admin access: Repository owners"
    Write-Host ""
    Write-Host "4. Naming Conventions:" -ForegroundColor Yellow
    Write-Host "   - Production: v{major}.{minor}.{patch}, stable, latest"
    Write-Host "   - Staging: staging, staging-{version}"
    Write-Host "   - Development: dev, dev-{feature}, pr-{number}"
    Write-Host "   - Build: build-{date}-{number}"
}

# Main execution
function Main {
    # Show help if requested
    if ($Help) {
        Show-Usage
        return 0
    }
    
    # Auto-detect owner if not provided
    if ($Owner -eq "unknown") {
        $Owner = Get-RepositoryOwner
    }
    
    # Check prerequisites for most commands
    if ($Command -notin @("help", "policy")) {
        if (-not (Test-Prerequisites)) {
            return 1
        }
    }
    
    # Execute command
    switch ($Command.ToLower()) {
        { $_ -in @("list", "ls") } {
            Get-Images
        }
        "info" {
            Show-RegistryInfo
        }
        { $_ -in @("cleanup", "clean") } {
            Invoke-ImageCleanup $DryRun
        }
        "sign" {
            if (-not (Invoke-ImageSigning $ImageTag)) { return 1 }
        }
        "verify" {
            if (-not (Test-ImageSignature $ImageTag)) { return 1 }
        }
        "push" {
            if (-not (Push-Image $ImageTag $LocalTag)) { return 1 }
        }
        "pull" {
            if (-not (Pull-Image $ImageTag)) { return 1 }
        }
        { $_ -in @("delete", "rm") } {
            Remove-Image $ImageTag
        }
        { $_ -in @("policy", "policies") } {
            Show-RegistryPolicies
        }
        { $_ -in @("help", "--help", "-h") } {
            Show-Usage
        }
        default {
            Write-Error "Unknown command: $Command"
            Show-Usage
            return 1
        }
    }
    
    return 0
}

# Change to script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AccountServiceDir = Split-Path -Parent $ScriptDir
Push-Location $AccountServiceDir

try {
    $exitCode = Main
    exit $exitCode
} finally {
    Pop-Location
}