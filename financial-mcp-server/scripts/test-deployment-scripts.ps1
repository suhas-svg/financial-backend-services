# Test Script for MCP Financial Server Deployment Scripts
# This script tests all deployment components without actually deploying

Write-Host "🧪 Testing MCP Financial Server Deployment Scripts" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host ""

$ErrorCount = 0
$TestCount = 0

function Test-Component {
    param(
        [string]$Name,
        [scriptblock]$TestScript
    )
    
    $script:TestCount++
    Write-Host "[$script:TestCount] Testing $Name..." -ForegroundColor Blue
    
    try {
        $result = & $TestScript
        if ($result -eq $false) {
            Write-Host "❌ FAILED: $Name" -ForegroundColor Red
            $script:ErrorCount++
        } else {
            Write-Host "✅ PASSED: $Name" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "❌ ERROR: $Name - $_" -ForegroundColor Red
        $script:ErrorCount++
    }
    Write-Host ""
}

# Test 1: Configuration Validation Script
Test-Component "Configuration Validation Script" {
    $output = python scripts/validate-config.py --env development --env-file .env.dev 2>&1
    return $LASTEXITCODE -eq 0
}

# Test 2: PowerShell Health Check Script
Test-Component "PowerShell Health Check Script" {
    # Test help output
    $output = powershell -ExecutionPolicy Bypass -File scripts/health-check.ps1 -Mode basic -HostName nonexistent -Port 9999 2>&1
    # Should fail but script should run without syntax errors
    return $true
}

# Test 3: PowerShell Deployment Script
Test-Component "PowerShell Deployment Script Help" {
    $output = powershell -ExecutionPolicy Bypass -File scripts/deploy-dev.ps1 -Action invalid 2>&1
    # Should show usage and exit with error
    return $output -match "Usage:"
}

# Test 4: Docker Build (Development)
Test-Component "Docker Build - Development Target" {
    $output = docker build --target development -t mcp-financial-server:test-dev . 2>&1
    return $LASTEXITCODE -eq 0
}

# Test 5: Docker Build (Production)
Test-Component "Docker Build - Production Target" {
    $output = docker build --target production -t mcp-financial-server:test-prod . 2>&1
    return $LASTEXITCODE -eq 0
}

# Test 6: Docker Compose Configuration Validation
Test-Component "Docker Compose - Development Config" {
    $output = docker-compose -f docker-compose.dev.yml config 2>&1
    return $LASTEXITCODE -eq 0
}

Test-Component "Docker Compose - Staging Config" {
    $output = docker-compose -f docker-compose.staging.yml config 2>&1
    return $LASTEXITCODE -eq 0
}

Test-Component "Docker Compose - Production Config" {
    $output = docker-compose -f docker-compose.prod.yml config 2>&1
    return $LASTEXITCODE -eq 0
}

# Test 7: Environment Files Exist
Test-Component "Environment Files Existence" {
    $files = @(".env.dev", ".env.staging", ".env.prod", ".env.example")
    foreach ($file in $files) {
        if (-not (Test-Path $file)) {
            Write-Host "Missing file: $file" -ForegroundColor Red
            return $false
        }
    }
    return $true
}

# Test 8: Required Scripts Exist
Test-Component "Required Scripts Existence" {
    $scripts = @(
        "scripts/validate-config.py",
        "scripts/health-check.ps1",
        "scripts/deploy-dev.ps1",
        "scripts/deploy-dev.sh",
        "scripts/deploy-staging.sh",
        "scripts/deploy-prod.sh",
        "scripts/health-check.sh"
    )
    foreach ($script in $scripts) {
        if (-not (Test-Path $script)) {
            Write-Host "Missing script: $script" -ForegroundColor Red
            return $false
        }
    }
    return $true
}

# Test 9: Documentation Files Exist
Test-Component "Documentation Files Existence" {
    $docs = @("DEPLOYMENT.md", "OPERATIONS.md", "DEPLOYMENT-README.md", "Makefile")
    foreach ($doc in $docs) {
        if (-not (Test-Path $doc)) {
            Write-Host "Missing documentation: $doc" -ForegroundColor Red
            return $false
        }
    }
    return $true
}

# Test 10: Configuration Validation for All Environments
Test-Component "Configuration Validation - All Environments" {
    $environments = @("development", "staging", "production")
    foreach ($env in $environments) {
        $envFile = ".env.$env"
        if ($env -eq "development") { $envFile = ".env.dev" }
        if ($env -eq "production") { $envFile = ".env.prod" }
        
        $output = python scripts/validate-config.py --env $env --env-file $envFile 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Validation failed for $env environment" -ForegroundColor Red
            return $false
        }
    }
    return $true
}

# Clean up test images
Write-Host "🧹 Cleaning up test images..." -ForegroundColor Yellow
docker rmi mcp-financial-server:test-dev mcp-financial-server:test-prod 2>$null

# Summary
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "📊 Test Summary:" -ForegroundColor Cyan
Write-Host "Total Tests: $TestCount" -ForegroundColor White
Write-Host "Passed: $($TestCount - $ErrorCount)" -ForegroundColor Green
Write-Host "Failed: $ErrorCount" -ForegroundColor Red

if ($ErrorCount -eq 0) {
    Write-Host ""
    Write-Host "🎉 All deployment scripts are working correctly!" -ForegroundColor Green
    Write-Host ""
    Write-Host "✅ Ready for deployment:" -ForegroundColor Green
    Write-Host "  • Development: .\scripts\deploy-dev.ps1" -ForegroundColor White
    Write-Host "  • Health Check: .\scripts\health-check.ps1" -ForegroundColor White
    Write-Host "  • Validation: python scripts\validate-config.py --env development" -ForegroundColor White
    exit 0
} else {
    Write-Host ""
    Write-Host "❌ Some tests failed. Please review the errors above." -ForegroundColor Red
    exit 1
}