#!/bin/bash

# E2E Infrastructure Setup Script
# This script sets up the complete E2E testing infrastructure using Docker Compose

set -e

# Configuration
E2E_COMPOSE_FILE="../docker-compose-e2e.yml"
E2E_ENV_FILE=".env.e2e"
PROJECT_NAME="e2e-financial-services"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

function print_header() {
    echo -e "\n${CYAN}=== $1 ===${NC}"
}

function print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

function print_error() {
    echo -e "${RED}❌ $1${NC}"
}

function print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

function print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

function check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        print_error "Docker is not running"
        exit 1
    fi
}

function check_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed"
        exit 1
    fi
}

function start_e2e_infrastructure() {
    print_header "Starting E2E Infrastructure"
    
    # Check prerequisites
    check_docker
    check_docker_compose
    
    # Check if compose file exists
    if [ ! -f "$E2E_COMPOSE_FILE" ]; then
        print_error "E2E Docker Compose file not found: $E2E_COMPOSE_FILE"
        exit 1
    fi
    
    print_info "Using Docker Compose file: $E2E_COMPOSE_FILE"
    print_info "Using project name: $PROJECT_NAME"
    
    # Start the infrastructure
    print_info "Starting E2E infrastructure services..."
    docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" up -d --build
    
    print_info "Waiting for services to be healthy..."
    sleep 10
    
    # Wait for databases to be ready
    print_info "Waiting for PostgreSQL databases..."
    local max_attempts=30
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        attempt=$((attempt + 1))
        echo -n "."
        
        if docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" exec -T postgres-account-e2e pg_isready -U e2e_user -d account_db_e2e &>/dev/null && \
           docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" exec -T postgres-transaction-e2e pg_isready -U e2e_user -d transaction_db_e2e &>/dev/null; then
            echo ""
            print_success "PostgreSQL databases are ready"
            break
        fi
        
        sleep 2
    done
    
    if [ $attempt -ge $max_attempts ]; then
        print_warning "Databases may not be fully ready, but continuing..."
    fi
    
    # Wait for Redis
    print_info "Waiting for Redis..."
    attempt=0
    while [ $attempt -lt 15 ]; do
        attempt=$((attempt + 1))
        echo -n "."
        
        if docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" exec -T redis-e2e redis-cli -a e2e_redis_password ping 2>/dev/null | grep -q "PONG"; then
            echo ""
            print_success "Redis is ready"
            break
        fi
        
        sleep 2
    done
    
    # Wait for services
    print_info "Waiting for application services..."
    sleep 30
    
    print_success "E2E Infrastructure started successfully"
    print_info "Services are available at:"
    print_info "  Account Service: http://localhost:8083"
    print_info "  Transaction Service: http://localhost:8082"
    print_info "  Account Database: localhost:5434"
    print_info "  Transaction Database: localhost:5435"
    print_info "  Redis: localhost:6380"
}

function stop_e2e_infrastructure() {
    print_header "Stopping E2E Infrastructure"
    
    docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" down
    print_success "E2E Infrastructure stopped successfully"
}

function restart_e2e_infrastructure() {
    print_header "Restarting E2E Infrastructure"
    stop_e2e_infrastructure
    sleep 5
    start_e2e_infrastructure
}

function get_e2e_status() {
    print_header "E2E Infrastructure Status"
    
    docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" ps
}

function show_e2e_logs() {
    print_header "E2E Infrastructure Logs"
    
    if [ -n "$SERVICE" ]; then
        docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" logs -f "$SERVICE"
    else
        docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" logs -f
    fi
}

function clean_e2e_infrastructure() {
    print_header "Cleaning E2E Infrastructure"
    
    # Stop and remove containers, networks, and volumes
    docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" down -v --remove-orphans
    
    # Remove images
    print_info "Removing E2E images..."
    docker image rm account-service:e2e-latest 2>/dev/null || true
    docker image rm transaction-service:e2e-latest 2>/dev/null || true
    
    # Prune unused volumes and networks
    docker volume prune -f
    docker network prune -f
    
    print_success "E2E Infrastructure cleaned successfully"
}

function validate_e2e_infrastructure() {
    print_header "Validating E2E Infrastructure"
    
    # Test Account Service
    print_info "Testing Account Service health..."
    if curl -f -s "http://localhost:8083/actuator/health" | grep -q '"status":"UP"'; then
        print_success "Account Service is healthy"
    else
        print_error "Account Service is not healthy"
    fi
    
    # Test Transaction Service
    print_info "Testing Transaction Service health..."
    if curl -f -s "http://localhost:8082/actuator/health" | grep -q '"status":"UP"'; then
        print_success "Transaction Service is healthy"
    else
        print_error "Transaction Service is not healthy"
    fi
    
    # Test Database connectivity
    print_info "Testing database connectivity..."
    if docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" exec -T postgres-account-e2e psql -U e2e_user -d account_db_e2e -c "SELECT 1;" &>/dev/null; then
        print_success "Account Database is accessible"
    else
        print_error "Account Database is not accessible"
    fi
    
    if docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" exec -T postgres-transaction-e2e psql -U e2e_user -d transaction_db_e2e -c "SELECT 1;" &>/dev/null; then
        print_success "Transaction Database is accessible"
    else
        print_error "Transaction Database is not accessible"
    fi
    
    # Test Redis connectivity
    print_info "Testing Redis connectivity..."
    if docker-compose -f "$E2E_COMPOSE_FILE" -p "$PROJECT_NAME" exec -T redis-e2e redis-cli -a e2e_redis_password ping 2>/dev/null | grep -q "PONG"; then
        print_success "Redis is accessible"
    else
        print_error "Redis is not accessible"
    fi
    
    print_success "E2E Infrastructure validation completed"
}

function show_help() {
    cat << EOF
E2E Infrastructure Setup Script

Usage: $0 [COMMAND] [OPTIONS]

Commands:
  start       Start the E2E infrastructure
  stop        Stop the E2E infrastructure
  restart     Restart the E2E infrastructure
  status      Show the status of E2E services
  logs        Show logs from E2E services
  clean       Clean up E2E infrastructure (removes containers, volumes, images)
  validate    Validate that E2E infrastructure is working correctly
  help        Show this help message

Options:
  --service   Specify a specific service for logs (use with logs command)

Examples:
  $0 start
  $0 status
  $0 logs --service account-service-e2e
  $0 validate
  $0 clean

Services:
  - postgres-account-e2e (Account Database)
  - postgres-transaction-e2e (Transaction Database)
  - redis-e2e (Redis Cache)
  - account-service-e2e (Account Service)
  - transaction-service-e2e (Transaction Service)

Ports:
  - Account Service: http://localhost:8083
  - Transaction Service: http://localhost:8082
  - Account Database: localhost:5434
  - Transaction Database: localhost:5435
  - Redis: localhost:6380
EOF
}

# Parse command line arguments
COMMAND=""
SERVICE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        start|stop|restart|status|logs|clean|validate|help)
            COMMAND="$1"
            shift
            ;;
        --service)
            SERVICE="$2"
            shift 2
            ;;
        *)
            print_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Execute command
case $COMMAND in
    start)
        start_e2e_infrastructure
        ;;
    stop)
        stop_e2e_infrastructure
        ;;
    restart)
        restart_e2e_infrastructure
        ;;
    status)
        get_e2e_status
        ;;
    logs)
        show_e2e_logs
        ;;
    clean)
        clean_e2e_infrastructure
        ;;
    validate)
        validate_e2e_infrastructure
        ;;
    help|"")
        show_help
        ;;
    *)
        print_error "Unknown command: $COMMAND"
        show_help
        exit 1
        ;;
esac