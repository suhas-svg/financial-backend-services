#!/bin/bash

# Docker Management Script for Account Service
# Usage: ./docker-manage.sh [command] [environment] [options]

set -e

# Default values
ENVIRONMENT=${2:-dev}
COMMAND=${1:-help}
COMPOSE_PROJECT_NAME="account-service"

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
    echo "Docker Management Script for Account Service"
    echo ""
    echo "Usage: $0 [command] [environment] [options]"
    echo ""
    echo "Commands:"
    echo "  up          Start services"
    echo "  down        Stop services"
    echo "  restart     Restart services"
    echo "  build       Build images"
    echo "  logs        Show logs"
    echo "  status      Show service status"
    echo "  clean       Clean up containers, images, and volumes"
    echo "  shell       Open shell in app container"
    echo "  db-shell    Open database shell"
    echo "  test        Run tests in container"
    echo "  help        Show this help message"
    echo ""
    echo "Environments:"
    echo "  dev         Development environment (default)"
    echo "  staging     Staging environment"
    echo "  prod        Production environment"
    echo ""
    echo "Examples:"
    echo "  $0 up dev"
    echo "  $0 build staging"
    echo "  $0 logs dev app"
    echo "  $0 clean dev"
}

# Function to get Docker Compose files
get_compose_files() {
    local env=$1
    local files="-f docker-compose.yml"
    
    case $env in
        dev|development)
            files="$files -f docker-compose.dev.yml"
            ;;
        staging|stage)
            files="$files -f docker-compose.staging.yml"
            ;;
        prod|production)
            # Production uses base compose file only
            ;;
        *)
            print_error "Invalid environment: $env"
            exit 1
            ;;
    esac
    
    echo $files
}

# Function to set environment variables
set_environment() {
    local env=$1
    
    case $env in
        dev|development)
            export $(cat .env.dev | grep -v '^#' | xargs)
            ;;
        staging|stage)
            export $(cat .env.staging | grep -v '^#' | xargs)
            ;;
        prod|production)
            if [ -f .env.prod ]; then
                export $(cat .env.prod | grep -v '^#' | xargs)
            else
                print_warning "No .env.prod file found. Using default values."
            fi
            ;;
    esac
    
    export ENVIRONMENT=$env
    export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME}-${env}"
}

# Function to start services
start_services() {
    local env=$1
    shift
    local services="$@"
    
    print_status "Starting services for $env environment..."
    set_environment $env
    
    local compose_files=$(get_compose_files $env)
    
    if [ -n "$services" ]; then
        docker-compose $compose_files up -d $services
    else
        docker-compose $compose_files up -d
    fi
    
    print_success "Services started successfully!"
    
    # Show service status
    docker-compose $compose_files ps
}

# Function to stop services
stop_services() {
    local env=$1
    
    print_status "Stopping services for $env environment..."
    set_environment $env
    
    local compose_files=$(get_compose_files $env)
    docker-compose $compose_files down
    
    print_success "Services stopped successfully!"
}

# Function to restart services
restart_services() {
    local env=$1
    shift
    local services="$@"
    
    print_status "Restarting services for $env environment..."
    set_environment $env
    
    local compose_files=$(get_compose_files $env)
    
    if [ -n "$services" ]; then
        docker-compose $compose_files restart $services
    else
        docker-compose $compose_files restart
    fi
    
    print_success "Services restarted successfully!"
}

# Function to build images
build_images() {
    local env=$1
    
    print_status "Building images for $env environment..."
    set_environment $env
    
    local compose_files=$(get_compose_files $env)
    docker-compose $compose_files build --no-cache
    
    print_success "Images built successfully!"
}

# Function to show logs
show_logs() {
    local env=$1
    shift
    local service=${1:-}
    local follow=${2:-}
    
    set_environment $env
    local compose_files=$(get_compose_files $env)
    
    if [ "$follow" = "-f" ] || [ "$follow" = "--follow" ]; then
        if [ -n "$service" ]; then
            docker-compose $compose_files logs -f $service
        else
            docker-compose $compose_files logs -f
        fi
    else
        if [ -n "$service" ]; then
            docker-compose $compose_files logs --tail=100 $service
        else
            docker-compose $compose_files logs --tail=100
        fi
    fi
}

# Function to show service status
show_status() {
    local env=$1
    
    print_status "Service status for $env environment:"
    set_environment $env
    
    local compose_files=$(get_compose_files $env)
    docker-compose $compose_files ps
    
    echo ""
    print_status "Resource usage:"
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}" $(docker-compose $compose_files ps -q) 2>/dev/null || true
}

# Function to clean up
cleanup() {
    local env=$1
    
    print_warning "This will remove all containers, images, and volumes for $env environment."
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        set_environment $env
        local compose_files=$(get_compose_files $env)
        
        print_status "Stopping and removing containers..."
        docker-compose $compose_files down -v --remove-orphans
        
        print_status "Removing images..."
        docker-compose $compose_files down --rmi all
        
        print_status "Pruning unused resources..."
        docker system prune -f
        
        print_success "Cleanup completed!"
    else
        print_status "Cleanup cancelled."
    fi
}

# Function to open shell in app container
open_shell() {
    local env=$1
    
    set_environment $env
    local container_name="account-service-app-${env}"
    
    if docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
        print_status "Opening shell in $container_name..."
        docker exec -it $container_name /bin/sh
    else
        print_error "Container $container_name is not running."
        exit 1
    fi
}

# Function to open database shell
open_db_shell() {
    local env=$1
    
    set_environment $env
    local container_name="account-service-postgres-${env}"
    
    if docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
        print_status "Opening database shell in $container_name..."
        docker exec -it $container_name psql -U ${POSTGRES_USER:-postgres} -d ${POSTGRES_DB:-myfirstdb}
    else
        print_error "Database container $container_name is not running."
        exit 1
    fi
}

# Function to run tests
run_tests() {
    local env=$1
    
    print_status "Running tests in $env environment..."
    set_environment $env
    
    local compose_files=$(get_compose_files $env)
    docker-compose $compose_files exec app ./mvnw test
}

# Change to script directory
cd "$(dirname "$0")/.."

# Main command processing
case $COMMAND in
    up|start)
        start_services $ENVIRONMENT "${@:3}"
        ;;
    down|stop)
        stop_services $ENVIRONMENT
        ;;
    restart)
        restart_services $ENVIRONMENT "${@:3}"
        ;;
    build)
        build_images $ENVIRONMENT
        ;;
    logs)
        show_logs $ENVIRONMENT "${@:3}"
        ;;
    status|ps)
        show_status $ENVIRONMENT
        ;;
    clean|cleanup)
        cleanup $ENVIRONMENT
        ;;
    shell|bash)
        open_shell $ENVIRONMENT
        ;;
    db-shell|db)
        open_db_shell $ENVIRONMENT
        ;;
    test)
        run_tests $ENVIRONMENT
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        print_error "Unknown command: $COMMAND"
        show_usage
        exit 1
        ;;
esac