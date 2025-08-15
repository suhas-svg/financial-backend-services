#!/bin/bash

# Development Environment Deployment Script
# This script deploys the account service to the development environment

set -e

# Configuration
NAMESPACE="finance-services-dev"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIMEOUT=300  # 5 minutes timeout for deployments

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS") echo -e "${GREEN}✓ $message${NC}" ;;
        "ERROR") echo -e "${RED}✗ $message${NC}" ;;
        "INFO") echo -e "${BLUE}ℹ $message${NC}" ;;
        "WARNING") echo -e "${YELLOW}⚠ $message${NC}" ;;
    esac
}

# Function to check if kubectl is available
check_kubectl() {
    if ! command -v kubectl &> /dev/null; then
        print_status "ERROR" "kubectl is not installed or not in PATH"
        exit 1
    fi
    
    # Check if we can connect to the cluster
    if ! kubectl cluster-info &> /dev/null; then
        print_status "ERROR" "Cannot connect to Kubernetes cluster"
        exit 1
    fi
    
    print_status "SUCCESS" "kubectl is available and connected to cluster"
}

# Function to create namespace if it doesn't exist
create_namespace() {
    print_status "INFO" "Creating namespace: $NAMESPACE"
    
    if kubectl get namespace "$NAMESPACE" &> /dev/null; then
        print_status "INFO" "Namespace $NAMESPACE already exists"
    else
        kubectl apply -f "$SCRIPT_DIR/namespace.yaml"
        print_status "SUCCESS" "Namespace $NAMESPACE created"
    fi
}

# Function to apply Kubernetes manifests
apply_manifests() {
    local manifest_file=$1
    local description=$2
    
    print_status "INFO" "Applying $description..."
    
    if kubectl apply -f "$manifest_file"; then
        print_status "SUCCESS" "$description applied successfully"
    else
        print_status "ERROR" "Failed to apply $description"
        return 1
    fi
}

# Function to wait for deployment to be ready
wait_for_deployment() {
    local deployment_name=$1
    local description=$2
    
    print_status "INFO" "Waiting for $description to be ready..."
    
    if kubectl wait --for=condition=available --timeout=${TIMEOUT}s deployment/$deployment_name -n $NAMESPACE; then
        print_status "SUCCESS" "$description is ready"
    else
        print_status "ERROR" "$description failed to become ready within ${TIMEOUT} seconds"
        
        # Show deployment status for debugging
        print_status "INFO" "Deployment status:"
        kubectl get deployment $deployment_name -n $NAMESPACE -o wide
        
        print_status "INFO" "Pod status:"
        kubectl get pods -n $NAMESPACE -l app=${deployment_name%-deployment}
        
        print_status "INFO" "Recent events:"
        kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' | tail -10
        
        return 1
    fi
}

# Function to run smoke tests
run_smoke_tests() {
    print_status "INFO" "Running smoke tests..."
    
    # Delete any existing smoke test job
    kubectl delete job smoke-tests -n $NAMESPACE --ignore-not-found=true
    
    # Apply smoke test job
    if kubectl apply -f "$SCRIPT_DIR/smoke-tests.yaml"; then
        print_status "SUCCESS" "Smoke test job created"
        
        # Wait for job to complete
        print_status "INFO" "Waiting for smoke tests to complete..."
        if kubectl wait --for=condition=complete --timeout=300s job/smoke-tests -n $NAMESPACE; then
            print_status "SUCCESS" "Smoke tests completed successfully"
            
            # Show test results
            print_status "INFO" "Smoke test results:"
            kubectl logs job/smoke-tests -n $NAMESPACE
            
            return 0
        else
            print_status "ERROR" "Smoke tests failed or timed out"
            
            # Show test logs for debugging
            print_status "INFO" "Smoke test logs:"
            kubectl logs job/smoke-tests -n $NAMESPACE
            
            return 1
        fi
    else
        print_status "ERROR" "Failed to create smoke test job"
        return 1
    fi
}

# Function to show deployment status
show_deployment_status() {
    print_status "INFO" "Deployment Status Summary:"
    echo "========================================="
    
    # Show namespace status
    echo "Namespace:"
    kubectl get namespace $NAMESPACE
    echo ""
    
    # Show deployments
    echo "Deployments:"
    kubectl get deployments -n $NAMESPACE -o wide
    echo ""
    
    # Show services
    echo "Services:"
    kubectl get services -n $NAMESPACE -o wide
    echo ""
    
    # Show pods
    echo "Pods:"
    kubectl get pods -n $NAMESPACE -o wide
    echo ""
    
    # Show persistent volume claims
    echo "Persistent Volume Claims:"
    kubectl get pvc -n $NAMESPACE
    echo ""
    
    # Show recent events
    echo "Recent Events:"
    kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' | tail -10
    echo ""
}

# Function to get service URLs
get_service_urls() {
    print_status "INFO" "Service Access Information:"
    echo "========================================="
    
    # Get service information
    local service_ip=$(kubectl get service account-service -n $NAMESPACE -o jsonpath='{.spec.clusterIP}')
    local actuator_ip=$(kubectl get service account-service-actuator -n $NAMESPACE -o jsonpath='{.spec.clusterIP}')
    
    echo "Account Service: http://$service_ip:8080"
    echo "Actuator Service: http://$actuator_ip:9001"
    echo ""
    echo "Health Check: http://$actuator_ip:9001/actuator/health"
    echo "Metrics: http://$actuator_ip:9001/actuator/metrics"
    echo "Prometheus: http://$actuator_ip:9001/actuator/prometheus"
    echo ""
    
    # Port forwarding instructions
    echo "To access from outside the cluster, use port forwarding:"
    echo "kubectl port-forward service/account-service 8080:8080 -n $NAMESPACE"
    echo "kubectl port-forward service/account-service-actuator 9001:9001 -n $NAMESPACE"
    echo ""
}

# Function to cleanup on failure
cleanup_on_failure() {
    print_status "WARNING" "Deployment failed. Cleaning up..."
    
    # Delete smoke test job if it exists
    kubectl delete job smoke-tests -n $NAMESPACE --ignore-not-found=true
    
    # Optionally rollback deployment (uncomment if needed)
    # kubectl rollout undo deployment/account-service-deployment -n $NAMESPACE
    
    print_status "INFO" "Cleanup completed"
}

# Main deployment function
main() {
    echo "========================================="
    echo "Account Service Development Deployment"
    echo "========================================="
    echo "Namespace: $NAMESPACE"
    echo "Timestamp: $(date)"
    echo "========================================="
    
    # Check prerequisites
    check_kubectl
    
    # Create namespace
    create_namespace
    
    # Apply manifests in order
    print_status "INFO" "Deploying infrastructure components..."
    
    # 1. Secrets and RBAC
    apply_manifests "$SCRIPT_DIR/secrets.yaml" "Secrets and RBAC" || { cleanup_on_failure; exit 1; }
    
    # 2. ConfigMaps
    apply_manifests "$SCRIPT_DIR/configmap.yaml" "ConfigMaps" || { cleanup_on_failure; exit 1; }
    
    # 3. PostgreSQL Database
    apply_manifests "$SCRIPT_DIR/postgres-deployment.yaml" "PostgreSQL Database" || { cleanup_on_failure; exit 1; }
    
    # Wait for PostgreSQL to be ready
    wait_for_deployment "postgres-deployment" "PostgreSQL Database" || { cleanup_on_failure; exit 1; }
    
    # 4. Account Service Application
    apply_manifests "$SCRIPT_DIR/deployment.yaml" "Account Service Application" || { cleanup_on_failure; exit 1; }
    
    # Wait for application to be ready
    wait_for_deployment "account-service-deployment" "Account Service Application" || { cleanup_on_failure; exit 1; }
    
    # Run smoke tests
    if run_smoke_tests; then
        print_status "SUCCESS" "Development environment deployment completed successfully! 🎉"
    else
        print_status "WARNING" "Deployment completed but smoke tests failed"
        print_status "INFO" "The application may still be starting up. Check the logs and try again."
    fi
    
    # Show deployment status
    show_deployment_status
    
    # Show service URLs
    get_service_urls
    
    print_status "SUCCESS" "Development environment is ready for use!"
}

# Handle script interruption
trap cleanup_on_failure INT TERM

# Execute main function
main "$@"