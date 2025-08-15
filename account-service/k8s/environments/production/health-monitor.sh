#!/bin/bash
# Script to monitor health metrics and trigger automatic rollback if needed

set -e

NAMESPACE="finance-services-production"
APP_NAME="account-service"
CHECK_INTERVAL=30  # seconds
FAILURE_THRESHOLD=3
ERROR_RATE_THRESHOLD=5  # percentage
RESPONSE_TIME_THRESHOLD=500  # milliseconds

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    --interval)
      CHECK_INTERVAL="$2"
      shift
      shift
      ;;
    --failures)
      FAILURE_THRESHOLD="$2"
      shift
      shift
      ;;
    --error-rate)
      ERROR_RATE_THRESHOLD="$2"
      shift
      shift
      ;;
    --response-time)
      RESPONSE_TIME_THRESHOLD="$2"
      shift
      shift
      ;;
    *)
      echo "Unknown option: $key"
      exit 1
      ;;
  esac
done

# Get current active version
get_current_version() {
  # Check which version the main service is pointing to
  SELECTOR=$(kubectl get service $APP_NAME -n $NAMESPACE -o jsonpath='{.spec.selector.version}')
  if [ -z "$SELECTOR" ]; then
    # If no version selector, check endpoints
    ENDPOINTS=$(kubectl get endpoints $APP_NAME -n $NAMESPACE -o jsonpath='{.subsets[0].addresses[0].targetRef.name}')
    if [[ $ENDPOINTS == *"blue"* ]]; then
      echo "blue"
    elif [[ $ENDPOINTS == *"green"* ]]; then
      echo "green"
    else
      echo "unknown"
    fi
  else
    echo $SELECTOR
  fi
}

# Check health metrics
check_health_metrics() {
  local version=$1
  local failures=0
  
  echo "$(date): Starting health monitoring for $version version..."
  
  while true; do
    echo "$(date): Checking health metrics..."
    
    # Check if pods are ready
    READY_PODS=$(kubectl get pods -n $NAMESPACE -l app=$APP_NAME,version=$version -o jsonpath='{.items[*].status.containerStatuses[0].ready}' | tr ' ' '\n' | grep -c "true")
    TOTAL_PODS=$(kubectl get pods -n $NAMESPACE -l app=$APP_NAME,version=$version -o name | wc -l)
    
    if [ $READY_PODS -ne $TOTAL_PODS ] || [ $TOTAL_PODS -eq 0 ]; then
      echo "$(date): Warning - Not all pods are ready ($READY_PODS/$TOTAL_PODS)"
      failures=$((failures + 1))
    else
      # Check health endpoint
      POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=$APP_NAME,version=$version -o name | head -n 1)
      if [ -n "$POD_NAME" ]; then
        # Use kubectl port-forward to check health endpoint
        kubectl port-forward $POD_NAME -n $NAMESPACE 9001:9001 &
        PF_PID=$!
        sleep 2
        
        HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9001/actuator/health || echo "000")
        
        # Check error rate from metrics endpoint
        ERROR_RATE_OUTPUT=$(curl -s http://localhost:9001/actuator/metrics/http.server.requests.exception || echo "")
        ERROR_RATE=$(echo $ERROR_RATE_OUTPUT | grep -o '"value":[0-9.]*' | cut -d':' -f2)
        
        # Check response time from metrics endpoint
        RESPONSE_TIME_OUTPUT=$(curl -s http://localhost:9001/actuator/metrics/http.server.requests | grep -o '"mean":[0-9.]*' || echo "")
        RESPONSE_TIME=$(echo $RESPONSE_TIME_OUTPUT | cut -d':' -f2)
        
        kill $PF_PID
        
        if [ "$HTTP_STATUS" != "200" ]; then
          echo "$(date): Warning - Health check failed with status $HTTP_STATUS"
          failures=$((failures + 1))
        fi
        
        if [ -n "$ERROR_RATE" ] && [ $(echo "$ERROR_RATE > $ERROR_RATE_THRESHOLD" | bc -l) -eq 1 ]; then
          echo "$(date): Warning - Error rate too high: $ERROR_RATE% (threshold: $ERROR_RATE_THRESHOLD%)"
          failures=$((failures + 1))
        fi
        
        if [ -n "$RESPONSE_TIME" ] && [ $(echo "$RESPONSE_TIME > $RESPONSE_TIME_THRESHOLD" | bc -l) -eq 1 ]; then
          echo "$(date): Warning - Response time too high: ${RESPONSE_TIME}ms (threshold: ${RESPONSE_TIME_THRESHOLD}ms)"
          failures=$((failures + 1))
        fi
      else
        echo "$(date): Warning - No pods found"
        failures=$((failures + 1))
      fi
    fi
    
    # Check if we've exceeded the failure threshold
    if [ $failures -ge $FAILURE_THRESHOLD ]; then
      echo "$(date): Critical - Failure threshold exceeded ($failures/$FAILURE_THRESHOLD)"
      return 1
    elif [ $failures -gt 0 ]; then
      echo "$(date): Warning - $failures failures detected, but below threshold"
    else
      echo "$(date): Health check passed"
      failures=0  # Reset failures if everything is good
    fi
    
    sleep $CHECK_INTERVAL
  done
}

# Trigger rollback
trigger_rollback() {
  echo "$(date): Triggering automatic rollback due to health check failures"
  
  # Execute the blue-green switch script with rollback flag
  ./blue-green-switch.sh --rollback
  
  # Send notification
  echo "$(date): Rollback triggered - sending notification"
  # In a real implementation, you would send a notification here
}

# Main execution
echo "$(date): Starting health monitoring..."

# Get current active version
CURRENT_VERSION=$(get_current_version)
echo "$(date): Monitoring $CURRENT_VERSION version"

# Start health monitoring
if ! check_health_metrics $CURRENT_VERSION; then
  trigger_rollback
fi