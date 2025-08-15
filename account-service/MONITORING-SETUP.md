# Monitoring Setup and Testing Guide

This guide explains how to test and verify the monitoring and deployment tracking setup for the Account Service.

## Quick Start

1. **Start the Application**:
   ```bash
   cd account-service
   ./mvnw spring-boot:run
   ```

2. **Start Monitoring Stack** (optional):
   ```bash
   docker-compose --profile monitoring up -d
   ```

3. **Test Health Endpoints**:
   ```powershell
   ./scripts/test-health-endpoints.ps1
   ```

## Available Endpoints

### Health Monitoring Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Basic Spring Boot health check |
| `/actuator/prometheus` | GET | Prometheus metrics |
| `/actuator/info` | GET | Application information |
| `/api/health/status` | GET | Comprehensive health status |
| `/api/health/deployment` | GET | Deployment information |
| `/api/health/metrics` | GET | Metrics summary |
| `/api/health/check` | POST | Manual health check |
| `/api/health/deployment` | POST | Record deployment event |

### Example Requests

#### Get Health Status
```bash
curl http://localhost:8080/api/health/status
```

#### Get Deployment Info
```bash
curl http://localhost:8080/api/health/deployment
```

#### Record Deployment Event
```bash
curl -X POST http://localhost:8080/api/health/deployment \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "status=success&duration=5000"
```

#### Trigger Manual Health Check
```bash
curl -X POST http://localhost:8080/api/health/check
```

## Security Configuration

The following endpoints are publicly accessible (no authentication required):
- All `/actuator/*` endpoints
- All `/api/health/*` endpoints

This allows monitoring systems and CI/CD pipelines to access health information without authentication.

## Troubleshooting

### 403 Forbidden Error
If you get a 403 error when accessing health endpoints:

1. **Check Security Configuration**: Ensure `SecurityConfig.java` allows access to health endpoints
2. **Restart Application**: After security changes, restart the application
3. **Check Logs**: Look for security-related errors in application logs

### Endpoints Not Found
If endpoints return 404:

1. **Check Application Startup**: Ensure all beans are properly initialized
2. **Check Controller Registration**: Verify `HealthController` is being scanned
3. **Check Base Package**: Ensure controller is in the correct package

### Metrics Not Available
If Prometheus metrics are empty:

1. **Check Actuator Configuration**: Verify `management.endpoints.web.exposure.include` includes `prometheus`
2. **Check Micrometer Dependencies**: Ensure Micrometer is on the classpath
3. **Check Custom Metrics**: Verify custom metrics are properly registered

## Monitoring Stack

### Prometheus
- **URL**: http://localhost:9090
- **Targets**: Check `/targets` page to verify scraping
- **Metrics**: Search for custom metrics like `account_created_count`

### Grafana
- **URL**: http://localhost:3000
- **Login**: admin/admin
- **Dashboard**: Account Service Dashboard should be pre-loaded

## Custom Metrics

The application exposes several custom business metrics:

### Account Service Metrics
- `account_created_count` - Total accounts created
- `account_creation_latency` - Time to create accounts
- `account_total_count` - Current total accounts

### Authentication Metrics
- `auth_registration_total` - Total user registrations
- `auth_registration_failed_total` - Failed registration attempts
- `auth_registration_duration` - Registration processing time

### Deployment Metrics
- `deployment_total` - Total deployments
- `deployment_success_total` - Successful deployments
- `deployment_failure_total` - Failed deployments
- `deployment_duration_seconds` - Deployment duration
- `application_uptime_seconds` - Application uptime
- `application_health_score` - Overall health score (0-100)

### Health Check Metrics
- `health_check_total` - Total health checks performed
- `health_check_failure_total` - Failed health checks
- `health_check_duration_seconds` - Health check duration

## CI/CD Integration

The deployment tracking integrates with GitHub Actions:

1. **Deployment Start**: Records when deployment begins
2. **Deployment Success/Failure**: Records outcome
3. **Health Verification**: Performs post-deployment health checks
4. **Grafana Markers**: Creates deployment annotations
5. **Notifications**: Sends Slack notifications (if configured)

### Environment Variables for CI/CD

```yaml
env:
  GRAFANA_URL: http://localhost:3000
  GRAFANA_USER: admin
  GRAFANA_PASSWORD: admin
  SLACK_WEBHOOK_URL: https://hooks.slack.com/...
```

## Testing Scripts

### PowerShell Scripts
- `scripts/test-health-endpoints.ps1` - Test all health endpoints
- `scripts/test-monitoring.ps1` - Comprehensive monitoring test
- `scripts/deploy-monitoring.ps1` - Deploy monitoring stack

### Usage Examples

```powershell
# Test basic health endpoints
./scripts/test-health-endpoints.ps1

# Test full monitoring setup
./scripts/test-monitoring.ps1 -Environment dev

# Deploy monitoring stack
./scripts/deploy-monitoring.ps1 -Environment dev -Platform docker
```

## Production Considerations

1. **Security**: Change default passwords and enable HTTPS
2. **Resource Limits**: Set appropriate memory and CPU limits
3. **Data Retention**: Configure Prometheus retention policies
4. **Alerting**: Set up alert manager for critical alerts
5. **Backup**: Backup Grafana dashboards and Prometheus data

## Support

For issues with the monitoring setup:

1. Check application logs for errors
2. Verify all dependencies are available
3. Test endpoints individually
4. Check security configuration
5. Verify monitoring stack is running

The monitoring setup provides comprehensive observability for the Account Service, including business metrics, deployment tracking, and health monitoring.