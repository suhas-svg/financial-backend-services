#!/bin/bash

# Terraform Deployment Script
# This script helps deploy infrastructure to different environments

set -e

# Default values
ENVIRONMENT=""
ACTION="plan"
AUTO_APPROVE=false
DESTROY=false

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

# Function to show usage
show_usage() {
    echo "Usage: $0 -e ENVIRONMENT [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -e, --environment    Environment to deploy (dev, staging, prod)"
    echo "  -a, --action         Action to perform (plan, apply, destroy) [default: plan]"
    echo "  -y, --auto-approve   Auto approve apply/destroy operations"
    echo "  -h, --help          Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -e dev                    # Plan deployment to dev environment"
    echo "  $0 -e staging -a apply       # Apply changes to staging environment"
    echo "  $0 -e prod -a apply -y       # Apply changes to prod with auto-approve"
    echo "  $0 -e dev -a destroy         # Destroy dev environment"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -a|--action)
            ACTION="$2"
            shift 2
            ;;
        -y|--auto-approve)
            AUTO_APPROVE=true
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Validate required parameters
if [[ -z "$ENVIRONMENT" ]]; then
    print_error "Environment is required"
    show_usage
    exit 1
fi

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
    print_error "Environment must be one of: dev, staging, prod"
    exit 1
fi

# Validate action
if [[ ! "$ACTION" =~ ^(plan|apply|destroy)$ ]]; then
    print_error "Action must be one of: plan, apply, destroy"
    exit 1
fi

# Set working directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$SCRIPT_DIR/../environments/$ENVIRONMENT"

if [[ ! -d "$TERRAFORM_DIR" ]]; then
    print_error "Environment directory not found: $TERRAFORM_DIR"
    exit 1
fi

print_status "Changing to directory: $TERRAFORM_DIR"
cd "$TERRAFORM_DIR"

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

# Check Kubernetes connectivity
if ! kubectl cluster-info &> /dev/null; then
    print_error "Cannot connect to Kubernetes cluster"
    print_warning "Make sure your kubectl context is set correctly"
    exit 1
fi

print_success "Prerequisites check passed"

# Initialize Terraform
print_status "Initializing Terraform..."
terraform init

# Validate Terraform configuration
print_status "Validating Terraform configuration..."
terraform validate

# Format check
print_status "Checking Terraform formatting..."
if ! terraform fmt -check=true -diff=true; then
    print_warning "Terraform files are not properly formatted"
    print_status "Running terraform fmt to fix formatting..."
    terraform fmt
fi

# Execute the requested action
case $ACTION in
    plan)
        print_status "Running Terraform plan for $ENVIRONMENT environment..."
        terraform plan -var="environment=$ENVIRONMENT"
        ;;
    apply)
        print_status "Running Terraform apply for $ENVIRONMENT environment..."
        if [[ "$AUTO_APPROVE" == true ]]; then
            terraform apply -var="environment=$ENVIRONMENT" -auto-approve
        else
            terraform apply -var="environment=$ENVIRONMENT"
        fi
        
        if [[ $? -eq 0 ]]; then
            print_success "Infrastructure deployment completed successfully!"
            print_status "Getting deployment information..."
            terraform output
        else
            print_error "Infrastructure deployment failed!"
            exit 1
        fi
        ;;
    destroy)
        print_warning "This will destroy all infrastructure in the $ENVIRONMENT environment!"
        
        if [[ "$AUTO_APPROVE" != true ]]; then
            read -p "Are you sure you want to continue? (yes/no): " confirm
            if [[ "$confirm" != "yes" ]]; then
                print_status "Operation cancelled"
                exit 0
            fi
        fi
        
        print_status "Running Terraform destroy for $ENVIRONMENT environment..."
        if [[ "$AUTO_APPROVE" == true ]]; then
            terraform destroy -var="environment=$ENVIRONMENT" -auto-approve
        else
            terraform destroy -var="environment=$ENVIRONMENT"
        fi
        
        if [[ $? -eq 0 ]]; then
            print_success "Infrastructure destruction completed successfully!"
        else
            print_error "Infrastructure destruction failed!"
            exit 1
        fi
        ;;
esac

print_success "Operation completed successfully!"