# Transaction Service - Advanced Monitoring and Observability Guide

## Overview

The Transaction Service implements comprehensive monitoring and observability features including:

- **Micrometer Metrics** with Prometheus integration
- **Custom Business Metrics** for transaction monitoring
- **Distributed Tracing** with correlation headers
- **Dashboard-Ready Metrics** with percentiles and SLAs
- **Alerting** for critical transaction failures
- **Structured JSON Logging** with audit trails
- **Health Checks** for all dependencies

## Architecture

### Monitoring Components

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Application   │    │   Micrometer    │    │   Prometheus    │
│   Metrics       │───▶│   Registry      │───▶│   Endpoint      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                        │
┌─────────────────┐    ┌─────────────────┐             │
│   Distributed   │    │   Correlation   │             ▼
│   Tracing       │───▶│   Headers       │    ┌─────────────────┐
└─────────────────┘    └─────────────────┘    │     Grafana     │
                                              │   Dashboard     │
┌─────────────────┐    ┌─────────────────┐    └─────────────────┘
│   Alerting      │    │   Business      │
│   Service       │───▶│   Metrics       │
└─────────────────┘    └─────────────────┘
```

## Key Metrics

### Transaction Metrics

| Metric Name | Type | Description | Labels |
|-------------|------|-------------|--------|
| `transaction.initiated.total` | Counter | Total transactions initiated | - |
| `transaction.completed.total` | Counter | Total transactions completed | - |
| `transaction.failed.total` | Counter | Total transactions failed | - |
| `transaction.processing.duration` | Timer | Transaction processing time | type, status |
| `transaction.type.total` | Counter | Transactions by type | type |
| `transaction.status.total` | Counter | Transactions by status | status |
| `transaction.active.count` | Gauge | Current active transactions | - |
| `transaction.daily.volume` | Gauge | Daily transaction count | - |
| `transaction.daily.amount` | Gauge | Daily transaction amount | - |

### Error Metrics

| Metric Name | Type | Description |
|-------------|------|-------------|
| `transaction.error.insufficient_funds.total` | Counter | Insufficient funds errors |
| `transaction.error.account_not_found.total` | Counter | Account not found errors |
| `transaction.error.limit_exceeded.total` | Counter | Transaction limit exceeded |
| `account.service.error.total` | Counter | Account Service errors |

### Performance Metrics

| Metric Name | Type | Description | SLAs |
|-------------|------|-------------|------|
| `transaction.processing.duration` | Timer | Processing time | 50ms, 100ms, 200ms, 500ms, 1s, 2s, 5s |
| `account.service.call.duration` | Timer | Account Service calls | 100ms, 250ms, 500ms, 1s, 2s, 5s |
| `database.operation.duration` | Timer | Database operations | 10ms, 50ms, 100ms, 250ms, 500ms, 1s |

### Alert Metrics

| Metric Name | Type | Description |
|-------------|------|-------------|
| `alerts.critical.total` | Counter | Critical alerts triggered |
| `alerts.warning.total` | Counter | Warning alerts triggered |
| `alerts.info.total` | Counter | Info alerts triggered |

## Distributed Tracing

### Correlation Headers

The service automatically adds and propagates the following headers:

| Header | Description | Example |
|--------|-------------|---------|
| `X-Correlation-ID` | Request correlation ID | `550e8400-e29b-41d4-a716-446655440000` |
| `X-Trace-ID` | Distributed trace ID | `1234567890abcdef` |
| `X-Span-ID` | Current span ID | `abcdef1234567890` |
| `X-Request-ID` | Unique request ID | `req_550e8400-e29b-41d4` |
| `X-User-ID` | User identifier | `user123` |
| `X-Session-ID` | Session identifier | `sess_abc123` |
| `X-Client-ID` | Client application ID | `mobile-app` |
| `X-Request-Source` | Request source | `web`, `mobile`, `api` |
| `X-Business-Context` | Business context | `transfer`, `deposit` |

### MDC Context

All correlation headers are automatically added to the logging MDC context for structured logging.

## Alerting Rules

### Critical Alerts

| Alert | Condition | Duration | Description |
|-------|-----------|----------|-------------|
| `TransactionHighErrorRate` | Error rate > 5% | 2 minutes | High transaction error rate |
| `TransactionLowSuccessRate` | Success rate < 95% | 3 minutes | Low transaction success rate |
| `AccountServiceUnavailable` | Availability < 95% | 1 minute | Account Service unavailable |
| `TransactionServiceDown` | Service down | 1 minute | Service health check failed |

### Warning Alerts

| Alert | Condition | Duration | Description |
|-------|-----------|----------|-------------|
| `TransactionSlowProcessing` | P95 > 2000ms | 5 minutes | Slow transaction processing |
| `TransactionHighDailyVolume` | Volume > 10,000 | Immediate | High daily volume |
| `DatabaseSlowQueries` | Avg query > 500ms | 3 minutes | Slow database queries |
| `JVMHighMemoryUsage` | Memory > 85% | 5 minutes | High JVM memory usage |

## Dashboard Configuration

### Key Panels

1. **Transaction Success Rate** - Real-time success rate with thresholds
2. **Transaction Throughput** - Transactions per second
3. **Active Transactions** - Current active transaction count
4. **Processing Time Percentiles** - P50, P95, P99 response times
5. **Error Rate by Type** - Breakdown of error types
6. **Account Service Performance** - Response time and availability
7. **Database Performance** - Query times and connection pool
8. **JVM Metrics** - Memory usage and GC performance
9. **Alert Status** - Current alert counts and status

### SLA Monitoring

The dashboard includes SLA monitoring for:
- **Transaction Success Rate**: > 99%
- **Transaction Processing Time**: P95 < 2 seconds
- **Account Service Availability**: > 99%
- **Database Query Time**: P95 < 500ms

## Endpoints

### Monitoring Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Service health status |
| `/actuator/metrics` | All available metrics |
| `/actuator/prometheus` | Prometheus metrics format |
| `/api/monitoring/health/detailed` | Detailed health with business metrics |
| `/api/monitoring/stats/transactions` | Transaction statistics |
| `/api/monitoring/stats/system` | System performance metrics |
| `/api/monitoring/alerts/status` | Alert status and configuration |

### Health Indicators

- **Database Health**: PostgreSQL connectivity and query performance
- **Redis Health**: Cache connectivity and performance
- **Account Service Health**: External service availability
- **Transaction Service Health**: Internal service status

## Configuration

### Application Properties

```properties
# Monitoring Configuration
management.endpoints.web.exposure.include=health,info,metrics,prometheus,httpexchanges
management.endpoint.prometheus.enabled=true
management.metrics.export.prometheus.enabled=true

# Distributed Tracing
management.tracing.enabled=true
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans

# Alerting Thresholds
alerting.error-rate.threshold=0.05
alerting.response-time.threshold=2000
alerting.account-service.error-threshold=10
alerting.daily-volume.threshold=10000

# Dashboard Metrics
management.metrics.distribution.percentiles-histogram.transaction.processing.duration=true
management.metrics.distribution.percentiles.transaction.processing.duration=0.5,0.95,0.99
management.metrics.distribution.sla.transaction.processing.duration=50ms,100ms,200ms,500ms,1s,2s,5s
```

### Prometheus Configuration

```yaml
scrape_configs:
  - job_name: 'transaction-service'
    static_configs:
      - targets: ['localhost:8081']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
```

### Grafana Dashboard

Import the provided `grafana-dashboard.json` for comprehensive monitoring visualization.

## Logging

### Structured JSON Logging

All logs are output in structured JSON format with correlation IDs:

```json
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "com.suhasan.finance.transaction_service.service.TransactionServiceImpl",
  "message": "Transaction processed successfully",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "traceId": "1234567890abcdef",
  "spanId": "abcdef1234567890",
  "userId": "user123",
  "transactionId": "txn_abc123",
  "transactionType": "TRANSFER",
  "amount": 100.00,
  "processingTime": 150
}
```

### Audit Logging

Financial transactions are logged to a separate audit log with:
- Transaction details
- User context
- Processing timestamps
- Success/failure status
- Error details (if applicable)

## Best Practices

### Metric Naming

- Use consistent naming conventions
- Include units in metric names where appropriate
- Use labels for dimensions, not metric names
- Keep cardinality reasonable

### Alerting

- Set appropriate thresholds based on SLAs
- Use different severity levels
- Include runbook links in alert annotations
- Implement alert suppression to prevent spam

### Tracing

- Always propagate correlation headers
- Include business context in traces
- Use meaningful span names
- Add relevant tags to spans

### Dashboard Design

- Group related metrics together
- Use appropriate visualization types
- Include SLA thresholds
- Provide drill-down capabilities

## Troubleshooting

### Common Issues

1. **High Memory Usage**
   - Check JVM heap settings
   - Review metric cardinality
   - Monitor garbage collection

2. **Missing Metrics**
   - Verify Micrometer configuration
   - Check metric filters
   - Ensure proper bean registration

3. **Tracing Issues**
   - Verify Zipkin endpoint configuration
   - Check sampling probability
   - Ensure proper header propagation

4. **Alert Fatigue**
   - Review alert thresholds
   - Implement proper suppression
   - Use appropriate severity levels

### Debugging

Enable debug logging for monitoring components:

```properties
logging.level.com.suhasan.finance.transaction_service.service.MetricsService=DEBUG
logging.level.com.suhasan.finance.transaction_service.service.AlertingService=DEBUG
logging.level.com.suhasan.finance.transaction_service.aspect.MonitoringAspect=DEBUG
```

## Performance Impact

The monitoring implementation is designed for minimal performance impact:

- **Metrics Collection**: < 1ms overhead per operation
- **Distributed Tracing**: < 2ms overhead per request
- **Structured Logging**: Asynchronous appenders for non-blocking I/O
- **Health Checks**: Cached results with configurable TTL

## Security Considerations

- Sensitive data is masked in logs
- Correlation headers don't contain PII
- Metrics endpoints can be secured with authentication
- Audit logs are tamper-evident

## Maintenance

### Regular Tasks

- Review and update alert thresholds
- Clean up old log files
- Monitor metric cardinality
- Update dashboard configurations
- Review and optimize queries

### Capacity Planning

Monitor these metrics for capacity planning:
- Transaction volume trends
- Processing time trends
- Resource utilization
- Error rate patterns