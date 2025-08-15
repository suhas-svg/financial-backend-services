#!/bin/bash
# Script to handle blue-green deployment switching

set -e

NAMESPACE="finance-services-production"
APP_NAME="account-service"
CURRENT_VERSION=""
TARGET_VERSION=""
ROLLBACK=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    --target)
      TARGET_VERSION="$2"
      shift
      shift
      ;;
    --rollback)
      ROLLBACK=true
      shift
      ;;
    *)
      echo "Unknown option: $key"
      exit 1
      ;;
  esac
done

# Determine current active version (blue or green)
get_current_version() {
  # Check which version the main service is pointing to
  BLUE_PODS=$(kubectl get pods -n $NAMESPACE -l app=$APP_NAME,version=blue -o name | wc -l)
  GREEN_PODS=$(kubectl get pods -n $NAMESPACE -l app=$APP_NAME,version=green -o name | wc -l)
  
  if [ $BLUE_PODS -gt 0 ] && [ $GREEN_PODS -eq 0 ]; then
    echo "blue"
  elif [ $GREEN_PODS -gt 0 ] && [ $BLUE_PODS -eq 0 ]; then
    echo "green"
  elif [ $BLUE_PODS -gt 0 ] && [ $GREEN_PODS -gt 0 ]; then
    # Check which one the service is pointing to
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
  else
    echo "none"
  fi
}

# Switch traffic to target version
switch_traffic() {
  local target=$1
  echo "Switching traffic to $target version..."
  
  # Update the main service to point to the target version
  kubectl patch service $APP_NAME -n $NAMESPACE -p "{\"spec\":{\"selector\":{\"app\":\"$APP_NAME\",\"version\":\"$target\"}}}"
  
  echo "Traffic switched to $target version"
}

# Check health of target version
check_health() {
  local version=$1
  local retries=10
  local wait_time=6
  
  echo "Checking health of $version version..."
  
  for i in $(seq 1 $retries); do
    echo "Health check attempt $i of $retries..."
    
    # Check if pods are ready
    READY_PODS=$(kubectl get pods -n $NAMESPACE -l app=$APP_NAME,version=$version -o jsonpath='{.items[*].status.containerStatuses[0].ready}' | tr ' ' '\n' | grep -c "true")
    TOTAL_PODS=$(kubectl get pods -n $NAMESPACE -l app=$APP_NAME,version=$version -o name | wc -l)
    
    if [ $READY_PODS -eq $TOTAL_PODS ] && [ $TOTAL_PODS -gt 0 ]; then
      # Check health endpoint
      POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=$APP_NAME,version=$version -o name | head -n 1)
      if [ -n "$POD_NAME" ]; then
        # Use kubectl port-forward to check health endpoint
        kubectl port-forward $POD_NAME -n $NAMESPACE 9001:9001 &
        PF_PID=$!
        sleep 2
        
        HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9001/actuator/health || echo "000")
        kill $PF_PID
        
        if [ "$HTTP_STATUS" == "200" ]; then
          echo "Health check passed!"
          return 0
        fi
      fi
    fi
    
    echo "Health check failed, retrying in $wait_time seconds..."
    sleep $wait_time
  done
  
  echo "Health check failed after $retries attempts"
  return 1
}

# Scale deployment
scale_deployment() {
  local version=$1
  local replicas=$2
  
  echo "Scaling $version deployment to $replicas replicas..."
  kubectl scale deployment $APP_NAME-$version -n $NAMESPACE --replicas=$replicas
  
  # Wait for scaling to complete
  kubectl rollout status deployment/$APP_NAME-$version -n $NAMESPACE --timeout=300s
}

# Main execution
echo "Starting blue-green deployment process..."

# Get current active version
CURRENT_VERSION=$(get_current_version)
echo "Current active version: $CURRENT_VERSION"

if [ "$ROLLBACK" = true ]; then
  echo "Rollback requested"
  
  if [ "$CURRENT_VERSION" == "blue" ]; then
    TARGET_VERSION="green"
  elif [ "$CURRENT_VERSION" == "green" ]; then
    TARGET_VERSION="blue"
  else
    echo "Cannot determine version to rollback to"
    exit 1
  fi
  
  # Check if target version exists and has pods
  PODS=$(kubectl get pods -n $NAMESPACE -l app=$APP_NAME,version=$TARGET_VERSION -o name | wc -l)
  if [ $PODS -eq 0 ]; then
    echo "No pods found for $TARGET_VERSION version, cannot rollback"
    exit 1
  fi
  
  # Switch traffic back to previous version
  switch_traffic $TARGET_VERSION
  
  echo "Rollback completed successfully"
  exit 0
fi

# Normal blue-green deployment
if [ -z "$TARGET_VERSION" ]; then
  # If no target specified, toggle between blue and green
  if [ "$CURRENT_VERSION" == "blue" ]; then
    TARGET_VERSION="green"
  else
    TARGET_VERSION="blue"
  fi
fi

echo "Target version: $TARGET_VERSION"

# Update the target deployment with new image
if [ -n "$IMAGE_TAG" ]; then
  echo "Updating $TARGET_VERSION deployment with image: $IMAGE_TAG"
  kubectl set image deployment/$APP_NAME-$TARGET_VERSION -n $NAMESPACE $APP_NAME=$IMAGE_TAG
fi

# Scale up the target deployment
scale_deployment $TARGET_VERSION 3

# Check health of target version
if ! check_health $TARGET_VERSION; then
  echo "Health check failed for $TARGET_VERSION version, aborting deployment"
  scale_deployment $TARGET_VERSION 0
  exit 1
fi

# Switch traffic to target version
switch_traffic $TARGET_VERSION

# Scale down the old version (optional, can keep both running for quick rollback)
if [ "$CURRENT_VERSION" != "none" ] && [ "$CURRENT_VERSION" != "$TARGET_VERSION" ]; then
  echo "Scaling down $CURRENT_VERSION version..."
  # Keep 1 replica of the old version for quick rollback if needed
  scale_deployment $CURRENT_VERSION 1
fi

echo "Blue-green deployment completed successfully"