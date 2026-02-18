# MCP Financial Server - Deployment Setup

This document provides a quick start guide for deploying the MCP Financial Server across different environments.

## Quick Start

### Prerequisites

Ensure you have the following installed:
- Docker (20.10+)
- Docker Compose (2.0+)
- Git
- Python 3.11+ (for validation scripts)

### 1. Development Environment

**Linux/macOS:**
```bash
# Quick setup
make setup-dev
make dev

# Or manual setup
cp .env.example .env.dev
# Edit .env.dev with your settings
./scripts/deploy-dev.sh
```

**Windows:**
```powershell
# Quick setup
Copy-Item .env.example .env.dev
# Edit .env.dev with your settings
.\scripts\deploy-dev.ps1
```

**Using Make (cross-platform):**
```bash
make quick-start
```

### 2. Staging Environment

```bash
# Setup staging configuration
make setup-staging
# Edit .env.staging with staging settings

# Deploy to staging
make staging

# Run staging tests
make staging-test
```

### 3. Production Environment

```bash
# Setup production configuration
make setup-prod
# Edit .env.prod with production settings

# Validate configuration
make validate-prod

# Deploy to production (requires confirmation)
make prod
```

## Environment Configuration

### Development (.env.dev)
- Local service URLs
- Debug logging enabled
- Relaxed security settings
- Hot reload enabled

### Staging (.env.staging)
- Staging service URLs
- Production-like settings
- Moderate security
- Performance monitoring

### Production (.env.prod)
- Production service URLs
- Strict security settings
- Optimized performance
- Full monitoring and alerting

## Available Commands

### Make Commands
```bash
make help              # Show all available commands
make dev               # Deploy development environment
make staging           # Deploy to staging
make prod              # Deploy to production
make test              # Run tests
make health            # Run health checks
make validate          # Validate configuration
make clean             # Clean up containers and images
make logs              # View service logs
```

### Direct Script Usage

**Linux/macOS:**
```bash
./scripts/deploy-dev.sh
./scripts/deploy-staging.sh
./scripts/deploy-prod.sh
./scripts/health-check.sh full
python scripts/validate-config.py --env production
```

**Windows:**
```powershell
.\scripts\deploy-dev.ps1
.\scripts\health-check.ps1 -Mode full
python scripts\validate-config.py --env production
```

## Health Monitoring

### Health Check Endpoints
- **Health**: `http://localhost:8082/health`
- **Readiness**: `http://localhost:8082/ready`
- **Metrics**: `http://localhost:9090/metrics`

### Monitoring URLs (Development)
- **Prometheus**: http://localhost:9091
- **Grafana**: http://localhost:3000 (admin/admin)

### Health Check Commands
```bash
# Full health check
make health

# Or directly
./scripts/health-check.sh full        # Linux/macOS
.\scripts\health-check.ps1 -Mode full # Windows
```

## Configuration Validation

Before deploying to any environment, validate your configuration:

```bash
# Validate development config
make validate

# Validate staging config
make validate-staging

# Validate production config
make validate-prod
```

## Troubleshooting

### Common Issues

1. **Service won't start**
   ```bash
   # Check configuration
   make validate
   
   # Check logs
   make logs
   
   # Check dependencies
   make health deps
   ```

2. **Health check fails**
   ```bash
   # Run detailed health check
   make health
   
   # Check service dependencies
   ./scripts/health-check.sh deps
   ```

3. **Performance issues**
   ```bash
   # Run performance check
   ./scripts/health-check.sh perf
   
   # Check resource usage
   docker stats
   ```

### Log Analysis
```bash
# View real-time logs
make logs

# View specific service logs
docker-compose logs -f mcp-financial-server

# Search for errors
docker-compose logs mcp-financial-server | grep ERROR
```

## Deployment Checklist

### Before Deployment
- [ ] Configuration validated
- [ ] Tests passing
- [ ] Dependencies available
- [ ] Secrets configured
- [ ] Backup created

### After Deployment
- [ ] Health checks passing
- [ ] Monitoring active
- [ ] Performance acceptable
- [ ] Security validated
- [ ] Documentation updated

## Security Considerations

### JWT Secret
- Minimum 32 characters
- Use cryptographically secure random generation
- Different secrets for each environment
- Store in secure secrets management system

### Network Security
- Use HTTPS in production
- Implement proper CORS policies
- Configure firewall rules
- Use VPN for internal communications

### Container Security
- Run as non-root user
- Use minimal base images
- Regular security updates
- Resource limits configured

## Backup and Recovery

### Configuration Backup
```bash
make backup
```

### Recovery Process
1. Stop current services
2. Restore configuration from backup
3. Start services
4. Verify health

## Support

### Documentation
- [DEPLOYMENT.md](DEPLOYMENT.md) - Comprehensive deployment guide
- [OPERATIONS.md](OPERATIONS.md) - Operations and maintenance guide
- [README.md](README.md) - Project overview and setup

### Monitoring and Alerts
- Prometheus metrics at `:9090/metrics`
- Grafana dashboards at `:3000`
- Health endpoints for monitoring systems

### Getting Help
1. Check the logs: `make logs`
2. Run health checks: `make health`
3. Validate configuration: `make validate`
4. Review documentation
5. Contact the development team

---

For detailed deployment procedures and operational guidance, see [DEPLOYMENT.md](DEPLOYMENT.md) and [OPERATIONS.md](OPERATIONS.md).