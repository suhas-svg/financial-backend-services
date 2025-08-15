#!/bin/bash

# Setup script for pre-commit hooks
set -e

echo "Setting up pre-commit hooks for code quality and security..."

# Check if pre-commit is installed
if ! command -v pre-commit &> /dev/null; then
    echo "Installing pre-commit..."
    
    # Try different installation methods
    if command -v pip &> /dev/null; then
        pip install pre-commit
    elif command -v pip3 &> /dev/null; then
        pip3 install pre-commit
    elif command -v brew &> /dev/null; then
        brew install pre-commit
    elif command -v apt-get &> /dev/null; then
        sudo apt-get update && sudo apt-get install -y pre-commit
    else
        echo "Please install pre-commit manually: https://pre-commit.com/#installation"
        exit 1
    fi
fi

# Install the git hook scripts
echo "Installing pre-commit hooks..."
pre-commit install

# Install commit-msg hook for conventional commits
pre-commit install --hook-type commit-msg

# Run hooks against all files to ensure they work
echo "Running pre-commit hooks against all files..."
pre-commit run --all-files || {
    echo "Some pre-commit hooks failed. Please fix the issues and try again."
    echo "You can run 'pre-commit run --all-files' to see detailed output."
}

echo "Pre-commit hooks setup completed!"
echo ""
echo "Available commands:"
echo "  pre-commit run --all-files    # Run all hooks against all files"
echo "  pre-commit run <hook-id>      # Run specific hook"
echo "  pre-commit autoupdate         # Update hook versions"
echo "  pre-commit uninstall          # Remove hooks"
echo ""
echo "Hooks will now run automatically on git commit."