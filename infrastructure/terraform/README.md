# Terraform Infrastructure for Account Service

This directory contains Terraform configurations for deploying the Account Service infrastructure across different environments (development, staging, and production).

## Directory Structure

```
infrastructure/terraform/
├── modules/                    # Reusable Terraform modules
│   ├── kubernetes/            # Kubernetes namespace and RBAC setup
│   ├── database/              # PostgreSQL database deployment
│   └── monitoring/            # Prometheus and Grafana monitoring
├── environments/              # Environment-specific configurations
│   ├── dev/                   # Development environment
│   ├── staging/               # Staging environment
│   └── prod/                  # Production environment
├── scripts/                   # Utility scripts
│   ├── deploy.sh             # Deployment script
│   └── validate.sh           # Validation script
├── backend.tf                # Backend configuration
├── validation.tf             # Validation rules
└── README.md                 # This file
```

## Prerequisites

Before using this Terraform configuration, ensure you have the following tools installed:

- [Terraform](https://www.terraform.io/downloads.html) >= 1.0
- [kubectl](https://kubernetes.io/docs/tasks/tools/) - Kubernetes CLI
- [Helm](https://helm.sh/docs/intro/install/) - Kubernetes package manager
- Access to a Kubernetes cluster (Docker Desktop, Kind, Minikube, or cloud provider)

## Quick Start

### 1. Set up Kubernetes Cluster

For local development, you can use Docker Desktop with Kubernetes enabled:

```bash
# Enable Kubernetes in Docker Desktop settings
# Or use Kind for a lightweight cluster
kind create cluster --name account-service-dev
```

### 2. Deploy to Development Environment

```bash
# Navigate to the development environment
cd infrastructure/terraform/environments/dev

# Initialize Terraform
terraform init

# Plan the deployment
terraform plan

# Apply the configuration
terraform apply
```

### 3. Using the Deployment Script

Alternatively, use the provided deployment script:

```bash
# Plan deployment to dev environment
./infrastructure/terraform/scripts/deploy.sh -e dev

# Apply changes to dev environment
./infrastructure/terraform/scripts/deploy.sh -e dev -a apply

# Apply with auto-approve
./infrastructure/terraform/scripts/deploy.sh -e dev -a apply -y
```

## Environment Configurations

### Development Environment

- **Namespace**: `account-service-dev`
- **Resources**: Minimal resource allocation for local development
- **Features**: Basic monitoring, no alerting, relaxed security policies
- **Storage**: Local storage classes (hostpath)

### Staging Environment

- **Namespace**: `account-service-staging`
- **Resources**: Production-like resource allocation
- **Features**: Full monitoring with alerting, network policies enabled
- **Storage**: Standard storage classes

### Production Environment

- **Namespace**: `account-service-prod`
- **Resources**: High availability with resource quotas
- **Features**: Full monitoring, alerting, security policies, backup strategies
- **Storage**: High-performance storage classes

## Modules

### Kubernetes Module

Creates the basic Kubernetes infrastructure:
- Namespace with proper labeling
- Service account with RBAC permissions
- Network policies for security
- Resource quotas for resource management

### Database Module

Deploys PostgreSQL database:
- PostgreSQL deployment with persistent storage
- Database credentials stored in Kubernetes secrets
- Configuration via ConfigMaps
- Health checks and resource limits

### Monitoring Module

Sets up monitoring infrastructure:
- Prometheus for metrics collection
- Grafana for visualization with pre-configured dashboards
- Alertmanager for alerting (staging/prod)
- ServiceMonitor for application metrics
- PrometheusRule for custom alerts

## State Management

### Local State (Development)

Development environment uses local state files for simplicity:
```hcl
terraform {
  backend "local" {
    path = "terraform.tfstate"
  }
}
```

### Remote State (Staging/Production)

For staging and production, configure remote state backends:

#### AWS S3 Backend
```hcl
terraform {
  backend "s3" {
    bucket         = "account-service-terraform-state"
    key            = "environments/staging/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}
```

#### Azure Storage Backend
```hcl
terraform {
  backend "azurerm" {
    resource_group_name  = "terraform-state-rg"
    storage_account_name = "accountservicetfstate"
    container_name       = "tfstate"
    key                  = "environments/staging/terraform.tfstate"
  }
}
```

## Validation and Testing

### Manual Validation

```bash
# Validate all configurations
./infrastructure/terraform/scripts/validate.sh

# Validate specific environment
cd infrastructure/terraform/environments/dev
terraform validate
terraform plan
```

### Automated Testing

The validation script performs:
- Terraform syntax validation
- Module validation
- Security checks (hardcoded secrets)
- Formatting checks
- Plan validation

## Security Considerations

### Secrets Management

- Database passwords are generated randomly or provided via variables
- Sensitive variables are marked with `sensitive = true`
- No hardcoded secrets in configuration files
- Kubernetes secrets are used for runtime secret storage

### Network Security

- Network policies restrict pod-to-pod communication
- Service accounts follow principle of least privilege
- Resource quotas prevent resource exhaustion

### Access Control

- RBAC policies limit service account permissions
- Namespace isolation between environments
- Monitoring access controls via Grafana

## Monitoring and Observability

### Metrics Collection

- Prometheus scrapes application metrics from `/actuator/prometheus`
- Custom ServiceMonitor for the account service
- Node and cluster metrics via node-exporter and kube-state-metrics

### Dashboards

Pre-configured Grafana dashboards:
- Spring Boot application metrics
- JVM performance metrics
- Kubernetes cluster overview

### Alerting

Production alerts for:
- Application downtime
- High error rates
- High response times
- Memory usage thresholds

## Troubleshooting

### Common Issues

1. **Kubernetes Connection Issues**
   ```bash
   kubectl cluster-info
   kubectl config current-context
   ```

2. **Helm Repository Issues**
   ```bash
   helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
   helm repo update
   ```

3. **Storage Class Issues**
   ```bash
   kubectl get storageclass
   # Update storage_class_name variable accordingly
   ```

4. **Resource Constraints**
   ```bash
   kubectl describe nodes
   kubectl top nodes
   # Adjust resource requests/limits in variables
   ```

### Debugging

Enable Terraform debug logging:
```bash
export TF_LOG=DEBUG
terraform apply
```

Check Kubernetes resources:
```bash
kubectl get all -n account-service-dev
kubectl describe pod <pod-name> -n account-service-dev
kubectl logs <pod-name> -n account-service-dev
```

## Cleanup

### Destroy Environment

```bash
# Using the deployment script
./infrastructure/terraform/scripts/deploy.sh -e dev -a destroy

# Or manually
cd infrastructure/terraform/environments/dev
terraform destroy
```

### Complete Cleanup

```bash
# Remove all environments
for env in dev staging prod; do
  cd infrastructure/terraform/environments/$env
  terraform destroy -auto-approve
done
```

## Contributing

When modifying the infrastructure:

1. Run validation before committing:
   ```bash
   ./infrastructure/terraform/scripts/validate.sh
   ```

2. Test changes in development environment first
3. Follow the environment promotion process: dev → staging → prod
4. Update documentation for any new modules or significant changes

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review Terraform and Kubernetes logs
3. Validate prerequisites are properly installed
4. Ensure cluster connectivity and permissions