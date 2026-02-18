#!/bin/bash

# Production Deployment Script for MCP Financial Server
set -e

echo "🚀 Starting MCP Financial Server Production Deployment..."

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_DIR/.env.prod"
VERSION=${VERSION:-$(git rev-parse --short HEAD)}
REGISTRY=${REGISTRY:-"your-registry.com"}

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
    log_info "Checking prerequisites for production deployment..."
    
    # Check required tools
    for tool in docker docker-compose git; do
        if ! command -v $tool &> /dev/null; then
            log_error "$tool is not installed or not in PATH"
            exit 1
        fi
    done
    
    # Check environment file
    if [ ! -f "$ENV_FILE" ]; then
        log_error "Production environment file not found: $ENV_FILE"
        exit 1
    fi
    
    # Check critical environment variables
    local required_vars=("JWT_SECRET" "DB_HOST" "DB_USER" "DB_PASSWORD" "REDIS_URL")
    for var in "${required_vars[@]}"; do
        if [ -z "${!var}" ]; then
            log_error "Required environment variable $var is not set"
            exit 1
        fi
    done
    
    # Check if we're on the correct branch
    local current_branch=$(git rev-parse --abbrev-ref HEAD)
    if [ "$current_branch" != "main" ] && [ "$current_branch" != "master" ]; then
        log_warning "Not on main/master branch. Current branch: $current_branch"
        read -p "Continue with production deployment? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Deployment cancelled"
            exit 0
        fi
    fi
    
    log_success "Prerequisites check passed"
}

# Pre-deployment validation
pre_deployment_validation() {
    log_info "Running pre-deployment validation..."
    
    # Check if staging tests passed
    if [ -f "tests/staging/results.json" ]; then
        local test_status=$(jq -r '.status' tests/staging/results.json)
        if [ "$test_status" != "passed" ]; then
            log_error "Staging tests have not passed. Cannot deploy to production."
            exit 1
        fi
    else
        log_warning "No staging test results found"
    fi
    
    # Validate configuration
    log_info "Validating production configuration..."
    if ! python scripts/validate-config.py --env production; then
        log_error "Configuration validation failed"
        exit 1
    fi
    
    log_success "Pre-deployment validation passed"
}

# Build and push image
build_and_push_image() {
    log_info "Building and pushing production image..."
    cd "$PROJECT_DIR"
    
    local image_name="$REGISTRY/mcp-financial-server"
    local full_tag="$image_name:$VERSION"
    local latest_tag="$image_name:latest"
    
    # Build production image
    docker build \
        --target production \
        --tag "$full_tag" \
        --tag "$latest_tag" \
        --build-arg VERSION="$VERSION" \
        --build-arg BUILD_DATE="$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
        .
    
    # Push to registry
    log_info "Pushing image to registry..."
    docker push "$full_tag"
    docker push "$latest_tag"
    
    log_success "Image pushed successfully: $full_tag"
}

# Blue-green deployment
blue_green_deploy() {
    log_info "Starting blue-green deployment..."
    cd "$PROJECT_DIR"
    
    # Export version for docker-compose
    export VERSION
    
    # Determine current and new colors
    local current_color="blue"
    local new_color="green"
    
    if docker-compose -f docker-compose.prod.yml ps | grep -q "green"; then
        current_color="green"
        new_color="blue"
    fi
    
    log_info "Deploying to $new_color environment..."
    
    # Start new environment
    docker-compose -f docker-compose.prod.yml up -d --scale mcp-financial-server=2
    
    # Wait for health check
    log_info "Waiting for new deployment to be healthy..."
    local max_attempts=120
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        local healthy_count=$(docker-compose -f docker-compose.prod.yml ps mcp-financial-server | grep "healthy" | wc -l)
        
        if [ "$healthy_count" -ge 2 ]; then
            log_success "New deployment is healthy!"
            break
        fi
        
        if [ $attempt -eq $max_attempts ]; then
            log_error "Health check failed, rolling back..."
            rollback_deployment
            exit 1
        fi
        
        sleep 5
        ((attempt++))
    done
    
    # Switch traffic (this would typically be done via load balancer)
    log_info "Switching traffic to new deployment..."
    # In a real scenario, this would update load balancer configuration
    
    # Stop old deployment after successful switch
    log_info "Stopping old deployment..."
    # docker-compose -f docker-compose.prod.yml stop old_containers
    
    log_success "Blue-green deployment completed"
}

# Rollback deployment
rollback_deployment() {
    log_warning "Rolling back production deployment..."
    cd "$PROJECT_DIR"
    
    # Get previous version
    local previous_version=$(docker images --format "table {{.Tag}}" $REGISTRY/mcp-financial-server | grep -v "latest" | head -2 | tail -1)
    
    if [ -n "$previous_version" ]; then
        log_info "Rolling back to version: $previous_version"
        export VERSION="$previous_version"
        docker-compose -f docker-compose.prod.yml up -d
        log_success "Rollback completed"
    else
        log_error "No previous version available for rollback"
        exit 1
    fi
}

# Run production tests
run_production_tests() {
    log_info "Running production smoke tests..."
    
    # Basic health check
    if ! curl -f http://localhost:8082/health; then
        log_error "Health check failed"
        return 1
    fi
    
    # Run critical path tests
    if [ -f "tests/production/smoke_tests.py" ]; then
        log_info "Running smoke tests..."
        python tests/production/smoke_tests.py
    fi
    
    # Check metrics endpoint
    if ! curl -f http://localhost:9090/metrics; then
        log_error "Metrics endpoint check failed"
        return 1
    fi
    
    log_success "Production tests passed"
}

# Post-deployment tasks
post_deployment() {
    log_info "Running post-deployment tasks..."
    
    # Update monitoring alerts
    if [ -f "scripts/update-alerts.sh" ]; then
        ./scripts/update-alerts.sh production
    fi
    
    # Notify deployment
    if [ -n "$SLACK_WEBHOOK" ]; then
        curl -X POST -H 'Content-type: application/json' \
            --data "{\"text\":\"🚀 MCP Financial Server v$VERSION deployed to production successfully!\"}" \
            "$SLACK_WEBHOOK"
    fi
    
    # Update deployment record
    echo "{\"version\":\"$VERSION\",\"timestamp\":\"$(date -u +'%Y-%m-%dT%H:%M:%SZ')\",\"status\":\"success\"}" > deployment-record.json
    
    log_success "Post-deployment tasks completed"
}

# Show deployment information
show_info() {
    log_info "Production deployment information:"
    echo ""
    echo "📊 Service Information:"
    echo "  • Version: $VERSION"
    echo "  • MCP Server: https://mcp.financial-app.com"
    echo "  • Health Check: https://mcp.financial-app.com/health"
    echo "  • Metrics: https://metrics.financial-app.com"
    echo "  • Monitoring: https://monitoring.financial-app.com"
    echo ""
    echo "🔧 Management commands:"
    echo "  • View logs: docker-compose -f docker-compose.prod.yml logs -f"
    echo "  • Scale service: docker-compose -f docker-compose.prod.yml up -d --scale mcp-financial-server=4"
    echo "  • Rollback: $0 rollback"
    echo "  • Status: $0 status"
    echo ""
}

# Check deployment status
check_status() {
    log_info "Checking production deployment status..."
    
    # Check container status
    docker-compose -f docker-compose.prod.yml ps
    
    # Check health
    curl -s http://localhost:8082/health | jq .
    
    # Check metrics
    echo "Active connections: $(curl -s http://localhost:9090/metrics | grep mcp_active_connections | tail -1)"
    echo "Total requests: $(curl -s http://localhost:9090/metrics | grep mcp_requests_total | tail -1)"
}

# Main execution
main() {
    case "${1:-deploy}" in
        "deploy")
            log_info "MCP Financial Server - Production Deployment"
            echo "=============================================="
            
            # Confirmation prompt
            log_warning "You are about to deploy to PRODUCTION!"
            read -p "Are you sure you want to continue? (yes/no): " -r
            if [ "$REPLY" != "yes" ]; then
                log_info "Deployment cancelled"
                exit 0
            fi
            
            check_prerequisites
            pre_deployment_validation
            build_and_push_image
            blue_green_deploy
            run_production_tests
            post_deployment
            show_info
            
            log_success "Production deployment completed successfully! 🎉"
            ;;
        "rollback")
            log_warning "Rolling back production deployment..."
            rollback_deployment
            ;;
        "test")
            log_info "Running production tests..."
            run_production_tests
            ;;
        "status")
            check_status
            ;;
        *)
            echo "Usage: $0 {deploy|rollback|test|status}"
            exit 1
            ;;
    esac
}

# Handle script interruption
trap 'log_error "Deployment interrupted"; exit 1' INT TERM

# Run main function
main "$@"