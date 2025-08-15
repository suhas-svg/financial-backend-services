# Development Environment Deployment

This directory contains Kubernetes manifests and deployment scripts for the Account Service development environment.

## Overview

The development environment provides:
- Single replica deployment for resource efficiency
- Debug logging enabled
- Extended health check timeouts
- Comprehensive smoke tests
- Local development-friendly configuration

## Prerequisites

- Kubernetes cluster (local or remote)
- kubectl configured and connected to cluster
- Docker images built and available
- Sufficient cluster resources (minimum 1 CPU, 1GB RAM)

## Quick Start

### Automated Deployment

```bash
# Make the deployment script executable
chmod +x deploy.sh

# Deploy the entire development environment
./deploy.sh
```

### Manual Deployment

```bash
# Create namespace
kubectl apply -f namespace.yaml

# Apply secrets and RBAC
kubectl apply -f secrets.yaml

# Apply configuration
kubectl apply -f configmap.yaml

# Deploy PostgreSQL database
kubectl apply -f postgres-deployment.yaml

# Wait for database to be ready
kubectl wait --for=condition=available --timeout=300s deployment/postgres-deployment -n finance-services-dev

# Deploy the application
kubectl apply -f deployment.yaml

# Wait for application to be ready
kubectl wait --for=condition=available --timeout=300s deployment/account-service-deployment -n finance-services-dev

# Run smoke tests
kubectl apply -f smoke-tests.yaml
```

### Using Kustomize

```bash
# Deploy using Kustomize
kubectl apply -k .

# Or build and apply
kustomize build . | kubectl apply -f -
```

## Environment Configuration

### Namespace
- **Name**: `finance-services-dev`
- **Labels**: `environment=development`

### Application Configuration
- **Profile**: `dev`
- **Replicas**: 1
- **Resources**: 
  - Requests: 256Mi memory, 100m CPU
  - Limits: 512Mi memory, 500m CPU
- **Logging**: DEBUG level enabled
- **Database**: PostgreSQL with development data

### Database Configuration
- **Image**: `postgres:15-alpine`
- **Database**: `myfirstdb`
- **User**: `postgres`
- **Storage**: 1Gi PVC
- **Resources**:
  - Requests: 128Mi memory, 100m CPU
  - Limits: 256Mi memory, 200m CPU

## Health Checks

The application includes comprehensive health checks:

### Liveness Probe
- **Endpoint**: `/actuator/health/liveness`
- **Port**: 9001
- **Initial Delay**: 90 seconds
- **Period**: 30 seconds
- **Timeout**: 10 seconds
- **Failure Threshold**: 3

### Readiness Probe
- **Endpoint**: `/actuator/health/readiness`
- **Port**: 9001
- **Initial Delay**: 30 seconds
- **Period**: 10 seconds
- **Timeout**: 5 seconds
- **Failure Threshold**: 3

### Startup Probe
- **Endpoint**: `/actuator/health`
- **Port**: 9001
- **Initial Delay**: 30 seconds
- **Period**: 10 seconds
- **Timeout**: 5 seconds
- **Failure Threshold**: 15 (allows 150 seconds for startup)

## Smoke Tests

Automated smoke tests verify:
- Application startup and health
- Database connectivity
- Actuator endpoints functionality
- Metrics collection
- Environment configuration
- Resource usage

### Running Smoke Tests

```bash
# Apply smoke test job
kubectl apply -f smoke-tests.yaml

# Check test results
kubectl logs job/smoke-tests -n finance-services-dev

# Clean up test job
kubectl delete job smoke-tests -n finance-services-dev
```

## Accessing the Application

### Port Forwarding

```bash
# Forward application port
kubectl port-forward service/account-service 8080:8080 -n finance-services-dev

# Forward actuator port
kubectl port-forward service/account-service-actuator 9001:9001 -n finance-services-dev
```

### Service URLs (within cluster)
- **Application**: `http://account-service.finance-services-dev.svc.cluster.local:8080`
- **Actuator**: `http://account-service-actuator.finance-services-dev.svc.cluster.local:9001`
- **Database**: `http://postgres-service.finance-services-dev.svc.cluster.local:5432`

### Health Check URLs
- **Health**: `http://localhost:9001/actuator/health`
- **Liveness**: `http://localhost:9001/actuator/health/liveness`
- **Readiness**: `http://localhost:9001/actuator/health/readiness`
- **Metrics**: `http://localhost:9001/actuator/metrics`
- **Prometheus**: `http://localhost:9001/actuator/prometheus`

## Monitoring and Debugging

### View Application Logs

```bash
# Application logs
kubectl logs -f deployment/account-service-deployment -n finance-services-dev

# Database logs
kubectl logs -f deployment/postgres-deployment -n finance-services-dev
```

### Check Pod Status

```bash
# List all pods
kubectl get pods -n finance-services-dev

# Describe pod for detailed information
kubectl describe pod <pod-name> -n finance-services-dev

# Get pod events
kubectl get events -n finance-services-dev --sort-by='.lastTimestamp'
```

### Resource Usage

```bash
# Check resource usage
kubectl top pods -n finance-services-dev

# Check node resources
kubectl top nodes
```

### Database Access

```bash
# Connect to PostgreSQL
kubectl exec -it deployment/postgres-deployment -n finance-services-dev -- psql -U postgres -d myfirstdb

# Run SQL queries
kubectl exec -it deployment/postgres-deployment -n finance-services-dev -- psql -U postgres -d myfirstdb -c "SELECT version();"
```

## Troubleshooting

### Common Issues

1. **Pod not starting**
   ```bash
   kubectl describe pod <pod-name> -n finance-services-dev
   kubectl logs <pod-name> -n finance-services-dev
   ```

2. **Database connection issues**
   ```bash
   # Check database pod status
   kubectl get pods -l app=postgres -n finance-services-dev
   
   # Test database connectivity
   kubectl exec -it deployment/postgres-deployment -n finance-services-dev -- pg_isready -U postgres
   ```

3. **Health check failures**
   ```bash
   # Check actuator endpoints
   kubectl port-forward service/account-service-actuator 9001:9001 -n finance-services-dev
   curl http://localhost:9001/actuator/health
   ```

4. **Resource constraints**
   ```bash
   # Check resource usage
   kubectl top pods -n finance-services-dev
   kubectl describe nodes
   ```

### Debug Commands

```bash
# Get all resources in namespace
kubectl get all -n finance-services-dev

# Check resource quotas
kubectl describe resourcequota -n finance-services-dev

# Check network policies
kubectl get networkpolicies -n finance-services-dev

# Check secrets
kubectl get secrets -n finance-services-dev
```

## Cleanup

### Remove Development Environment

```bash
# Delete all resources
kubectl delete namespace finance-services-dev

# Or delete individual components
kubectl delete -f deployment.yaml
kubectl delete -f postgres-deployment.yaml
kubectl delete -f configmap.yaml
kubectl delete -f secrets.yaml
kubectl delete -f namespace.yaml
```

### Using Kustomize

```bash
# Delete using Kustomize
kubectl delete -k .
```

## Security Considerations

- Uses non-root containers with security contexts
- Read-only root filesystem where possible
- Drops all capabilities
- Uses service accounts with minimal permissions
- Secrets are base64 encoded (use proper secret management in production)
- Network policies can be applied for additional security

## Development Workflow

1. **Code Changes**: Make changes to the application code
2. **Build Image**: Build new Docker image with changes
3. **Update Deployment**: Update image tag in deployment.yaml
4. **Deploy**: Run `./deploy.sh` or use kubectl apply
5. **Test**: Run smoke tests and manual testing
6. **Debug**: Use logs and health checks for troubleshooting

## CI/CD Integration

This development environment is automatically deployed by the CI/CD pipeline when:
- Code is pushed to `develop` or `main` branch
- All tests pass
- Security scans pass
- Docker image is successfully built

The pipeline will:
1. Deploy to development environment
2. Run smoke tests
3. Perform integration tests
4. Generate deployment report
5. Notify on success/failure

## Next Steps

After successful development deployment:
1. Promote to staging environment (manual approval required)
2. Run comprehensive testing in staging
3. Deploy to production (manual approval required)

## Support

For issues or questions:
- Check the troubleshooting section above
- Review application logs
- Contact the development team
- Create an issue in the project repository