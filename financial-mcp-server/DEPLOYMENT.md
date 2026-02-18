# MCP Financial Server - Deployment Guide

This guide provides comprehensive instructions for deploying the MCP Financial Server across different environments.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Configuration](#environment-configuration)
- [Development Deployment](#development-deployment)
- [Staging Deployment](#staging-deployment)
- [Production Deployment](#production-deployment)
- [Health Checks](#health-checks)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)
- [Rollback Procedures](#rollback-procedures)

## Prerequisites

### System Requirements

- **Docker**: Version 20.10 or higher
- **Docker Compose**: Version 2.0 or higher
- **Git**: For version control and deployment scripts
- **curl**: For health checks and API testing
- **jq**: For JSON processing (optional but recommended)

### Network Requirements

- Access to backend services (Account Service, Transaction Service)
- Database connectivity (PostgreSQL)
- Redis connectivity (for caching and sessions)
- Internet access for Docker image pulls

### Security Requirements

- Valid JWT secret key (minimum 32 characters)
- Database credentials
- SSL certificates (for production)
- Network security groups configured

## Environment Configuration

### Configuration Files

The MCP Financial Server uses environment-specific configuration files:

- `.env.dev` - Development environment
- `.env.staging` - Staging environment  
- `.env.prod` - Production environment
- `.env.example` - Template with all available options

### Key Configuration Variables

#### Core Settings
```bash
HOST=0.0.0.0                    # Server bind address
PORT=8082                       # MCP server port
DEBUG=false                     # Enable debug mode
LOG_LEVEL=INFO                  # Logging level
```

#### Service Integration
```bash
ACCOUNT_SERVICE_URL=http://account-service:8080
TRANSACTION_SERVICE_URL=http://transaction-service:8081
JWT_SECRET=your-jwt-secret-here
```

#### Performance Settings
```bash
HTTP_TIMEOUT=5000               # HTTP client timeout (ms)
MAX_RETRIES=3                   # Maximum retry attempts
CIRCUIT_BREAKER_FAILURE_THRESHOLD=5
CIRCUIT_BREAKER_RECOVERY_TIMEOUT=30
```

#### Monitoring
```bash
METRICS_ENABLED=true            # Enable Prometheus metrics
METRICS_PORT=9090               # Metrics server port
HEALTH_CHECK_ENABLED=true       # Enable health endpoints
```

### Configuration Validation

Before deployment, validate your configuration:

```bash
# Validate development config
python scripts/validate-config.py --env development --env-file .env.dev

# Validate production config
python scripts/validate-config.py --env production --env-file .env.prod
```

## Development Deployment

### Quick Start

1. **Clone and Setup**
   ```bash
   git clone <repository-url>
   cd financial-mcp-server
   cp .env.example .env.dev
   ```

2. **Configure Environment**
   Edit `.env.dev` with your local settings:
   ```bash
   # Update service URLs for local development
   ACCOUNT_SERVICE_URL=http://localhost:8080
   TRANSACTION_SERVICE_URL=http://localhost:8081
   ```

3. **Deploy**
   ```bash
   chmod +x scripts/deploy-dev.sh
   ./scripts/deploy-dev.sh
   ```

### Development Features

- **Hot Reload**: Source code changes are automatically reloaded
- **Verbose Logging**: Debug-level logging enabled
- **Development Database**: Isolated PostgreSQL instance with seed data
- **Monitoring Stack**: Prometheus and Grafana for development monitoring

### Development URLs

- MCP Server: http://localhost:8082
- Health Check: http://localhost:8082/health
- Metrics: http://localhost:9090/metrics
- Prometheus: http://localhost:9091
- Grafana: http://localhost:3000 (admin/admin)

### Development Commands

```bash
# View logs
docker-compose -f docker-compose.dev.yml logs -f mcp-financial-server

# Restart service
docker-compose -f docker-compose.dev.yml restart mcp-financial-server

# Run tests
docker-compose -f docker-compose.dev.yml exec mcp-financial-server python -m pytest

# Access container shell
docker-compose -f docker-compose.dev.yml exec mcp-financial-server bash
```

## Staging Deployment

### Prerequisites

- Staging environment secrets configured
- Network connectivity to staging backend services
- External volumes and networks created

### Deployment Process

1. **Prepare Environment**
   ```bash
   # Set required environment variables
   export JWT_SECRET="your-staging-jwt-secret"
   export DB_HOST="staging-db.example.com"
   export DB_USER="mcp_user"
   export DB_PASSWORD="secure-password"
   export REDIS_URL="redis://staging-redis.example.com:6379/0"
   ```

2. **Create External Resources**
   ```bash
   # Create network
   docker network create financial-staging-network
   
   # Create volumes
   docker volume create prometheus_staging_data
   docker volume create grafana_staging_data
   ```

3. **Deploy**
   ```bash
   chmod +x scripts/deploy-staging.sh
   ./scripts/deploy-staging.sh
   ```

### Staging Features

- **Resource Limits**: CPU and memory limits enforced
- **Health Checks**: Comprehensive health monitoring
- **Log Rotation**: Automatic log file rotation
- **Monitoring Integration**: Prometheus metrics collection

### Staging Commands

```bash
# Check deployment status
./scripts/deploy-staging.sh status

# Run staging tests
./scripts/deploy-staging.sh test

# Rollback deployment
./scripts/deploy-staging.sh rollback
```

## Production Deployment

### Prerequisites

- Production secrets management system
- Load balancer configuration
- SSL certificates
- Backup and disaster recovery plan
- Monitoring and alerting setup

### Pre-Deployment Checklist

- [ ] Staging tests passed
- [ ] Configuration validated
- [ ] Secrets properly configured
- [ ] Database migrations completed
- [ ] Monitoring alerts configured
- [ ] Rollback plan prepared

### Deployment Process

1. **Set Production Environment**
   ```bash
   # Production secrets (use secrets management system)
   export JWT_SECRET="$(vault kv get -field=jwt_secret secret/mcp-financial)"
   export DB_HOST="prod-db.example.com"
   export DB_USER="$(vault kv get -field=db_user secret/mcp-financial)"
   export DB_PASSWORD="$(vault kv get -field=db_password secret/mcp-financial)"
   export REDIS_URL="$(vault kv get -field=redis_url secret/mcp-financial)"
   export VERSION="$(git rev-parse --short HEAD)"
   ```

2. **Pre-Deployment Validation**
   ```bash
   # Validate configuration
   python scripts/validate-config.py --env production
   
   # Run pre-deployment checks
   ./scripts/pre-deployment-check.sh
   ```

3. **Deploy with Confirmation**
   ```bash
   chmod +x scripts/deploy-prod.sh
   ./scripts/deploy-prod.sh
   ```

### Production Features

- **Blue-Green Deployment**: Zero-downtime deployments
- **Auto-Scaling**: Horizontal scaling based on load
- **Health Monitoring**: Advanced health checks and monitoring
- **Security Hardening**: Production security configurations
- **Backup Integration**: Automated backup procedures

### Production Commands

```bash
# Check production status
./scripts/deploy-prod.sh status

# Scale service
docker-compose -f docker-compose.prod.yml up -d --scale mcp-financial-server=4

# Emergency rollback
./scripts/deploy-prod.sh rollback

# View production logs
docker-compose -f docker-compose.prod.yml logs -f --tail=100
```

## Health Checks

### Health Check Script

The `scripts/health-check.sh` script provides comprehensive health monitoring:

```bash
# Basic health check
./scripts/health-check.sh basic

# Check dependencies
./scripts/health-check.sh deps

# Performance check
./scripts/health-check.sh perf

# Full health check
./scripts/health-check.sh full
```

### Health Endpoints

- **Health**: `GET /health` - Basic service health
- **Readiness**: `GET /ready` - Service readiness for traffic
- **Liveness**: `GET /live` - Service liveness probe
- **Metrics**: `GET /metrics` - Prometheus metrics

### Health Check Response

```json
{
  "status": "healthy",
  "timestamp": "2024-01-15T10:30:00Z",
  "version": "1.0.0",
  "environment": "production",
  "checks": {
    "database": "healthy",
    "account_service": "healthy",
    "transaction_service": "healthy",
    "redis": "healthy"
  },
  "metrics": {
    "uptime": 86400,
    "memory_usage": "256MB",
    "active_connections": 42
  }
}
```

## Monitoring

### Prometheus Metrics

Key metrics exposed by the MCP Financial Server:

```
# Request metrics
mcp_requests_total{tool="create_account",status="success"} 150
mcp_request_duration_seconds{tool="create_account"} 0.045

# Connection metrics
mcp_active_connections 25
mcp_connection_duration_seconds 120.5

# Service metrics
service_requests_total{service="account-service",status="200"} 1250
circuit_breaker_state{service="account-service"} 0

# System metrics
process_memory_usage_bytes 268435456
process_cpu_usage_percent 15.2
```

### Grafana Dashboards

Pre-configured dashboards available:

- **MCP Overview**: High-level service metrics
- **Performance**: Response times and throughput
- **Errors**: Error rates and failure analysis
- **Infrastructure**: System resource usage

### Alerting Rules

Critical alerts configured:

- High error rate (>5% for 5 minutes)
- High response time (>2s average for 5 minutes)
- Service unavailable (health check failing)
- Memory usage high (>80% for 10 minutes)
- Circuit breaker open

## Troubleshooting

### Common Issues

#### Service Won't Start

1. **Check Configuration**
   ```bash
   python scripts/validate-config.py --env <environment>
   ```

2. **Check Dependencies**
   ```bash
   ./scripts/health-check.sh deps
   ```

3. **Check Logs**
   ```bash
   docker-compose logs mcp-financial-server
   ```

#### High Memory Usage

1. **Check Memory Metrics**
   ```bash
   curl http://localhost:9090/metrics | grep memory
   ```

2. **Analyze Memory Usage**
   ```bash
   docker stats mcp-financial-server
   ```

3. **Restart Service**
   ```bash
   docker-compose restart mcp-financial-server
   ```

#### Connection Issues

1. **Test Service Connectivity**
   ```bash
   curl -f http://localhost:8080/actuator/health  # Account Service
   curl -f http://localhost:8081/actuator/health  # Transaction Service
   ```

2. **Check Network Configuration**
   ```bash
   docker network ls
   docker network inspect financial-network
   ```

#### Authentication Failures

1. **Verify JWT Secret**
   ```bash
   echo $JWT_SECRET | wc -c  # Should be >32 characters
   ```

2. **Test JWT Token**
   ```bash
   # Use a valid JWT token to test authentication
   curl -H "Authorization: Bearer <token>" http://localhost:8082/health
   ```

### Log Analysis

#### Structured Logging

Logs are in JSON format for easy parsing:

```bash
# Filter error logs
docker-compose logs mcp-financial-server | jq 'select(.level=="ERROR")'

# Filter by request ID
docker-compose logs mcp-financial-server | jq 'select(.request_id=="req-123")'

# Performance analysis
docker-compose logs mcp-financial-server | jq 'select(.duration_ms > 1000)'
```

#### Common Log Patterns

```bash
# Authentication errors
grep "authentication_failed" logs/mcp-server.log

# Circuit breaker events
grep "circuit_breaker" logs/mcp-server.log

# Database connection issues
grep "database_error" logs/mcp-server.log
```

## Rollback Procedures

### Automatic Rollback

The deployment scripts include automatic rollback on failure:

```bash
# Staging rollback
./scripts/deploy-staging.sh rollback

# Production rollback
./scripts/deploy-prod.sh rollback
```

### Manual Rollback

1. **Identify Previous Version**
   ```bash
   docker images | grep mcp-financial-server
   ```

2. **Tag Previous Version**
   ```bash
   docker tag mcp-financial-server:previous mcp-financial-server:latest
   ```

3. **Restart Services**
   ```bash
   docker-compose up -d mcp-financial-server
   ```

### Database Rollback

If database migrations need to be rolled back:

1. **Backup Current State**
   ```bash
   pg_dump -h $DB_HOST -U $DB_USER $DB_NAME > rollback_backup.sql
   ```

2. **Apply Rollback Migration**
   ```bash
   # Run rollback scripts if available
   python manage.py migrate --rollback
   ```

### Verification After Rollback

1. **Health Check**
   ```bash
   ./scripts/health-check.sh full
   ```

2. **Functional Test**
   ```bash
   # Test critical MCP operations
   python tests/smoke_tests.py
   ```

3. **Monitor Metrics**
   ```bash
   # Check error rates and performance
   curl http://localhost:9090/metrics
   ```

## Security Considerations

### Production Security

- Use strong JWT secrets (minimum 256-bit)
- Enable HTTPS/TLS for all communications
- Implement proper CORS policies
- Use secrets management systems
- Regular security updates
- Network segmentation
- Access logging and monitoring

### Container Security

- Run as non-root user
- Use minimal base images
- Scan images for vulnerabilities
- Implement resource limits
- Use read-only filesystems where possible

## Maintenance

### Regular Tasks

- **Daily**: Monitor health and performance metrics
- **Weekly**: Review logs for errors and warnings
- **Monthly**: Update dependencies and security patches
- **Quarterly**: Review and update configuration

### Backup Procedures

- Configuration backups
- Database backups
- Log archival
- Disaster recovery testing

### Updates and Patches

1. **Test in Development**
2. **Deploy to Staging**
3. **Run Integration Tests**
4. **Deploy to Production**
5. **Monitor and Verify**

---

For additional support or questions, please refer to the project documentation or contact the development team.