#!/bin/bash

# E2E Environment Management Script
# This script provides orchestration for the E2E testing environment

set -e

# Configuration
COMPOSE_FILE="docker-compose-e2e.yml"
NETWORK_NAME="e2e-network"
MAX_WAIT_TIME=300  # 5 minutes
HEALTH_CHECK_INTERVAL=10

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
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

# Function to check if Docker is running
check_docker() {
    log_info "Checking Docker availability..."
    if ! docker info >/dev/null 2>&1; then
        log_error "Docker is not running or not accessible"
        exit 1
    fi
    log_success "Docker is available"
}

# Function to check if Docker Compose is available
check_docker_compose() {
    log_info "Checking Docker Compose availability..."
    if ! docker compose version >/dev/null 2>&1; then
        log_error "Docker Compose is not available"
        exit 1
    fi
    log_success "Docker Compose is available"
}

# Function to wait for service health
wait_for_service_health() {
    local service_name=$1
    local max_attempts=$((MAX_WAIT_TIME / HEALTH_CHECK_INTERVAL))
    local attempt=1
    
    log_info "Waiting for $service_name to become healthy..."
    
    while [ $attempt -le $max_attempts ]; do
        if docker compose -f $COMPOSE_FILE ps --format json | jq -r ".[] | select(.Service == \"$service_name\") | .Health" | grep -q "healthy"; then
            log_success "$service_name is healthy"
            return 0
        fi
        
        log_info "Attempt $attempt/$max_attempts: $service_name not ready yet, waiting ${HEALTH_CHECK_INTERVAL}s..."
        sleep $HEALTH_CHECK_INTERVAL
        attempt=$((attempt + 1))
    done
    
    log_error "$service_name failed to become healthy within ${MAX_WAIT_TIME}s"
    return 1
}

# Function to wait for all core services
wait_for_core_services() {
    log_info "Waiting for core infrastructure services..."
    
    # Wait for databases
    wait_for_service_health "postgres-account-e2e" || return 1
    wait_for_service_health "postgres-transaction-e2e" || return 1
    
    # Wait for cache
    wait_for_service_health "redis-e2e" || return 1
    
    log_info "Waiting for application services..."
    
    # Wait for applications
    wait_for_service_health "account-service-e2e" || return 1
    wait_for_service_health "transaction-service-e2e" || return 1
    
    log_success "All core services are healthy"
}

# Function to validate environment
validate_environment() {
    log_info "Validating E2E environment..."
    
    if docker compose -f $COMPOSE_FILE --profile validate up --exit-code-from e2e-validator e2e-validator; then
        log_success "Environment validation passed"
        return 0
    else
        log_error "Environment validation failed"
        return 1
    fi
}

# Function to setup test data
setup_test_data() {
    log_info "Setting up E2E test data..."
    
    if docker compose -f $COMPOSE_FILE --profile setup-data up --exit-code-from e2e-data-setup e2e-data-setup; then
        log_success "Test data setup completed"
        return 0
    else
        log_warning "Test data setup failed or no test data files found"
        return 0  # Non-critical failure
    fi
}

# Function to cleanup environment
cleanup_environment() {
    log_info "Cleaning up E2E environment..."
    
    # Run cleanup service
    docker compose -f $COMPOSE_FILE --profile cleanup up --exit-code-from e2e-cleanup e2e-cleanup 2>/dev/null || true
    
    # Stop and remove containers
    docker compose -f $COMPOSE_FILE down --volumes --remove-orphans 2>/dev/null || true
    
    # Remove custom network if it exists
    docker network rm $NETWORK_NAME 2>/dev/null || true
    
    # Prune unused volumes (with confirmation)
    if [ "$1" = "--prune-volumes" ]; then
        log_warning "Pruning all unused Docker volumes..."
        docker volume prune -f
    fi
    
    log_success "Environment cleanup completed"
}

# Function to show service status
show_status() {
    log_info "E2E Environment Status:"
    echo
    
    if docker compose -f $COMPOSE_FILE ps --format table; then
        echo
        log_info "Service Health Status:"
        docker compose -f $COMPOSE_FILE ps --format json | jq -r '.[] | "\(.Service): \(.State) (\(.Health // "no health check"))"'
    else
        log_warning "No services are currently running"
    fi
}

# Function to show logs
show_logs() {
    local service=$1
    if [ -n "$service" ]; then
        log_info "Showing logs for $service..."
        docker compose -f $COMPOSE_FILE logs -f "$service"
    else
        log_info "Showing logs for all services..."
        docker compose -f $COMPOSE_FILE logs -f
    fi
}

# Function to start environment
start_environment() {
    log_info "Starting E2E testing environment..."
    
    # Check prerequisites
    check_docker
    check_docker_compose
    
    # Start core services
    log_info "Starting infrastructure services..."
    docker compose -f $COMPOSE_FILE up -d postgres-account-e2e postgres-transaction-e2e redis-e2e
    
    # Wait for infrastructure
    wait_for_service_health "postgres-account-e2e" || exit 1
    wait_for_service_health "postgres-transaction-e2e" || exit 1
    wait_for_service_health "redis-e2e" || exit 1
    
    # Start application services
    log_info "Starting application services..."
    docker compose -f $COMPOSE_FILE up -d account-service-e2e transaction-service-e2e
    
    # Wait for applications
    wait_for_service_health "account-service-e2e" || exit 1
    wait_for_service_health "transaction-service-e2e" || exit 1
    
    # Validate environment
    validate_environment || exit 1
    
    # Setup test data
    setup_test_data
    
    log_success "E2E environment is ready for testing!"
    echo
    log_info "Service URLs:"
    echo "  Account Service: http://localhost:8083"
    echo "  Transaction Service: http://localhost:8082"
    echo "  Account Database: localhost:5434"
    echo "  Transaction Database: localhost:5435"
    echo "  Redis Cache: localhost:6380"
}

# Function to stop environment
stop_environment() {
    log_info "Stopping E2E testing environment..."
    docker compose -f $COMPOSE_FILE down
    log_success "E2E environment stopped"
}

# Function to restart environment
restart_environment() {
    log_info "Restarting E2E testing environment..."
    stop_environment
    start_environment
}

# Function to reset environment (full cleanup and restart)
reset_environment() {
    log_info "Resetting E2E testing environment..."
    cleanup_environment
    start_environment
}

# Main script logic
case "$1" in
    start)
        start_environment
        ;;
    stop)
        stop_environment
        ;;
    restart)
        restart_environment
        ;;
    reset)
        reset_environment
        ;;
    cleanup)
        cleanup_environment "$2"
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs "$2"
        ;;
    validate)
        validate_environment
        ;;
    setup-data)
        setup_test_data
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|reset|cleanup|status|logs|validate|setup-data}"
        echo
        echo "Commands:"
        echo "  start       - Start the E2E testing environment"
        echo "  stop        - Stop the E2E testing environment"
        echo "  restart     - Restart the E2E testing environment"
        echo "  reset       - Full reset (cleanup + start)"
        echo "  cleanup     - Clean up environment and resources"
        echo "              - Use 'cleanup --prune-volumes' to also remove unused volumes"
        echo "  status      - Show current status of all services"
        echo "  logs        - Show logs for all services"
        echo "              - Use 'logs <service-name>' for specific service"
        echo "  validate    - Validate environment readiness"
        echo "  setup-data  - Setup test data"
        echo
        echo "Examples:"
        echo "  $0 start                    # Start E2E environment"
        echo "  $0 logs account-service-e2e # Show Account Service logs"
        echo "  $0 cleanup --prune-volumes # Full cleanup including volumes"
        exit 1
        ;;
esac