#!/bin/bash

# Performance Testing Script for Account Service
# This script runs all performance tests and generates reports

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL=${BASE_URL:-"http://localhost:8080"}
RESULTS_DIR="results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo -e "${BLUE}=== Account Service Performance Testing ===${NC}"
echo "Base URL: $BASE_URL"
echo "Timestamp: $TIMESTAMP"
echo ""

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}Error: k6 is not installed. Please install k6 first.${NC}"
    echo "Visit: https://k6.io/docs/getting-started/installation/"
    exit 1
fi

# Check if the service is running
echo -e "${YELLOW}Checking if service is running...${NC}"
if ! curl -s "$BASE_URL/actuator/health" > /dev/null; then
    echo -e "${RED}Error: Service is not running at $BASE_URL${NC}"
    echo "Please start the account service first."
    exit 1
fi

echo -e "${GREEN}Service is running!${NC}"
echo ""

# Create results directory
mkdir -p "$RESULTS_DIR"

# Function to run a test
run_test() {
    local test_name=$1
    local test_file=$2
    local description=$3
    
    echo -e "${BLUE}Running $test_name...${NC}"
    echo "Description: $description"
    echo ""
    
    local result_file="$RESULTS_DIR/${test_name}_${TIMESTAMP}.json"
    local summary_file="$RESULTS_DIR/${test_name}_${TIMESTAMP}_summary.txt"
    
    # Run k6 test with JSON output
    if k6 run --out json="$result_file" --env BASE_URL="$BASE_URL" "$test_file" | tee "$summary_file"; then
        echo -e "${GREEN}✓ $test_name completed successfully${NC}"
        
        # Extract key metrics from the summary
        echo -e "${YELLOW}Key Metrics:${NC}"
        grep -E "(http_req_duration|http_req_failed|errors)" "$summary_file" | tail -10
        echo ""
    else
        echo -e "${RED}✗ $test_name failed${NC}"
        echo ""
    fi
}

# Run Load Test
run_test "load-test" "load-test.js" "Standard load test with gradual ramp-up to 100 concurrent users"

# Wait between tests
echo -e "${YELLOW}Waiting 30 seconds before next test...${NC}"
sleep 30

# Run Stress Test
run_test "stress-test" "stress-test.js" "Stress test to find system breaking point (up to 500 users)"

# Wait between tests
echo -e "${YELLOW}Waiting 60 seconds before next test...${NC}"
sleep 60

# Run Spike Test
run_test "spike-test" "spike-test.js" "Spike test with sudden load increases to test system resilience"

# Generate summary report
echo -e "${BLUE}=== Generating Summary Report ===${NC}"
REPORT_FILE="$RESULTS_DIR/performance_report_${TIMESTAMP}.md"

cat > "$REPORT_FILE" << EOF
# Performance Test Report

**Date:** $(date)
**Service:** Account Service
**Base URL:** $BASE_URL

## Test Summary

### Load Test
- **Purpose:** Validate system performance under expected load
- **Max Users:** 100 concurrent users
- **Duration:** ~6 minutes
- **Results:** See load-test_${TIMESTAMP}_summary.txt

### Stress Test
- **Purpose:** Find system breaking point
- **Max Users:** 500 concurrent users
- **Duration:** ~15 minutes
- **Results:** See stress-test_${TIMESTAMP}_summary.txt

### Spike Test
- **Purpose:** Test system resilience to sudden load spikes
- **Max Users:** 300 concurrent users (sudden spikes)
- **Duration:** ~3 minutes
- **Results:** See spike-test_${TIMESTAMP}_summary.txt

## Key Performance Indicators

### Response Time Thresholds
- Load Test: 95th percentile < 500ms
- Stress Test: 95th percentile < 1000ms
- Spike Test: 95th percentile < 2000ms

### Error Rate Thresholds
- Load Test: < 1%
- Stress Test: < 5%
- Spike Test: < 10%

## Recommendations

1. **Monitor Response Times:** Ensure 95th percentile response times stay within thresholds
2. **Error Rate Monitoring:** Set up alerts for error rates exceeding thresholds
3. **Resource Utilization:** Monitor CPU, memory, and database connections during peak load
4. **Database Performance:** Consider connection pooling optimization if database is the bottleneck
5. **Caching Strategy:** Implement caching for frequently accessed data to improve response times

## Files Generated
- Raw results: *_${TIMESTAMP}.json
- Test summaries: *_${TIMESTAMP}_summary.txt
- This report: performance_report_${TIMESTAMP}.md

EOF

echo -e "${GREEN}Performance testing completed!${NC}"
echo -e "${BLUE}Results saved in: $RESULTS_DIR${NC}"
echo -e "${BLUE}Summary report: $REPORT_FILE${NC}"
echo ""

# Display final summary
echo -e "${YELLOW}=== Final Summary ===${NC}"
echo "All performance tests have been executed."
echo "Check the results directory for detailed reports and metrics."
echo "Use the JSON files for detailed analysis and the summary files for quick overview."
echo ""
echo -e "${GREEN}Happy performance testing! 🚀${NC}"