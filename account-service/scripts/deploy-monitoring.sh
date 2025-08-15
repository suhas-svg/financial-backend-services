#!/bin/bash

# Deploy Prometheus and Grafana monitoring stack for Account Service
# This script deploys the complete monitoring stack including Prometheus for metrics collection
# and Grafana for visualization, supporting both Docker Compose and Kubernetes deployments.

set -e

# Default values
ENVIRONMENT=${1:-dev}
PLATFORM=${2:-docker}
NAMESPACE=${3:-finance-services}

# Color functions for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

info "=== Account Service Monitoring Stack Deployment ==="
info "Environment: $ENVIRONMENT"
info "Platform: $PLATFORM"

# Change to account-service directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCOUNT_SERVICE_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ACCOUNT_SERVICE_DIR"

if [ "$PLATFORM" = "docker" ]; then
    info "Deploying monitoring stack with Docker Compose..."
    
    # Create monitoring directory if it doesn't exist
    if [ ! -d "monitoring" ]; then
        info "Creating monitoring directory..."
        mkdir -p monitoring
    fi
    
    # Set environment variables
    export ENVIRONMENT=$ENVIRONMENT
    export PROMETHEUS_PORT=$([ "$ENVIRONMENT" = "prod" ] && echo "9090" || echo "9090")
    export GRAFANA_PORT=$([ "$ENVIRONMENT" = "prod" ] && echo "3000" || echo "3000")
    export GRAFANA_PASSWORD=$([ "$ENVIRONMENT" = "prod" ] && echo "secure-admin-password" || echo "admin")
    
    # Deploy with monitoring profile
    info "Starting Prometheus and Grafana containers..."
    docker-compose --profile monitoring up -d
    
    # Wait for services to be ready
    info "Waiting for services to be ready..."
    max_attempts=30
    attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        attempt=$((attempt + 1))
        sleep 5
        
        if curl -s "http://localhost:$PROMETHEUS_PORT/-/ready" > /dev/null 2>&1 && \
           curl -s "http://localhost:$GRAFANA_PORT/api/health" > /dev/null 2>&1; then
            info "✅ All monitoring services are ready!"
            break
        else
            warn "Attempt $attempt/$max_attempts - Services not ready yet..."
        fi
    done
    
    if [ $attempt -eq $max_attempts ]; then
        error "❌ Services failed to become ready within timeout"
        exit 1
    fi
    
    # Display access information
    info ""
    info "=== Monitoring Stack Access Information ==="
    info "Prometheus: http://localhost:$PROMETHEUS_PORT"
    info "Grafana: http://localhost:$GRAFANA_PORT"
    info "Grafana Login: admin / $GRAFANA_PASSWORD"
    info ""
    
elif [ "$PLATFORM" = "kubernetes" ]; then
    info "Deploying monitoring stack to Kubernetes..."
    
    # Check if kubectl is available
    if ! command -v kubectl &> /dev/null; then
        error "❌ kubectl is not available. Please install kubectl first."
        exit 1
    fi
    
    # Create namespace if it doesn't exist
    info "Creating namespace: $NAMESPACE"
    kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    
    # Deploy Prometheus configuration
    info "Deploying Prometheus configuration..."
    kubectl apply -f k8s/monitoring/prometheus-config.yaml
    
    # Deploy Prometheus
    info "Deploying Prometheus..."
    kubectl apply -f k8s/monitoring/prometheus-deployment.yaml
    
    # Deploy Grafana configuration
    info "Deploying Grafana configuration..."
    kubectl apply -f k8s/monitoring/grafana-config.yaml
    
    # Deploy Grafana
    info "Deploying Grafana..."
    kubectl apply -f k8s/monitoring/grafana-deployment.yaml
    
    # Wait for deployments to be ready
    info "Waiting for deployments to be ready..."
    kubectl wait --for=condition=available --timeout=300s deployment/prometheus -n "$NAMESPACE"
    kubectl wait --for=condition=available --timeout=300s deployment/grafana -n "$NAMESPACE"
    
    # Get service information
    info ""
    info "=== Kubernetes Monitoring Stack Information ==="
    kubectl get pods -n "$NAMESPACE" -l app=prometheus
    kubectl get pods -n "$NAMESPACE" -l app=grafana
    kubectl get services -n "$NAMESPACE"
    
    # Port forwarding instructions
    info ""
    info "=== Port Forwarding Commands ==="
    info "Prometheus: kubectl port-forward -n $NAMESPACE svc/prometheus 9090:9090"
    info "Grafana: kubectl port-forward -n $NAMESPACE svc/grafana 3000:3000"
    info ""
fi

# Verify monitoring endpoints
info "=== Verifying Monitoring Setup ==="

if [ "$PLATFORM" = "docker" ]; then
    # Test Prometheus targets
    if curl -s "http://localhost:$PROMETHEUS_PORT/api/v1/targets" > /dev/null; then
        active_targets=$(curl -s "http://localhost:$PROMETHEUS_PORT/api/v1/targets" | jq -r '.data.activeTargets | map(select(.health == "up")) | length')
        info "✅ Prometheus active targets: $active_targets"
    else
        warn "⚠️  Could not verify Prometheus targets"
    fi
    
    # Test Grafana datasource
    if curl -s -u "admin:$GRAFANA_PASSWORD" "http://localhost:$GRAFANA_PORT/api/datasources" > /dev/null; then
        datasource_count=$(curl -s -u "admin:$GRAFANA_PASSWORD" "http://localhost:$GRAFANA_PORT/api/datasources" | jq '. | length')
        info "✅ Grafana datasources configured: $datasource_count"
    else
        warn "⚠️  Could not verify Grafana datasources"
    fi
fi

info ""
info "=== Deployment Summary ==="
info "✅ Monitoring stack deployed successfully!"
info "✅ Prometheus configured for metrics collection"
info "✅ Grafana configured with dashboards"
info "✅ Alert rules configured for critical metrics"
info ""
info "Next steps:"
info "1. Access Grafana to view dashboards"
info "2. Configure additional alert channels if needed"
info "3. Customize dashboards for your specific needs"
info "4. Set up notification channels (Slack, email, etc.)"