#!/bin/bash

# Development environment setup script for MCP Financial Server

set -e

echo "Setting up MCP Financial Server development environment..."

# Check Python version
python_version=$(python3 --version 2>&1 | awk '{print $2}' | cut -d. -f1,2)
required_version="3.9"

if [ "$(printf '%s\n' "$required_version" "$python_version" | sort -V | head -n1)" != "$required_version" ]; then
    echo "Error: Python $required_version or higher is required. Found: $python_version"
    exit 1
fi

echo "✓ Python version check passed: $python_version"

# Create virtual environment
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
    echo "✓ Virtual environment created"
else
    echo "✓ Virtual environment already exists"
fi

# Activate virtual environment
echo "Activating virtual environment..."
source venv/bin/activate

# Upgrade pip
echo "Upgrading pip..."
pip install --upgrade pip

# Install dependencies
echo "Installing dependencies..."
pip install -r requirements.txt

echo "✓ Dependencies installed"

# Create .env file if it doesn't exist
if [ ! -f ".env" ]; then
    echo "Creating .env file from template..."
    cp .env.example .env
    echo "✓ .env file created - please review and update configuration"
else
    echo "✓ .env file already exists"
fi

# Create necessary directories
mkdir -p logs
mkdir -p monitoring/grafana/dashboards
mkdir -p monitoring/grafana/datasources

echo "✓ Directory structure created"

# Run basic validation
echo "Running basic validation..."
python -c "import src.mcp_financial; print('✓ Package imports successfully')"

echo ""
echo "🎉 Development environment setup complete!"
echo ""
echo "Next steps:"
echo "1. Review and update .env file with your configuration"
echo "2. Ensure Account Service is running on port 8080"
echo "3. Ensure Transaction Service is running on port 8081"
echo "4. Run the server: python src/main.py"
echo "5. Run tests: pytest"
echo ""
echo "To activate the virtual environment in the future:"
echo "source venv/bin/activate"