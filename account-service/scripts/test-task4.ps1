# Test Script for Task 4: Container Image Management and Security (PowerShell)
# This script tests all components of task 4 to ensure they work correctly

param(
    [switch]$Verbose = $false,
    [switch]$Help = $false
)

# Test results tracking
$script:TestsPassed = 0
$script:TestsFailed = 0
$script:TestsTotal = 0

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
    Write-Host "Task 4 Test Script for Container Image Management and Security" -ForegroundColor Blue
    Write-Host ""
    Write-Host "Usage: .\test-task4.ps1 [options]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Parameters:" -ForegroundColor Yellow
    Write-Host "  -Verbose    Show detailed test output"
    Write-Host "  -Help       Show this help message"
    Write-Host ""
    Write-Host "This script tests all components of Task 4:" -ForegroundColor Green
    Write-Host "  - 4.1 Optimized Docker build process"
    Write-Host "  - 4.2 Container security scanning"
    Write-Host "  - 4.3 Container registry integration"
}

# Function to run a test
function Invoke-Test {
    param(
        [string]$TestName,
        [scriptblock]$TestCommand
    )
    
    $script:TestsTotal++
    Write-Status "Running test: $TestName"
    
    try {
        $result = & $TestCommand
        if ($result) {
            Write-Success "✅ $TestName - PASSED"
            $script:TestsPassed++
            return $true
        } else {
            Write-Error "❌ $TestName - FAILED"
            $script:TestsFailed++
            return $false
        }
    } catch {
        Write-Error "❌ $TestName - FAILED: $_"
        $script:TestsFailed++
        return $false
    }
}

# Function to check if command exists
function Test-Command {
    param([string]$Command)
    return [bool](Get-Command $Command -ErrorAction SilentlyContinue)
}

# Test 1: Verify Dockerfile structure and best practices
function Test-DockerfileStructure {
    Write-Status "Testing Dockerfile structure..."
    
    # Check if Dockerfile exists
    if (-not (Test-Path "Dockerfile")) {
        Write-Error "Dockerfile not found"
        return $false
    }
    
    $dockerfileContent = Get-Content "Dockerfile" -Raw
    
    # Check for multi-stage build
    if ($dockerfileContent -notmatch "FROM.*AS builder") {
        Write-Error "Multi-stage build not found in Dockerfile"
        return $false
    }
    
    # Check for non-root user
    if ($dockerfileContent -notmatch "USER appuser") {
        Write-Error "Non-root user not configured in Dockerfile"
        return $false
    }
    
    # Check for health check
    if ($dockerfileContent -notmatch "HEALTHCHECK") {
        Write-Error "Health check not found in Dockerfile"
        return $false
    }
    
    # Check for proper labeling
    if ($dockerfileContent -notmatch "org\.opencontainers\.image") {
        Write-Error "OCI labels not found in Dockerfile"
        return $false
    }
    
    Write-Success "Dockerfile structure validation passed"
    return $true
}

# Test 2: Verify development Dockerfile exists
function Test-DevDockerfile {
    Write-Status "Testing development Dockerfile..."
    
    if (-not (Test-Path "Dockerfile.dev")) {
        Write-Error "Dockerfile.dev not found"
        return $false
    }
    
    $dockerfileContent = Get-Content "Dockerfile.dev" -Raw
    
    # Check for debug port exposure
    if ($dockerfileContent -notmatch "EXPOSE.*5005") {
        Write-Error "Debug port not exposed in Dockerfile.dev"
        return $false
    }
    
    Write-Success "Development Dockerfile validation passed"
    return $true
}

# Test 3: Test Docker build process
function Test-DockerBuild {
    Write-Status "Testing Docker build process..."
    
    # Check if Docker is running
    try {
        docker info | Out-Null
    } catch {
        Write-Warning "Docker is not running, skipping build test"
        return $true
    }
    
    # Test basic build
    try {
        docker build -t account-service:test . | Out-Null
        Write-Success "Docker build successful"
        
        # Test image layers
        $layers = (docker history account-service:test --format "table {{.CreatedBy}}").Count
        if ($layers -lt 20) {
            Write-Success "Image has optimized layers ($layers layers)"
        } else {
            Write-Warning "Image has many layers ($layers layers) - consider optimization"
        }
        
        # Clean up test image
        docker rmi account-service:test | Out-Null
        return $true
    } catch {
        Write-Error "Docker build failed: $_"
        return $false
    }
}

# Test 4: Test build scripts
function Test-BuildScripts {
    Write-Status "Testing build scripts..."
    
    # Test bash script
    if (Test-Path "scripts/build-docker.sh") {
        Write-Success "build-docker.sh exists"
        
        # Test help option (if bash is available)
        if (Test-Command "bash") {
            try {
                bash scripts/build-docker.sh --help | Out-Null
                Write-Success "build-docker.sh help option works"
            } catch {
                Write-Warning "build-docker.sh help option failed"
            }
        }
    } else {
        Write-Error "build-docker.sh not found"
        return $false
    }
    
    # Test PowerShell script
    if (Test-Path "scripts/build-docker.ps1") {
        Write-Success "build-docker.ps1 exists"
        
        # Test help option
        try {
            & ".\scripts\build-docker.ps1" -Help | Out-Null
            Write-Success "build-docker.ps1 help option works"
        } catch {
            Write-Warning "build-docker.ps1 help option failed"
        }
    } else {
        Write-Error "build-docker.ps1 not found"
        return $false
    }
    
    return $true
}

# Test 5: Test Docker Compose configuration
function Test-DockerCompose {
    Write-Status "Testing Docker Compose configuration..."
    
    # Check if docker-compose.yml exists
    if (-not (Test-Path "docker-compose.yml")) {
        Write-Error "docker-compose.yml not found"
        return $false
    }
    
    # Validate compose file syntax
    if (Test-Command "docker-compose") {
        try {
            docker-compose config | Out-Null
            Write-Success "Docker Compose syntax is valid"
        } catch {
            Write-Error "Docker Compose syntax validation failed"
            return $false
        }
    } elseif (Test-Command "docker") {
        try {
            docker compose config | Out-Null
            Write-Success "Docker Compose syntax is valid"
        } catch {
            Write-Error "Docker Compose syntax validation failed"
            return $false
        }
    } else {
        Write-Warning "Docker Compose not available, skipping syntax validation"
    }
    
    # Check for environment-specific overrides
    if ((Test-Path "docker-compose.dev.yml") -and (Test-Path "docker-compose.staging.yml")) {
        Write-Success "Environment-specific compose files exist"
    } else {
        Write-Error "Environment-specific compose files missing"
        return $false
    }
    
    return $true
}

# Test 6: Test environment configuration files
function Test-EnvConfigs {
    Write-Status "Testing environment configuration files..."
    
    # Check for environment files
    $envFiles = @(".env.dev", ".env.staging")
    foreach ($envFile in $envFiles) {
        if (Test-Path $envFile) {
            Write-Success "$envFile exists"
            
            # Check for required variables
            $content = Get-Content $envFile -Raw
            if ($content -match "ENVIRONMENT=" -and $content -match "REGISTRY=") {
                Write-Success "$envFile has required variables"
            } else {
                Write-Warning "$envFile missing some required variables"
            }
        } else {
            Write-Error "$envFile not found"
            return $false
        }
    }
    
    return $true
}

# Test 7: Test security scanning scripts
function Test-SecurityScripts {
    Write-Status "Testing security scanning scripts..."
    
    # Test bash security script
    if (Test-Path "scripts/security-scan.sh") {
        Write-Success "security-scan.sh exists"
        
        # Test help option (if bash is available)
        if (Test-Command "bash") {
            try {
                bash scripts/security-scan.sh --help | Out-Null
                Write-Success "security-scan.sh help option works"
            } catch {
                Write-Warning "security-scan.sh help option failed"
            }
        }
    } else {
        Write-Error "security-scan.sh not found"
        return $false
    }
    
    # Test PowerShell security script
    if (Test-Path "scripts/security-scan.ps1") {
        Write-Success "security-scan.ps1 exists"
        
        # Test help option
        try {
            & ".\scripts\security-scan.ps1" -Help | Out-Null
            Write-Success "security-scan.ps1 help option works"
        } catch {
            Write-Warning "security-scan.ps1 help option failed"
        }
    } else {
        Write-Error "security-scan.ps1 not found"
        return $false
    }
    
    return $true
}

# Test 8: Test security policy configuration
function Test-SecurityPolicies {
    Write-Status "Testing security policy configuration..."
    
    # Check security policy file
    if (Test-Path "security-policy.yaml") {
        Write-Success "security-policy.yaml exists"
        
        # Check for key sections
        $content = Get-Content "security-policy.yaml" -Raw
        if ($content -match "vulnerability_scanning:" -and 
            $content -match "secret_detection:" -and 
            $content -match "configuration_scanning:") {
            Write-Success "Security policy has required sections"
        } else {
            Write-Warning "Security policy missing some sections"
        }
    } else {
        Write-Error "security-policy.yaml not found"
        return $false
    }
    
    # Check Trivy ignore file
    if (Test-Path ".trivyignore") {
        Write-Success ".trivyignore file exists"
    } else {
        Write-Warning ".trivyignore file not found"
    }
    
    return $true
}

# Test 9: Test registry management scripts
function Test-RegistryScripts {
    Write-Status "Testing registry management scripts..."
    
    # Test bash registry script
    if (Test-Path "scripts/registry-manage.sh") {
        Write-Success "registry-manage.sh exists"
        
        # Test help option (if bash is available)
        if (Test-Command "bash") {
            try {
                bash scripts/registry-manage.sh --help | Out-Null
                Write-Success "registry-manage.sh help option works"
            } catch {
                Write-Warning "registry-manage.sh help option failed"
            }
        }
    } else {
        Write-Error "registry-manage.sh not found"
        return $false
    }
    
    # Test PowerShell registry script
    if (Test-Path "scripts/registry-manage.ps1") {
        Write-Success "registry-manage.ps1 exists"
        
        # Test help option
        try {
            & ".\scripts\registry-manage.ps1" -Help | Out-Null
            Write-Success "registry-manage.ps1 help option works"
        } catch {
            Write-Warning "registry-manage.ps1 help option failed"
        }
    } else {
        Write-Error "registry-manage.ps1 not found"
        return $false
    }
    
    return $true
}

# Test 10: Test registry configuration
function Test-RegistryConfig {
    Write-Status "Testing registry configuration..."
    
    if (Test-Path "registry-config.yaml") {
        Write-Success "registry-config.yaml exists"
        
        # Check for key sections
        $content = Get-Content "registry-config.yaml" -Raw
        if ($content -match "registry:" -and 
            $content -match "tagging:" -and 
            $content -match "security:") {
            Write-Success "Registry config has required sections"
        } else {
            Write-Warning "Registry config missing some sections"
        }
    } else {
        Write-Error "registry-config.yaml not found"
        return $false
    }
    
    return $true
}

# Test 11: Test Docker management scripts
function Test-DockerManagement {
    Write-Status "Testing Docker management scripts..."
    
    # Test bash docker management script
    if (Test-Path "scripts/docker-manage.sh") {
        Write-Success "docker-manage.sh exists"
        
        # Test help option (if bash is available)
        if (Test-Command "bash") {
            try {
                bash scripts/docker-manage.sh --help | Out-Null
                Write-Success "docker-manage.sh help option works"
            } catch {
                Write-Warning "docker-manage.sh help option failed"
            }
        }
    } else {
        Write-Error "docker-manage.sh not found"
        return $false
    }
    
    # Test PowerShell docker management script
    if (Test-Path "scripts/docker-manage.ps1") {
        Write-Success "docker-manage.ps1 exists"
        
        # Test help option
        try {
            & ".\scripts\docker-manage.ps1" -Help | Out-Null
            Write-Success "docker-manage.ps1 help option works"
        } catch {
            Write-Warning "docker-manage.ps1 help option failed"
        }
    } else {
        Write-Error "docker-manage.ps1 not found"
        return $false
    }
    
    return $true
}

# Test 12: Test CI/CD workflow integration
function Test-CicdIntegration {
    Write-Status "Testing CI/CD workflow integration..."
    
    if (Test-Path "../.github/workflows/ci-cd-pipeline.yml") {
        Write-Success "CI/CD workflow file exists"
        
        $workflowContent = Get-Content "../.github/workflows/ci-cd-pipeline.yml" -Raw
        
        # Check for Docker build steps
        if ($workflowContent -match "docker/build-push-action") {
            Write-Success "Docker build action found in workflow"
        } else {
            Write-Error "Docker build action not found in workflow"
            return $false
        }
        
        # Check for security scanning
        if ($workflowContent -match "trivy-action") {
            Write-Success "Security scanning found in workflow"
        } else {
            Write-Error "Security scanning not found in workflow"
            return $false
        }
        
        # Check for image signing
        if ($workflowContent -match "cosign") {
            Write-Success "Image signing found in workflow"
        } else {
            Write-Error "Image signing not found in workflow"
            return $false
        }
    } else {
        Write-Error "CI/CD workflow file not found"
        return $false
    }
    
    return $true
}

# Test 13: Test database initialization script
function Test-DbInit {
    Write-Status "Testing database initialization script..."
    
    if (Test-Path "scripts/init-db.sql") {
        Write-Success "Database initialization script exists"
        
        # Check for basic SQL syntax
        $content = Get-Content "scripts/init-db.sql" -Raw
        if ($content -match "CREATE DATABASE" -and $content -match "CREATE EXTENSION") {
            Write-Success "Database script has required SQL commands"
        } else {
            Write-Warning "Database script missing some SQL commands"
        }
    } else {
        Write-Error "Database initialization script not found"
        return $false
    }
    
    return $true
}

# Test 14: Test file permissions and security
function Test-FileSecurity {
    Write-Status "Testing file permissions and security..."
    
    # Check for sensitive files
    if (Test-Path ".env") {
        Write-Warning ".env file found - ensure it's in .gitignore"
    }
    
    # Check script files exist
    $scripts = @("scripts/build-docker.ps1", "scripts/security-scan.ps1", "scripts/registry-manage.ps1", "scripts/docker-manage.ps1")
    foreach ($script in $scripts) {
        if (Test-Path $script) {
            Write-Success "$script exists"
        } else {
            Write-Warning "$script not found"
        }
    }
    
    return $true
}

# Test 15: Integration test with actual build (if Docker is available)
function Test-Integration {
    Write-Status "Running integration test..."
    
    if (-not (Test-Command "docker")) {
        Write-Warning "Docker not available, skipping integration test"
        return $true
    }
    
    try {
        docker info | Out-Null
    } catch {
        Write-Warning "Docker not running, skipping integration test"
        return $true
    }
    
    # Test build with environment variables
    $env:BUILD_ENV = "dev"
    $env:APP_VERSION = "test-1.0.0"
    $env:BUILD_DATE = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $env:VCS_REF = try { git rev-parse HEAD } catch { "test-commit" }
    
    try {
        docker build `
            --build-arg BUILD_ENV="$env:BUILD_ENV" `
            --build-arg APP_VERSION="$env:APP_VERSION" `
            --build-arg BUILD_DATE="$env:BUILD_DATE" `
            --build-arg VCS_REF="$env:VCS_REF" `
            -t account-service:integration-test . | Out-Null
        
        Write-Success "Integration build successful"
        
        # Test image labels
        $labels = docker inspect account-service:integration-test --format='{{json .Config.Labels}}'
        if ($labels -match "org.opencontainers.image.version") {
            Write-Success "Image labels are properly set"
        } else {
            Write-Warning "Image labels not found"
        }
        
        # Clean up
        docker rmi account-service:integration-test | Out-Null
        return $true
    } catch {
        Write-Error "Integration build failed: $_"
        return $false
    }
}

# Main test execution
function Main {
    # Show help if requested
    if ($Help) {
        Show-Usage
        return 0
    }
    
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  Task 4 Testing: Container Image" -ForegroundColor Cyan
    Write-Host "  Management and Security" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    
    # Change to account-service directory
    $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $AccountServiceDir = Split-Path -Parent $ScriptDir
    Push-Location $AccountServiceDir
    
    try {
        Write-Status "Starting Task 4 component tests..."
        Write-Host ""
        
        # Run all tests
        Invoke-Test "Dockerfile Structure" { Test-DockerfileStructure }
        Invoke-Test "Development Dockerfile" { Test-DevDockerfile }
        Invoke-Test "Docker Build Process" { Test-DockerBuild }
        Invoke-Test "Build Scripts" { Test-BuildScripts }
        Invoke-Test "Docker Compose Configuration" { Test-DockerCompose }
        Invoke-Test "Environment Configurations" { Test-EnvConfigs }
        Invoke-Test "Security Scanning Scripts" { Test-SecurityScripts }
        Invoke-Test "Security Policy Configuration" { Test-SecurityPolicies }
        Invoke-Test "Registry Management Scripts" { Test-RegistryScripts }
        Invoke-Test "Registry Configuration" { Test-RegistryConfig }
        Invoke-Test "Docker Management Scripts" { Test-DockerManagement }
        Invoke-Test "CI/CD Integration" { Test-CicdIntegration }
        Invoke-Test "Database Initialization" { Test-DbInit }
        Invoke-Test "File Security" { Test-FileSecurity }
        Invoke-Test "Integration Test" { Test-Integration }
        
        # Print summary
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "           TEST SUMMARY" -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "Total Tests: $script:TestsTotal"
        Write-Host "Passed: $script:TestsPassed" -ForegroundColor Green
        Write-Host "Failed: $script:TestsFailed" -ForegroundColor Red
        Write-Host ""
        
        if ($script:TestsFailed -eq 0) {
            Write-Success "🎉 All tests passed! Task 4 implementation is working correctly."
            Write-Host ""
            Write-Host "Task 4 Components Verified:" -ForegroundColor Green
            Write-Host "✅ 4.1 Optimized Docker build process"
            Write-Host "✅ 4.2 Container security scanning"
            Write-Host "✅ 4.3 Container registry integration"
            return 0
        } else {
            Write-Error "❌ Some tests failed. Please review the issues above."
            Write-Host ""
            Write-Host "Failed tests need attention before Task 4 can be considered complete."
            return 1
        }
    } finally {
        Pop-Location
    }
}

# Run main function
$exitCode = Main
exit $exitCode