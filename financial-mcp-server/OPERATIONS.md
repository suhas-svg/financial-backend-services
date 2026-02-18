# MCP Financial Server - Operations Guide

This guide provides operational procedures and best practices for managing the MCP Financial Server in production environments.

## Table of Contents

- [Daily Operations](#daily-operations)
- [Monitoring and Alerting](#monitoring-and-alerting)
- [Performance Management](#performance-management)
- [Security Operations](#security-operations)
- [Backup and Recovery](#backup-and-recovery)
- [Incident Response](#incident-response)
- [Maintenance Procedures](#maintenance-procedures)
- [Capacity Planning](#capacity-planning)

## Daily Operations

### Morning Health Check

Start each day with a comprehensive health check:

```bash
# Run full health check
./scripts/health-check.sh full

# Check service status
docker-compose -f docker-compose.prod.yml ps

# Review overnight metrics
curl -s http://localhost:9090/api/v1/query?query=mcp_requests_total | jq .
```

### Key Metrics to Monitor

#### Service Health Metrics
- **Uptime**: Service availability percentage
- **Response Time**: Average response time < 500ms
- **Error Rate**: Error rate < 1%
- **Active Connections**: Number of active MCP connections

#### Resource Metrics
- **CPU Usage**: Should be < 70% average
- **Memory Usage**: Should be < 80% of allocated
- **Disk Usage**: Should be < 85% of available
- **Network I/O**: Monitor for unusual spikes

#### Business Metrics
- **Transaction Volume**: Daily transaction counts
- **Authentication Success Rate**: > 99%
- **MCP Tool Usage**: Most frequently used tools
- **Service Integration Health**: Backend service connectivity

### Daily Checklist

- [ ] Check service health status
- [ ] Review error logs from past 24 hours
- [ ] Verify backup completion
- [ ] Check resource utilization
- [ ] Review security alerts
- [ ] Validate monitoring system health
- [ ] Check certificate expiration dates

## Monitoring and Alerting

### Prometheus Queries

#### Critical Alerts

```promql
# High error rate (>5% for 5 minutes)
rate(mcp_requests_total{status!="success"}[5m]) / rate(mcp_requests_total[5m]) > 0.05

# High response time (>2s average for 5 minutes)
histogram_quantile(0.95, rate(mcp_request_duration_seconds_bucket[5m])) > 2

# Service down (no requests for 2 minutes)
absent(up{job="mcp-financial-server"}) or up{job="mcp-financial-server"} == 0

# High memory usage (>80% for 10 minutes)
process_memory_usage_bytes / process_memory_limit_bytes > 0.8

# Circuit breaker open
circuit_breaker_state > 0
```

#### Performance Monitoring

```promql
# Request rate
rate(mcp_requests_total[5m])

# Average response time
rate(mcp_request_duration_seconds_sum[5m]) / rate(mcp_request_duration_seconds_count[5m])

# 95th percentile response time
histogram_quantile(0.95, rate(mcp_request_duration_seconds_bucket[5m]))

# Active connections
mcp_active_connections

# Service dependency health
up{job=~"account-service|transaction-service"}
```

### Alert Escalation

#### Severity Levels

**Critical (P1)**
- Service completely down
- Data corruption detected
- Security breach
- Response: Immediate (< 15 minutes)

**High (P2)**
- High error rate (>5%)
- Performance degradation (>2s response time)
- Circuit breaker activated
- Response: Within 1 hour

**Medium (P3)**
- Elevated resource usage
- Non-critical feature failures
- Configuration warnings
- Response: Within 4 hours

**Low (P4)**
- Minor performance issues
- Informational alerts
- Capacity planning warnings
- Response: Next business day

### Grafana Dashboards

#### MCP Financial Server Overview
- Service health status
- Request rate and response times
- Error rate trends
- Active connections
- Resource utilization

#### Performance Dashboard
- Response time percentiles
- Throughput metrics
- Database query performance
- Cache hit rates
- Service dependency latency

#### Infrastructure Dashboard
- CPU, memory, disk usage
- Network I/O
- Container metrics
- Host system metrics

## Performance Management

### Performance Baselines

#### Normal Operating Ranges
- **Response Time**: 50-200ms (95th percentile)
- **Throughput**: 100-500 requests/second
- **CPU Usage**: 20-50%
- **Memory Usage**: 30-60%
- **Error Rate**: <0.5%

#### Performance Optimization

**Application Level**
```bash
# Enable connection pooling
HTTP_POOL_SIZE=20
HTTP_POOL_MAXSIZE=100

# Optimize timeouts
HTTP_TIMEOUT=5000
CONNECT_TIMEOUT=2000

# Circuit breaker tuning
CIRCUIT_BREAKER_FAILURE_THRESHOLD=3
CIRCUIT_BREAKER_RECOVERY_TIMEOUT=30
```

**Container Level**
```yaml
# Resource limits
deploy:
  resources:
    limits:
      cpus: '2.0'
      memory: 1G
    reservations:
      cpus: '1.0'
      memory: 512M
```

### Load Testing

#### Regular Load Tests
```bash
# Weekly load test
artillery run tests/load/weekly-load-test.yml

# Stress test (monthly)
artillery run tests/load/stress-test.yml

# Spike test (before major releases)
artillery run tests/load/spike-test.yml
```

#### Performance Regression Testing
```bash
# Compare performance against baseline
python scripts/performance-regression-test.py --baseline v1.0.0 --current HEAD
```

## Security Operations

### Security Monitoring

#### Key Security Metrics
- Authentication failure rate
- Suspicious IP addresses
- Unusual request patterns
- Failed authorization attempts
- JWT token validation failures

#### Security Alerts

```promql
# High authentication failure rate
rate(mcp_auth_failures_total[5m]) > 10

# Suspicious request patterns
rate(mcp_requests_total{status="403"}[5m]) > 5

# JWT token validation failures
rate(jwt_validation_failures_total[5m]) > 1
```

### Security Procedures

#### Daily Security Tasks
- Review authentication logs
- Check for failed login attempts
- Monitor for unusual traffic patterns
- Verify SSL certificate status
- Review access logs for anomalies

#### Weekly Security Tasks
- Update security patches
- Review user access permissions
- Audit configuration changes
- Check for new vulnerabilities
- Review firewall logs

#### Monthly Security Tasks
- Security assessment
- Penetration testing
- Access review and cleanup
- Security training updates
- Incident response drill

### Incident Response

#### Security Incident Classification

**Critical Security Incident**
- Data breach or unauthorized access
- Service compromise
- Malware detection
- Response: Immediate isolation and investigation

**High Security Incident**
- Repeated authentication failures
- Suspicious network activity
- Configuration vulnerabilities
- Response: Within 2 hours

#### Security Incident Response Steps

1. **Immediate Response**
   ```bash
   # Isolate affected systems
   docker-compose stop mcp-financial-server
   
   # Preserve evidence
   docker logs mcp-financial-server > incident-logs-$(date +%Y%m%d-%H%M%S).log
   
   # Block suspicious IPs
   iptables -A INPUT -s <suspicious-ip> -j DROP
   ```

2. **Investigation**
   - Analyze logs for attack vectors
   - Check for data exfiltration
   - Assess system integrity
   - Document findings

3. **Recovery**
   - Apply security patches
   - Update configurations
   - Restore from clean backups if needed
   - Gradual service restoration

4. **Post-Incident**
   - Conduct post-mortem
   - Update security procedures
   - Implement additional monitoring
   - Report to stakeholders

## Backup and Recovery

### Backup Strategy

#### Configuration Backups
```bash
# Daily configuration backup
tar -czf config-backup-$(date +%Y%m%d).tar.gz \
  .env.prod docker-compose.prod.yml monitoring/

# Upload to secure storage
aws s3 cp config-backup-$(date +%Y%m%d).tar.gz s3://mcp-backups/config/
```

#### Application State Backup
```bash
# Backup application logs
tar -czf logs-backup-$(date +%Y%m%d).tar.gz logs/

# Backup metrics data
docker run --rm -v prometheus_data:/data -v $(pwd):/backup \
  alpine tar -czf /backup/prometheus-backup-$(date +%Y%m%d).tar.gz /data
```

### Recovery Procedures

#### Service Recovery
```bash
# Stop current service
docker-compose -f docker-compose.prod.yml down

# Restore from backup
tar -xzf config-backup-YYYYMMDD.tar.gz

# Start service
docker-compose -f docker-compose.prod.yml up -d

# Verify recovery
./scripts/health-check.sh full
```

#### Disaster Recovery
1. **Assess Damage**
   - Determine scope of failure
   - Identify affected components
   - Estimate recovery time

2. **Activate DR Site**
   - Switch to backup infrastructure
   - Restore from latest backups
   - Update DNS/load balancer

3. **Verify Recovery**
   - Run comprehensive tests
   - Validate data integrity
   - Monitor for issues

### Recovery Testing

#### Monthly DR Drill
```bash
# Simulate service failure
./scripts/simulate-failure.sh

# Execute recovery procedure
./scripts/disaster-recovery.sh

# Validate recovery
./scripts/validate-recovery.sh
```

## Maintenance Procedures

### Scheduled Maintenance

#### Weekly Maintenance Window
- **Time**: Sunday 2:00-4:00 AM UTC
- **Duration**: 2 hours maximum
- **Activities**:
  - Security updates
  - Configuration changes
  - Performance optimization
  - Log rotation

#### Monthly Maintenance
- **Time**: First Sunday of month, 2:00-6:00 AM UTC
- **Duration**: 4 hours maximum
- **Activities**:
  - Major updates
  - Database maintenance
  - Infrastructure changes
  - Capacity adjustments

### Maintenance Procedures

#### Pre-Maintenance Checklist
- [ ] Notify stakeholders
- [ ] Create backup
- [ ] Prepare rollback plan
- [ ] Test changes in staging
- [ ] Schedule maintenance window

#### During Maintenance
```bash
# Enable maintenance mode
echo "maintenance" > /app/status

# Perform updates
./scripts/update-service.sh

# Run post-update tests
./scripts/post-update-tests.sh

# Disable maintenance mode
rm /app/status
```

#### Post-Maintenance Checklist
- [ ] Verify service health
- [ ] Check performance metrics
- [ ] Review error logs
- [ ] Notify completion
- [ ] Document changes

### Update Procedures

#### Security Updates
```bash
# Check for security updates
./scripts/check-security-updates.sh

# Apply critical security patches
./scripts/apply-security-patches.sh

# Restart services if required
docker-compose restart mcp-financial-server
```

#### Application Updates
```bash
# Deploy new version
VERSION=v1.2.0 ./scripts/deploy-prod.sh

# Monitor deployment
./scripts/monitor-deployment.sh

# Rollback if issues detected
./scripts/deploy-prod.sh rollback
```

## Capacity Planning

### Resource Monitoring

#### CPU Usage Trends
```promql
# Average CPU usage over time
avg_over_time(process_cpu_usage_percent[24h])

# Peak CPU usage
max_over_time(process_cpu_usage_percent[24h])
```

#### Memory Usage Trends
```promql
# Memory usage growth
increase(process_memory_usage_bytes[7d])

# Memory utilization percentage
process_memory_usage_bytes / process_memory_limit_bytes * 100
```

#### Request Volume Trends
```promql
# Daily request volume
sum(increase(mcp_requests_total[24h]))

# Peak request rate
max_over_time(rate(mcp_requests_total[5m])[24h])
```

### Scaling Decisions

#### Horizontal Scaling Triggers
- CPU usage > 70% for 15 minutes
- Memory usage > 80% for 10 minutes
- Response time > 1s for 5 minutes
- Active connections > 80% of limit

#### Vertical Scaling Triggers
- Consistent resource exhaustion
- Memory leaks detected
- CPU bottlenecks identified
- Storage capacity issues

### Capacity Planning Process

#### Monthly Capacity Review
1. **Analyze Growth Trends**
   - Request volume growth
   - Resource usage trends
   - Performance degradation patterns

2. **Forecast Requirements**
   - Project 3-month resource needs
   - Identify potential bottlenecks
   - Plan infrastructure changes

3. **Update Capacity Plan**
   - Adjust resource allocations
   - Schedule infrastructure upgrades
   - Update monitoring thresholds

#### Capacity Planning Tools
```bash
# Generate capacity report
python scripts/capacity-report.py --period 30d

# Forecast resource needs
python scripts/forecast-capacity.py --horizon 90d

# Analyze performance trends
python scripts/performance-analysis.py --metrics cpu,memory,requests
```

---

This operations guide should be reviewed and updated quarterly to ensure it remains current with operational practices and system changes.