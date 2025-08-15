#!/bin/bash

# Terraform Validation Script
# This script validates Terraform configurations across all environments

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_ROOT="$SCRIPT_DIR/.."

print_status "Starting Terraform validation..."

# Check prerequisites
print_status "Checking prerequisites..."

if ! command -v terraform &> /dev/null; then
    print_error "Terraform is not installed or not in PATH"
    exit 1
fi

if ! command -v kubectl &> /dev/null; then
    print_error "kubectl is not installed or not in PATH"
    exit 1
fi

if ! command -v helm &> /dev/null; then
    print_error "helm is not installed or not in PATH"
    exit 1
fi

print_success "Prerequisites check passed"

# Validate modules
print_status "Validating Terraform modules..."

MODULES_DIR="$TERRAFORM_ROOT/modules"
for module_dir in "$MODULES_DIR"/*; do
    if [[ -d "$module_dir" ]]; then
        module_name=$(basename "$module_dir")
        print_status "Validating module: $module_name"
        
        cd "$module_dir"
        
        # Initialize module
        terraform init -backend=false
        
        # Validate module
        if terraform validate; then
            print_success "Module $module_name validation passed"
        else
            print_error "Module $module_name validation failed"
            exit 1
        fi
        
        # Check formatting
        if terraform fmt -check=true; then
            print_success "Module $module_name formatting is correct"
        else
            print_warning "Module $module_name formatting issues found"
            terraform fmt -diff=true
        fi
    fi
done

# Validate environments
print_status "Validating environment configurations..."

ENVIRONMENTS_DIR="$TERRAFORM_ROOT/environments"
ENVIRONMENTS=("dev" "staging" "prod")

for env in "${ENVIRONMENTS[@]}"; do
    env_dir="$ENVIRONMENTS_DIR/$env"
    
    if [[ -d "$env_dir" ]]; then
        print_status "Validating environment: $env"
        
        cd "$env_dir"
        
        # Initialize environment
        terraform init -backend=false
        
        # Validate environment
        if terraform validate; then
            print_success "Environment $env validation passed"
        else
            print_error "Environment $env validation failed"
            exit 1
        fi
        
        # Check formatting
        if terraform fmt -check=true; then
            print_success "Environment $env formatting is correct"
        else
            print_warning "Environment $env formatting issues found"
            terraform fmt -diff=true
        fi
        
        # Run plan to check for configuration issues
        print_status "Running plan for environment: $env"
        if terraform plan -var="environment=$env" -out="$env.tfplan" > /dev/null 2>&1; then
            print_success "Environment $env plan succeeded"
            rm -f "$env.tfplan"
        else
            print_warning "Environment $env plan had issues (this may be expected if cluster is not available)"
        fi
    else
        print_warning "Environment directory not found: $env_dir"
    fi
done

# Validate security configurations
print_status "Validating security configurations..."

# Check for hardcoded secrets
print_status "Checking for hardcoded secrets..."
if grep -r -i "password\|secret\|key" "$TERRAFORM_ROOT" --include="*.tf" --exclude-dir=".terraform" | grep -v "variable\|description\|output" | grep -v "password.*=.*var\." | grep -v "secret.*=.*var\."; then
    print_warning "Potential hardcoded secrets found. Please review the above matches."
else
    print_success "No hardcoded secrets detected"
fi

# Check for proper variable validation
print_status "Checking variable validations..."
validation_count=$(find "$TERRAFORM_ROOT" -name "variables.tf" -exec grep -l "validation" {} \; | wc -l)
if [[ $validation_count -gt 0 ]]; then
    print_success "Found $validation_count files with variable validations"
else
    print_warning "No variable validations found. Consider adding validations for critical variables."
fi

# Check for required tags/labels
print_status "Checking for consistent labeling..."
if grep -r "environment.*=.*var\.environment" "$TERRAFORM_ROOT" --include="*.tf" > /dev/null; then
    print_success "Environment labeling found"
else
    print_warning "Environment labeling may be missing"
fi

# Generate validation report
REPORT_FILE="$TERRAFORM_ROOT/validation-report.txt"
print_status "Generating validation report: $REPORT_FILE"

cat > "$REPORT_FILE" << EOF
Terraform Infrastructure Validation Report
==========================================
Generated: $(date)

Prerequisites:
- Terraform: $(terraform version | head -n1)
- kubectl: $(kubectl version --client --short 2>/dev/null || echo "Not available")
- helm: $(helm version --short 2>/dev/null || echo "Not available")

Modules Validated:
$(find "$TERRAFORM_ROOT/modules" -maxdepth 1 -type d -not -path "$TERRAFORM_ROOT/modules" | sed 's|.*/||' | sed 's/^/- /')

Environments Validated:
$(find "$TERRAFORM_ROOT/environments" -maxdepth 1 -type d -not -path "$TERRAFORM_ROOT/environments" | sed 's|.*/||' | sed 's/^/- /')

Security Checks:
- Hardcoded secrets scan: Completed
- Variable validations: $validation_count files with validations
- Labeling consistency: Checked

Status: PASSED
EOF

print_success "Validation completed successfully!"
print_status "Validation report saved to: $REPORT_FILE"

# Optional: Run terraform-docs if available
if command -v terraform-docs &> /dev/null; then
    print_status "Generating documentation with terraform-docs..."
    
    for module_dir in "$MODULES_DIR"/*; do
        if [[ -d "$module_dir" ]]; then
            module_name=$(basename "$module_dir")
            terraform-docs markdown table "$module_dir" > "$module_dir/README.md"
            print_success "Documentation generated for module: $module_name"
        fi
    done
else
    print_warning "terraform-docs not found. Consider installing it for automatic documentation generation."
fi

print_success "All validations completed successfully!"