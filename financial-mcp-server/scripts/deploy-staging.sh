#!/bin/bash

# Staging Deployment Script for MCP Financial Server
set -e

echo "🚀 Starting MCP Financial Server Staging Deployment..."

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_DIR/.env.staging"
VERSION=${VERSION:-$(git rev-parse --short HEAD)}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites for staging deployment..."
    
    # Check required tools
    for tool in docker docker-compose git; do
        if ! command -v $tool &> /dev/null; then
            log_error "$tool is not installed or not in PATH"
            exit 1
        fi
    done
    
    # Check environment file
    if [ ! -f "$ENV_FILE" ]; then
        log_error "Staging environment file not found: $ENV_FILE"
        exit 1
    fi
    
    # Check required environment variables
    local required_vars=("JWT_SECRET" "DB_HOST" "DB_USER" "DB_PASSWORD")
    for var in "${required_vars[@]}"; do
        if [ -z "${!var}" ]; then
            log_error "Required environment variable $var is not set"
            exit 1
        fi
    done
    
    log_success "Prerequisites check passed"
}

# Create external resources
create_external_resources() {
    log_info "Creating external resources..."
    
    # Create network if it doesn't exist
    if ! docker network ls | grep -q "financial-staging-network"; then
        log_info "Creating staging network..."
        docker network create financial-staging-network
    fi
    
    # Create volumes if they don't exist
    for volume in prometheus_staging_data grafana_staging_data; do
        if ! docker volume ls | grep -q "$volume"; then
            log_info "Creating volume: $volume"
            docker volume create "$volume"
        fi
    done
    
    log_success "External resources ready"
}

# Build and tag image
build_image() {
    log_info "Building staging image..."
    cd "$PROJECT_DIR"
    
    # Build with version tag
    docker build \
        --target production \
        --tag "mcp-financial-server:staging" \
        --tag "mcp-financial-server:staging-${VERSION}" \
        .
    
    log_success "Image built successfully: mcp-financial-server:staging-${VERSION}"
}

# Deploy services
deploy() {
    log_info "Deploying staging services..."
    cd "$PROJECT_DIR"
    
    # Export version for docker-compose
    export VERSION
    
    # Deploy with zero-downtime strategy
    log_info "Starting new containers..."
    docker-compose -f docker-compose.staging.yml up -d --no-deps mcp-financial-server
    
    # Wait for health check
    log_info "Waiting for service to be healthy..."
    local max_attempts=60
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if docker-compose -f docker-compose.staging.yml exec -T mcp-financial-server curl -f http://localhost:8082/health &> /dev/null; then
            log_success "New service is healthy!"
            break
        fi
        
        if [ $attempt -eq $max_attempts ]; then
            log_error "Health check failed, rolling back..."
            rollback
            exit 1
        fi
        
        sleep 5
        ((attempt++))
    done
    
    # Start monitoring services
    log_info "Starting monitoring services..."
    docker-compose -f docker-compose.staging.yml up -d prometheus-staging grafana-staging
    
    log_success "Staging deployment completed"
}

# Rollback function
rollback() {
    log_warning "Rolling back to previous version..."
    cd "$PROJECT_DIR"
    
    # Stop current containers
    docker-compose -f docker-compose.staging.yml stop mcp-financial-server
    
    # Start previous version (if exists)
    if docker images | grep -q "mcp-financial-server:staging-previous"; then
        docker tag mcp-financial-server:staging-previous mcp-financial-server:staging
        docker-compose -f docker-compose.staging.yml up -d mcp-financial-server
        log_success "Rollback completed"
    else
        log_error "No previous version available for rollback"
    fi
}

# Run tests
run_tests() {
    log_info "Running staging tests..."
    cd "$PROJECT_DIR"
    
    # Run health checks
    if ! curl -f http://localhost:8082/health; then
        log_error "Health check failed"
        return 1
    fi
    
    # Run integration tests (if available)
    if [ -f "tests/staging/run_tests.py" ]; then
        log_info "Running integration tests..."
        python tests/staging/run_tests.py
    fi
    
    log_success "Tests passed"
}

# Cleanup old images
cleanup() {
    log_info "Cleaning up old images..."
    
    # Keep last 3 versions
    docker images --format "table {{.Repository}}:{{.Tag}}" | \
        grep "mcp-financial-server:staging-" | \
        tail -n +4 | \
        xargs -r docker rmi || true
    
    log_success "Cleanup completed"
}

# Show deployment information
show_info() {
    log_info "Staging deployment information:"
    echo ""
    echo "📊 Service Information:"
    echo "  • Version: staging-${VERSION}"
    echo "  • MCP Server: http://staging-mcp.financial-app.com:8082"
    echo "  • Health Check: http://staging-mcp.financial-app.com:8082/health"
    echo "  • Metrics: http://staging-mcp.financial-app.com:9090/metrics"
    echo "  • Prometheus: http://staging-prometheus.financial-app.com:9091"
    echo "  • Grafana: http://staging-grafana.financial-app.com:3000"
    echo ""
    echo "🔧 Management commands:"
    echo "  • View logs: docker-compose -f docker-compose.staging.yml logs -f"
    echo "  • Scale service: docker-compose -f docker-compose.staging.yml up -d --scale mcp-financial-server=2"
    echo "  • Rollback: $0 rollback"
    echo ""
}

# Main execution
main() {
    case "${1:-deploy}" in
        "deploy")
            log_info "MCP Financial Server - Staging Deployment"
            echo "============================================="
            
            check_prerequisites
            create_external_resources
            
            # Tag current as previous (for rollback)
            if docker images | grep -q "mcp-financial-server:staging"; then
                docker tag mcp-financial-server:staging mcp-financial-server:staging-previous
            fi
            
            build_image
            deploy
            run_tests
            cleanup
            show_info
            
            log_success "Staging deployment completed successfully! 🎉"
            ;;
        "rollback")
            log_warning "Rolling back staging deployment..."
            rollback
            ;;
        "test")
            log_info "Running staging tests..."
            run_tests
            ;;
        *)
            echo "Usage: $0 {deploy|rollback|test}"
            exit 1
            ;;
    esac
}

# Handle script interruption
trap 'log_error "Deployment interrupted"; exit 1' INT TERM

# Run main function
main "$@"