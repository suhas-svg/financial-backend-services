# Development environment setup script for MCP Financial Server (Windows)

Write-Host "Setting up MCP Financial Server development environment..." -ForegroundColor Green

# Check Python version
try {
    $pythonVersion = python --version 2>&1
    if ($pythonVersion -match "Python (\d+\.\d+)") {
        $version = [version]$matches[1]
        $requiredVersion = [version]"3.9"
        
        if ($version -lt $requiredVersion) {
            Write-Host "Error: Python 3.9 or higher is required. Found: $($version)" -ForegroundColor Red
            exit 1
        }
        Write-Host "✓ Python version check passed: $($version)" -ForegroundColor Green
    }
} catch {
    Write-Host "Error: Python not found. Please install Python 3.9 or higher." -ForegroundColor Red
    exit 1
}

# Create virtual environment
if (-not (Test-Path "venv")) {
    Write-Host "Creating virtual environment..." -ForegroundColor Yellow
    python -m venv venv
    Write-Host "✓ Virtual environment created" -ForegroundColor Green
} else {
    Write-Host "✓ Virtual environment already exists" -ForegroundColor Green
}

# Activate virtual environment
Write-Host "Activating virtual environment..." -ForegroundColor Yellow
& "venv\Scripts\Activate.ps1"

# Upgrade pip
Write-Host "Upgrading pip..." -ForegroundColor Yellow
python -m pip install --upgrade pip

# Install dependencies
Write-Host "Installing dependencies..." -ForegroundColor Yellow
pip install -r requirements.txt

Write-Host "✓ Dependencies installed" -ForegroundColor Green

# Create .env file if it doesn't exist
if (-not (Test-Path ".env")) {
    Write-Host "Creating .env file from template..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
    Write-Host "✓ .env file created - please review and update configuration" -ForegroundColor Green
} else {
    Write-Host "✓ .env file already exists" -ForegroundColor Green
}

# Create necessary directories
$directories = @("logs", "monitoring\grafana\dashboards", "monitoring\grafana\datasources")
foreach ($dir in $directories) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

Write-Host "✓ Directory structure created" -ForegroundColor Green

# Run basic validation
Write-Host "Running basic validation..." -ForegroundColor Yellow
try {
    python -c "import sys; sys.path.append('src'); import mcp_financial; print('✓ Package imports successfully')"
} catch {
    Write-Host "Warning: Package validation failed - this is expected until dependencies are properly configured" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🎉 Development environment setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Review and update .env file with your configuration"
Write-Host "2. Ensure Account Service is running on port 8080"
Write-Host "3. Ensure Transaction Service is running on port 8081"
Write-Host "4. Run the server: python src/main.py"
Write-Host "5. Run tests: pytest"
Write-Host ""
Write-Host "To activate the virtual environment in the future:" -ForegroundColor Cyan
Write-Host "venv\Scripts\Activate.ps1"