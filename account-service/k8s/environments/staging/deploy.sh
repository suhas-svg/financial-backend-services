#!/bin/bash
set -e

# Staging environment deployment script
NAMESPACE="finance-services-staging"
ENVIRONMENT="staging"

echo "Deploying to staging environment..."

# Create namespace if it doesn't exist
kubectl apply -f namespace.yaml

# Generate secure passwords if they don't exist
if ! kubectl get secret account-service-secrets -n $NAMESPACE &> /dev/null; then
  echo "Creating new secrets for staging environment..."
  
  # Generate random passwords
  POSTGRES_PASSWORD=$(openssl rand -base64 20)
  REDIS_PASSWORD=$(openssl rand -base64 20)
  JWT_SECRET=$(openssl rand -base64 32)
  
  # Create secret
  kubectl create secret generic account-service-secrets \
    --namespace=$NAMESPACE \
    --from-literal=POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
    --from-literal=REDIS_PASSWORD=$REDIS_PASSWORD \
    --from-literal=JWT_SECRET=$JWT_SECRET
else
  echo "Using existing secrets for staging environment..."
fi

# Apply ConfigMaps
echo "Applying ConfigMaps..."
kubectl apply -f configmap.yaml

# Deploy PostgreSQL
echo "Deploying PostgreSQL..."
kubectl apply -f postgres-deployment.yaml

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres -n $NAMESPACE --timeout=300s

# Deploy application
echo "Deploying Account Service application..."
kubectl apply -f deployment.yaml

# Wait for application to be ready
echo "Waiting for Account Service to be ready..."
kubectl wait --for=condition=ready pod -l app=account-service -n $NAMESPACE --timeout=300s

# Get service URL
SERVICE_URL=$(kubectl get ingress account-service-ingress -n $NAMESPACE -o jsonpath='{.spec.rules[0].host}' 2>/dev/null || echo "localhost:8080")
METRICS_URL=$(kubectl get ingress account-service-ingress -n $NAMESPACE -o jsonpath='{.spec.rules[1].host}' 2>/dev/null || echo "localhost:9001")

echo "Deployment to staging environment completed successfully!"
echo "Service URL: https://$SERVICE_URL"
echo "Metrics URL: https://$METRICS_URL"

# Run post-deployment verification
echo "Running post-deployment verification..."

# Check if pods are running
RUNNING_PODS=$(kubectl get pods -n $NAMESPACE -l app=account-service -o jsonpath='{.items[*].status.phase}' | tr ' ' '\n' | grep -c "Running" || echo "0")
TOTAL_PODS=$(kubectl get pods -n $NAMESPACE -l app=account-service -o jsonpath='{.items[*].status.phase}' | tr ' ' '\n' | wc -l || echo "0")

if [ "$RUNNING_PODS" -eq "$TOTAL_PODS" ] && [ "$TOTAL_PODS" -gt 0 ]; then
  echo "✅ All pods are running: $RUNNING_PODS/$TOTAL_PODS"
else
  echo "❌ Not all pods are running: $RUNNING_PODS/$TOTAL_PODS"
  exit 1
fi

# Check if service is accessible (using port-forward for local testing)
kubectl port-forward service/account-service-actuator 9001:9001 -n $NAMESPACE &
PORT_FORWARD_PID=$!
sleep 5

if curl -s http://localhost:9001/actuator/health | grep -q "UP"; then
  echo "✅ Health check passed"
else
  echo "❌ Health check failed"
  kill $PORT_FORWARD_PID
  exit 1
fi

kill $PORT_FORWARD_PID

echo "Staging deployment verification completed successfully!"
exit 0