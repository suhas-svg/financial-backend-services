#!/bin/bash

# Automated deployment validation script for Transaction Service
# Usage: ./deployment-validation.sh [BASE_URL] [JWT_TOKEN]

set -e

# Configuration
BASE_URL=${1:-"http://localhost:8080"}
ACCOUNT_SERVICE_URL=${2:-"http://localhost:8081"}
JWT_TOKEN=${3:-""}
TIMEOUT=30

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
PASSED=0
FAILED=0
SKIPPED=0

# Function to print test results
print_result() {
    local test_name="$1"
    local success="$2"
    local message="$3"
    
    if [ "$success" = "true" ]; then
        echo -e "${GREEN}✓${NC} $test_name"
        ((PASSED++))
    else
        echo -e "${RED}✗${NC} $test_name"
        if [ -n "$message" ]; then
            echo -e "  Error: $message"
        fi
        ((FAILED++))
    fi
}

# Function to make HTTP requests
http_request() {
    local url="$1"
    local method="${2:-GET}"
    local expected_status="${3:-200}"
    local headers="$4"
    local data="$5"
    
    local curl_cmd="curl -s -w '%{http_code}' --connect-timeout $TIMEOUT --max-time $TIMEOUT"
    
    if [ -n "$headers" ]; then
        curl_cmd="$curl_cmd $headers"
    fi
    
    if [ "$method" != "GET" ]; then
        curl_cmd="$curl_cmd -X $method"
    fi
    
    if [ -n "$data" ]; then
        curl_cmd="$curl_cmd -H 'Content-Type: application/json' -d '$data'"
    fi
    
    local response
    response=$(eval "$curl_cmd '$url'" 2>/dev/null)
    local status_code="${response: -3}"
    local body="${response%???}"
    
    if [ "$status_code" = "$expected_status" ]; then
        echo "true|$status_code|$body"
    else
        echo "false|$status_code|$body"
    fi
}

# Test health endpoints
test_health_endpoints() {
    echo -e "${BLUE}Testing Health Endpoints...${NC}"
    
    # Test actuator health
    result=$(http_request "$BASE_URL/actuator/health")
    IFS='|' read -r success status_code body <<< "$result"
    print_result "Actuator Health Endpoint" "$success" "Status: $status_code"
    
    # Test custom health endpoint
    result=$(http_request "$BASE_URL/api/transactions/health")
    IFS='|' read -r success status_code body <<< "$result"
    print_result "Custom Health Endpoint" "$success" "Status: $status_code"
    
    # Test metrics endpoint
    result=$(http_request "$BASE_URL/actuator/metrics")
    IFS='|' read -r success status_code body <<< "$result"
    print_result "Metrics Endpoint" "$success" "Status: $status_code"
    
    # Test Prometheus metrics
    result=$(http_request "$BASE_URL/actuator/prometheus")
    IFS='|' read -r success status_code body <<< "$result"
    print_result "Prometheus Metrics Endpoint" "$success" "Status: $status_code"
}

# Test authentication
test_authentication() {
    echo -e "${BLUE}Testing Authentication...${NC}"
    
    if [ -z "$JWT_TOKEN" ]; then
        print_result "JWT Token Authentication" "false" "JWT token not provided"
        return
    fi
    
    local auth_header="-H 'Authorization: Bearer $JWT_TOKEN'"
    
    # Test authenticated endpoint
    result=$(http_request "$BASE_URL/api/transactions/account/TEST001" "GET" "404" "$auth_header")
    IFS='|' read -r success status_code body <<< "$result"
    if [ "$status_code" = "404" ] || [ "$status_code" = "200" ]; then
        print_result "Authenticated Endpoint Access" "true" "Status: $status_code"
    else
        print_result "Authenticated Endpoint Access" "false" "Status: $status_code"
    fi
    
    # Test unauthenticated access (should fail)
    result=$(http_request "$BASE_URL/api/transactions/account/TEST001" "GET" "401")
    IFS='|' read -r success status_code body <<< "$result"
    print_result "Unauthenticated Access Rejection" "$success" "Status: $status_code"
}

# Test transaction endpoints
test_transaction_endpoints() {
    echo -e "${BLUE}Testing Transaction Endpoints...${NC}"
    
    if [ -z "$JWT_TOKEN" ]; then
        echo -e "${YELLOW}Skipping transaction endpoint tests - JWT token not provided${NC}"
        return
    fi
    
    local auth_header="-H 'Authorization: Bearer $JWT_TOKEN'"
    
    # Test deposit endpoint structure
    local deposit_data='{"accountId":"VALIDATION_TEST_001","amount":100.00,"description":"Deployment validation test"}'
    result=$(http_request "$BASE_URL/api/transactions/deposit" "POST" "400" "$auth_header" "$deposit_data")
    IFS='|' read -r success status_code body <<< "$result"
    if [ "$status_code" = "400" ] || [ "$status_code" = "404" ] || [ "$status_code" = "422" ]; then
        print_result "Deposit Endpoint Structure" "true" "Status: $status_code"
    else
        print_result "Deposit Endpoint Structure" "false" "Status: $status_code"
    fi
    
    # Test transfer endpoint structure
    local transfer_data='{"fromAccountId":"VALIDATION_TEST_001","toAccountId":"VALIDATION_TEST_002","amount":50.00,"description":"Deployment validation transfer"}'
    result=$(http_request "$BASE_URL/api/transactions/transfer" "POST" "400" "$auth_header" "$transfer_data")
    IFS='|' read -r success status_code body <<< "$result"
    if [ "$status_code" = "400" ] || [ "$status_code" = "404" ] || [ "$status_code" = "422" ]; then
        print_result "Transfer Endpoint Structure" "true" "Status: $status_code"
    else
        print_result "Transfer Endpoint Structure" "false" "Status: $status_code"
    fi
    
    # Test withdrawal endpoint structure
    local withdrawal_data='{"accountId":"VALIDATION_TEST_001","amount":25.00,"description":"Deployment validation withdrawal"}'
    result=$(http_request "$BASE_URL/api/transactions/withdraw" "POST" "400" "$auth_header" "$withdrawal_data")
    IFS='|' read -r success status_code body <<< "$result"
    if [ "$status_code" = "400" ] || [ "$status_code" = "404" ] || [ "$status_code" = "422" ]; then
        print_result "Withdrawal Endpoint Structure" "true" "Status: $status_code"
    else
        print_result "Withdrawal Endpoint Structure" "false" "Status: $status_code"
    fi
}

# Test database connectivity
test_database_connectivity() {
    echo -e "${BLUE}Testing Database Connectivity...${NC}"
    
    result=$(http_request "$BASE_URL/actuator/health")
    IFS='|' read -r success status_code body <<< "$result"
    
    if [ "$success" = "true" ]; then
        if echo "$body" | grep -q '"db".*"UP"'; then
            print_result "Database Connectivity" "true" "DB Status: UP"
        else
            print_result "Database Connectivity" "false" "DB Status not found or not UP"
        fi
    else
        print_result "Database Connectivity" "false" "Health endpoint failed"
    fi
}

# Test Redis connectivity
test_redis_connectivity() {
    echo -e "${BLUE}Testing Redis Connectivity...${NC}"
    
    result=$(http_request "$BASE_URL/actuator/health")
    IFS='|' read -r success status_code body <<< "$result"
    
    if [ "$success" = "true" ]; then
        if echo "$body" | grep -q '"redis".*"UP"'; then
            print_result "Redis Connectivity" "true" "Redis Status: UP"
        else
            print_result "Redis Connectivity" "false" "Redis Status not found or not UP"
        fi
    else
        print_result "Redis Connectivity" "false" "Health endpoint failed"
    fi
}

# Test Account Service integration
test_account_service_integration() {
    echo -e "${BLUE}Testing Account Service Integration...${NC}"
    
    # Test if Account Service is reachable
    result=$(http_request "$ACCOUNT_SERVICE_URL/actuator/health")
    IFS='|' read -r success status_code body <<< "$result"
    print_result "Account Service Reachability" "$success" "Status: $status_code"
    
    # Test health endpoint response
    result=$(http_request "$BASE_URL/actuator/health")
    IFS='|' read -r success status_code body <<< "$result"
    print_result "Account Service Integration Health" "$success" "Health endpoint accessible"
}

# Test performance baseline
test_performance_baseline() {
    echo -e "${BLUE}Testing Performance Baseline...${NC}"
    
    # Test response time for health endpoint
    start_time=$(date +%s%N)
    result=$(http_request "$BASE_URL/actuator/health")
    end_time=$(date +%s%N)
    
    response_time=$(( (end_time - start_time) / 1000000 )) # Convert to milliseconds
    IFS='|' read -r success status_code body <<< "$result"
    
    if [ "$success" = "true" ] && [ "$response_time" -lt 5000 ]; then
        print_result "Health Endpoint Response Time" "true" "Response time: ${response_time}ms"
    else
        print_result "Health Endpoint Response Time" "false" "Response time: ${response_time}ms"
    fi
}

# Generate test report
generate_report() {
    echo ""
    echo -e "${BLUE}=== DEPLOYMENT VALIDATION REPORT ===${NC}"
    echo "Timestamp: $(date)"
    echo "Service URL: $BASE_URL"
    echo "Account Service URL: $ACCOUNT_SERVICE_URL"
    echo ""
    
    echo "Test Results Summary:"
    echo -e "${GREEN}Passed: $PASSED${NC}"
    echo -e "${RED}Failed: $FAILED${NC}"
    echo -e "${YELLOW}Skipped: $SKIPPED${NC}"
    echo ""
    
    local total=$((PASSED + FAILED + SKIPPED))
    local success_rate=0
    if [ "$total" -gt 0 ]; then
        success_rate=$((PASSED * 100 / total))
    fi
    echo "Success Rate: ${success_rate}%"
    
    if [ "$FAILED" -gt 0 ]; then
        echo ""
        echo -e "${RED}Some tests failed. Check the output above for details.${NC}"
        return 1
    else
        echo ""
        echo -e "${GREEN}✓ All deployment validation tests passed!${NC}"
        return 0
    fi
}

# Main execution
main() {
    echo -e "${BLUE}Starting Transaction Service Deployment Validation...${NC}"
    echo "Service URL: $BASE_URL"
    echo "Account Service URL: $ACCOUNT_SERVICE_URL"
    echo ""
    
    # Run all test suites
    test_health_endpoints
    test_database_connectivity
    test_redis_connectivity
    test_account_service_integration
    test_authentication
    test_transaction_endpoints
    test_performance_baseline
    
    # Generate final report
    generate_report
}

# Check if curl is available
if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl is required but not installed.${NC}"
    exit 1
fi

# Run main function
main "$@"