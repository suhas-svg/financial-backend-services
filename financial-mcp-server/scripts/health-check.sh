#!/bin/bash

# Health Check Script for MCP Financial Server
set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Default values
HOST=${HOST:-localhost}
PORT=${PORT:-8082}
METRICS_PORT=${METRICS_PORT:-9090}
TIMEOUT=${TIMEOUT:-10}
RETRIES=${RETRIES:-3}
ENVIRONMENT=${ENVIRONMENT:-development}

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

# Health check function
check_endpoint() {
    local url=$1
    local description=$2
    local expected_status=${3:-200}
    
    log_info "Checking $description..."
    
    local attempt=1
    while [ $attempt -le $RETRIES ]; do
        local response=$(curl -s -w "%{http_code}" -o /tmp/health_response --max-time $TIMEOUT "$url" 2>/dev/null || echo "000")
        
        if [ "$response" = "$expected_status" ]; then
            log_success "$description is healthy (HTTP $response)"
            return 0
        fi
        
        if [ $attempt -eq $RETRIES ]; then
            log_error "$description failed (HTTP $response)"
            if [ -f /tmp/health_response ]; then
                log_error "Response: $(cat /tmp/health_response)"
            fi
            return 1
        fi
        
        log_warning "$description check failed (attempt $attempt/$RETRIES), retrying..."
        sleep 2
        ((attempt++))
    done
}

# Detailed health check
detailed_health_check() {
    local base_url="http://$HOST:$PORT"
    local metrics_url="http://$HOST:$METRICS_PORT"
    
    log_info "Running detailed health check for MCP Financial Server"
    echo "Environment: $ENVIRONMENT"
    echo "Base URL: $base_url"
    echo "Metrics URL: $metrics_url"
    echo "=============================================="
    
    local failed_checks=0
    
    # Basic health endpoint
    if ! check_endpoint "$base_url/health" "Health endpoint"; then
        ((failed_checks++))
    fi
    
    # Readiness check
    if ! check_endpoint "$base_url/ready" "Readiness endpoint"; then
        ((failed_checks++))
    fi
    
    # Metrics endpoint
    if ! check_endpoint "$metrics_url/metrics" "Metrics endpoint"; then
        ((failed_checks++))
    fi
    
    # MCP protocol endpoint (if available)
    if ! check_endpoint "$base_url/mcp" "MCP protocol endpoint"; then
        log_warning "MCP protocol endpoint not available (this may be normal)"
    fi
    
    return $failed_checks
}

# Service dependency checks
check_dependencies() {
    log_info "Checking service dependencies..."
    
    local failed_deps=0
    
    # Check Account Service
    local account_service_url=${ACCOUNT_SERVICE_URL:-"http://localhost:8080"}
    if ! check_endpoint "$account_service_url/actuator/health" "Account Service"; then
        ((failed_deps++))
    fi
    
    # Check Transaction Service
    local transaction_service_url=${TRANSACTION_SERVICE_URL:-"http://localhost:8081"}
    if ! check_endpoint "$transaction_service_url/actuator/health" "Transaction Service"; then
        ((failed_deps++))
    fi
    
    # Check Database (if accessible)
    if command -v pg_isready &> /dev/null; then
        local db_host=${DB_HOST:-localhost}
        local db_port=${DB_PORT:-5432}
        if pg_isready -h "$db_host" -p "$db_port" &> /dev/null; then
            log_success "PostgreSQL database is accessible"
        else
            log_error "PostgreSQL database is not accessible"
            ((failed_deps++))
        fi
    fi
    
    # Check Redis (if configured)
    if [ -n "$REDIS_URL" ] && command -v redis-cli &> /dev/null; then
        if redis-cli -u "$REDIS_URL" ping &> /dev/null; then
            log_success "Redis is accessible"
        else
            log_error "Redis is not accessible"
            ((failed_deps++))
        fi
    fi
    
    return $failed_deps
}

# Performance check
performance_check() {
    log_info "Running performance checks..."
    
    local base_url="http://$HOST:$PORT"
    local start_time=$(date +%s%N)
    
    # Measure response time
    if curl -s -w "%{time_total}" -o /dev/null --max-time $TIMEOUT "$base_url/health" > /tmp/response_time; then
        local response_time=$(cat /tmp/response_time)
        local response_time_ms=$(echo "$response_time * 1000" | bc -l | cut -d. -f1)
        
        if [ "$response_time_ms" -lt 1000 ]; then
            log_success "Response time: ${response_time_ms}ms (good)"
        elif [ "$response_time_ms" -lt 3000 ]; then
            log_warning "Response time: ${response_time_ms}ms (acceptable)"
        else
            log_error "Response time: ${response_time_ms}ms (slow)"
            return 1
        fi
    else
        log_error "Failed to measure response time"
        return 1
    fi
    
    return 0
}

# Memory and resource check
resource_check() {
    log_info "Checking resource usage..."
    
    # Check if running in Docker
    if [ -f /.dockerenv ]; then
        log_info "Running inside Docker container"
        
        # Check memory usage
        if [ -f /sys/fs/cgroup/memory/memory.usage_in_bytes ]; then
            local memory_usage=$(cat /sys/fs/cgroup/memory/memory.usage_in_bytes)
            local memory_limit=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
            local memory_usage_mb=$((memory_usage / 1024 / 1024))
            local memory_limit_mb=$((memory_limit / 1024 / 1024))
            
            log_info "Memory usage: ${memory_usage_mb}MB / ${memory_limit_mb}MB"
            
            local memory_percent=$((memory_usage * 100 / memory_limit))
            if [ "$memory_percent" -lt 80 ]; then
                log_success "Memory usage is normal (${memory_percent}%)"
            else
                log_warning "High memory usage (${memory_percent}%)"
            fi
        fi
    else
        log_info "Running on host system"
        
        # Check system resources
        if command -v free &> /dev/null; then
            local memory_info=$(free -m | grep "Mem:")
            log_info "System memory: $memory_info"
        fi
        
        if command -v df &> /dev/null; then
            local disk_info=$(df -h / | tail -1)
            log_info "Disk usage: $disk_info"
        fi
    fi
}

# Generate health report
generate_report() {
    local exit_code=$1
    local timestamp=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
    
    local status="healthy"
    if [ $exit_code -ne 0 ]; then
        status="unhealthy"
    fi
    
    cat > /tmp/health_report.json << EOF
{
    "timestamp": "$timestamp",
    "environment": "$ENVIRONMENT",
    "status": "$status",
    "host": "$HOST",
    "port": $PORT,
    "exit_code": $exit_code,
    "checks": {
        "health_endpoint": $([ $exit_code -eq 0 ] && echo "true" || echo "false"),
        "dependencies": $([ $exit_code -eq 0 ] && echo "true" || echo "false"),
        "performance": $([ $exit_code -eq 0 ] && echo "true" || echo "false")
    }
}
EOF
    
    if [ -n "$HEALTH_REPORT_PATH" ]; then
        cp /tmp/health_report.json "$HEALTH_REPORT_PATH"
        log_info "Health report saved to: $HEALTH_REPORT_PATH"
    fi
}

# Main execution
main() {
    local mode=${1:-full}
    
    case "$mode" in
        "basic")
            log_info "Running basic health check..."
            detailed_health_check
            ;;
        "deps")
            log_info "Checking dependencies only..."
            check_dependencies
            ;;
        "perf")
            log_info "Running performance check..."
            performance_check
            ;;
        "full")
            log_info "Running comprehensive health check..."
            echo "=============================================="
            
            local total_failures=0
            
            # Run all checks
            detailed_health_check || ((total_failures += $?))
            echo ""
            
            check_dependencies || ((total_failures += $?))
            echo ""
            
            performance_check || ((total_failures += $?))
            echo ""
            
            resource_check
            echo ""
            
            # Generate report
            generate_report $total_failures
            
            if [ $total_failures -eq 0 ]; then
                log_success "All health checks passed! 🎉"
                exit 0
            else
                log_error "Health check failed with $total_failures issues"
                exit 1
            fi
            ;;
        *)
            echo "Usage: $0 {basic|deps|perf|full}"
            echo ""
            echo "Modes:"
            echo "  basic - Basic health endpoint check"
            echo "  deps  - Check service dependencies"
            echo "  perf  - Performance and response time check"
            echo "  full  - Comprehensive health check (default)"
            echo ""
            echo "Environment variables:"
            echo "  HOST - Server host (default: localhost)"
            echo "  PORT - Server port (default: 8082)"
            echo "  METRICS_PORT - Metrics port (default: 9090)"
            echo "  TIMEOUT - Request timeout (default: 10)"
            echo "  RETRIES - Number of retries (default: 3)"
            echo "  ENVIRONMENT - Environment name (default: development)"
            exit 1
            ;;
    esac
}

# Clean up temporary files on exit
cleanup() {
    rm -f /tmp/health_response /tmp/response_time /tmp/health_report.json
}

trap cleanup EXIT

# Run main function
main "$@"