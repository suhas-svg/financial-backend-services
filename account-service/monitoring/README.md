# Account Service Monitoring Stack

This directory contains the complete monitoring setup for the Account Service, including Prometheus for metrics collection and Grafana for visualization.

## Overview

The monitoring stack provides:
- **Prometheus**: Metrics collection and alerting
- **Grafana**: Visualization dashboards and alerting
- **Custom Business Metrics**: Account operations, authentication, and performance metrics
- **Infrastructure Metrics**: JVM, database, and system metrics
- **Alert Rules**: Critical system and business alerts

## Quick Start

### Docker Compose Deployment

```bash
# Deploy monitoring stack
./scripts/deploy-monitoring.sh dev docker

# Or using PowerShell
./scripts/deploy-monitoring.ps1 -Environment dev -Platform docker
```

### Kubernetes Deployment

```bash
# Deploy to Kubernetes
./scripts/deploy-monitoring.sh dev kubernetes finance-services

# Or using PowerShell
./scripts/deploy-monitoring.ps1 -Environment dev -Platform kubernetes -Namespace finance-services
```

## Access Information

### Local Development (Docker)
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000
  - Username: `admin`
  - Password: `admin` (dev) / `secure-admin-password` (prod)

### Kubernetes
Use port forwarding to access services:
```bash
# Prometheus
kubectl port-forward -n finance-services svc/prometheus 9090:9090

# Grafana
kubectl port-forward -n finance-services svc/grafana 3000:3000
```

## Dashboards

### Account Service Dashboard
The main dashboard includes:
- **Request Rate**: HTTP requests per second by endpoint
- **Response Time**: 95th and 50th percentile response times
- **Error Rate**: HTTP 5xx error percentage
- **JVM Memory**: Heap memory usage and limits
- **Database Connections**: Active vs max connections
- **Business Metrics**: Account operations and authentication metrics

### Custom Metrics

#### Business Metrics
- `account_created_count`: Total accounts created
- `account_creation_latency`: Time to create accounts
- `account_total_count`: Current total accounts
- `auth_registration_total`: Total user registrations
- `auth_registration_failed_total`: Failed registration attempts
- `auth_registration_duration`: Registration processing time

#### Infrastructure Metrics
- `jvm_memory_used_bytes`: JVM memory usage
- `hikaricp_connections_active`: Database connection pool usage
- `http_server_requests_seconds`: HTTP request metrics
- `system_cpu_usage`: System CPU utilization

## Alert Rules

### Critical Alerts
- **ApplicationDown**: Service is unreachable
- **HighErrorRate**: Error rate > 5% for 2 minutes
- **DatabaseDown**: PostgreSQL is unreachable
- **HighMemoryUsage**: JVM heap > 85% for 5 minutes

### Warning Alerts
- **HighResponseTime**: 95th percentile > 2s for 3 minutes
- **DatabaseConnectionsHigh**: Connection pool > 80% for 2 minutes
- **HighFailedAuthenticationRate**: Failed auth > 10/sec for 2 minutes
- **HighCPUUsage**: CPU > 80% for 5 minutes

## Configuration Files

### Prometheus Configuration
- `prometheus.yml`: Main Prometheus configuration
- `alert_rules.yml`: Alert rule definitions

### Grafana Configuration
- `datasources/prometheus.yml`: Prometheus datasource configuration
- `dashboards/dashboard.yml`: Dashboard provisioning configuration
- `dashboards/account-service-dashboard.json`: Main service dashboard

### Kubernetes Resources
- `k8s/monitoring/prometheus-deployment.yaml`: Prometheus deployment
- `k8s/monitoring/prometheus-config.yaml`: Prometheus ConfigMap
- `k8s/monitoring/grafana-deployment.yaml`: Grafana deployment
- `k8s/monitoring/grafana-config.yaml`: Grafana ConfigMaps

## Customization

### Adding Custom Metrics
1. Add metric definitions in `MetricsConfig.java`
2. Instrument your service classes with the metrics
3. Update Grafana dashboards to display new metrics
4. Add alert rules for critical thresholds

### Adding New Dashboards
1. Create dashboard JSON in `dashboards/` directory
2. Update `dashboard.yml` provisioning configuration
3. Restart Grafana or wait for auto-reload

### Configuring Alerts
1. Update `alert_rules.yml` with new rules
2. Configure notification channels in Grafana
3. Test alert rules with simulated conditions

## Troubleshooting

### Common Issues

#### Prometheus Not Scraping Targets
- Check if application exposes `/actuator/prometheus` endpoint
- Verify network connectivity between Prometheus and application
- Check Prometheus logs: `docker logs account-service-prometheus-dev`

#### Grafana Dashboard Not Loading
- Verify Prometheus datasource is configured correctly
- Check Grafana logs: `docker logs account-service-grafana-dev`
- Ensure dashboard JSON is valid

#### Missing Metrics
- Verify custom metrics are registered in `MetricsConfig.java`
- Check application logs for metric registration errors
- Confirm metrics appear in Prometheus targets page

### Debugging Commands

```bash
# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Check Prometheus metrics
curl http://localhost:9090/api/v1/label/__name__/values

# Check application metrics endpoint
curl http://localhost:8080/actuator/prometheus

# Check Grafana datasources
curl -u admin:admin http://localhost:3000/api/datasources
```

## Production Considerations

### Security
- Change default Grafana admin password
- Enable HTTPS for all monitoring endpoints
- Configure proper authentication and authorization
- Use secrets management for sensitive configuration

### Performance
- Configure appropriate retention policies for Prometheus
- Set up remote storage for long-term metrics retention
- Monitor resource usage of monitoring stack itself
- Configure proper resource limits in Kubernetes

### High Availability
- Deploy Prometheus in HA mode with multiple replicas
- Use external storage for Grafana dashboards and configuration
- Set up monitoring for the monitoring stack itself
- Configure backup and restore procedures

## Integration with CI/CD

The monitoring stack integrates with the CI/CD pipeline to:
- Create deployment markers in Grafana
- Monitor deployment success/failure rates
- Alert on deployment-related issues
- Track performance regression after deployments

See the main CI/CD pipeline documentation for deployment tracking implementation.