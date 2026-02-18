#!/bin/bash

# Development Deployment Script for MCP Financial Server
set -e

echo "🚀 Starting MCP Financial Server Development Deployment..."

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_DIR/.env.dev"

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
    log_info "Checking prerequisites..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed or not in PATH"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose is not installed or not in PATH"
        exit 1
    fi
    
    if [ ! -f "$ENV_FILE" ]; then
        log_error "Environment file not found: $ENV_FILE"
        log_info "Copying from example..."
        cp "$PROJECT_DIR/.env.example" "$ENV_FILE"
        log_warning "Please review and update $ENV_FILE before running again"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Clean up existing containers
cleanup() {
    log_info "Cleaning up existing containers..."
    cd "$PROJECT_DIR"
    
    docker-compose -f docker-compose.dev.yml down --remove-orphans || true
    docker system prune -f --volumes || true
    
    log_success "Cleanup completed"
}

# Build and start services
deploy() {
    log_info "Building and starting development services..."
    cd "$PROJECT_DIR"
    
    # Build the application
    log_info "Building MCP Financial Server..."
    docker-compose -f docker-compose.dev.yml build --no-cache
    
    # Start services
    log_info "Starting services..."
    docker-compose -f docker-compose.dev.yml up -d
    
    log_success "Services started successfully"
}

# Health check
health_check() {
    log_info "Performing health checks..."
    
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        log_info "Health check attempt $attempt/$max_attempts..."
        
        if curl -f http://localhost:8082/health &> /dev/null; then
            log_success "MCP Financial Server is healthy!"
            break
        fi
        
        if [ $attempt -eq $max_attempts ]; then
            log_error "Health check failed after $max_attempts attempts"
            log_info "Checking container logs..."
            docker-compose -f docker-compose.dev.yml logs mcp-financial-server
            exit 1
        fi
        
        sleep 5
        ((attempt++))
    done
}

# Show service information
show_info() {
    log_info "Development environment is ready!"
    echo ""
    echo "📊 Service URLs:"
    echo "  • MCP Financial Server: http://localhost:8082"
    echo "  • Health Check: http://localhost:8082/health"
    echo "  • Metrics: http://localhost:9090/metrics"
    echo "  • Prometheus: http://localhost:9091"
    echo "  • Grafana: http://localhost:3000 (admin/admin)"
    echo "  • PostgreSQL: localhost:5432"
    echo "  • Redis: localhost:6379"
    echo ""
    echo "🔧 Useful commands:"
    echo "  • View logs: docker-compose -f docker-compose.dev.yml logs -f"
    echo "  • Stop services: docker-compose -f docker-compose.dev.yml down"
    echo "  • Restart MCP server: docker-compose -f docker-compose.dev.yml restart mcp-financial-server"
    echo ""
}

# Main execution
main() {
    log_info "MCP Financial Server - Development Deployment"
    echo "=============================================="
    
    check_prerequisites
    cleanup
    deploy
    health_check
    show_info
    
    log_success "Development deployment completed successfully! 🎉"
}

# Handle script interruption
trap 'log_error "Deployment interrupted"; exit 1' INT TERM

# Run main function
main "$@"