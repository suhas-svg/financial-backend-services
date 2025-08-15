# Transaction Service Performance Testing

This document describes the comprehensive performance testing suite for the Transaction Service, including load testing, database performance analysis, cache performance validation, and concurrent processing tests.

## Overview

The performance testing suite includes:

- **Load Testing**: Concurrent transaction processing under realistic load
- **Database Performance**: Query optimization and large dataset handling
- **Cache Performance**: Redis cache hit rates and response times
- **Stress Testing**: System behavior under extreme load conditions
- **JMeter Integration**: Industry-standard load testing tools

## Test Structure

```
src/test/java/com/suhasan/finance/transaction_service/performance/
├── TransactionPerformanceTest.java          # Main load and concurrent tests
├── DatabasePerformanceBenchmark.java       # Database query performance
├── CachePerformanceBenchmark.java         # Redis cache performance
└── JMeterLoadTest.java                     # JMeter integration tests

src/test/resources/
├── performance-test-schema.sql             # Optimized database schema
├── application-performance.properties      # Performance test configuration
└── application-test.properties            # Base test configuration

scripts/
└── run-performance-tests.ps1              # Performance test runner script
```

## Running Performance Tests

### Prerequisites

1. **Docker**: Required for Testcontainers (PostgreSQL and Redis)
2. **Java 22**: Required for running the tests
3. **Maven 3.8+**: For building and running tests
4. **Minimum 4GB RAM**: For JVM performance testing

### Quick Start

```bash
# Run all performance tests
./scripts/run-performance-tests.ps1

# Run specific test types
./scripts/run-performance-tests.ps1 -TestType load
./scripts/run-performance-tests.ps1 -TestType database
./scripts/run-performance-tests.ps1 -TestType cache
./scripts/run-performance-tests.ps1 -TestType jmeter

# Run with custom parameters
./scripts/run-performance-tests.ps1 -Threads 100 -Duration 600 -Verbose
```

### Maven Commands

```bash
# Run performance tests with Maven profile
mvn test -Pperformance

# Run specific performance test class
mvn test -Dtest=TransactionPerformanceTest -Pperformance

# Run with JVM tuning for performance
mvn test -Pperformance -Dargline="-Xms2g -Xmx8g -XX:+UseG1GC"
```

## Test Scenarios

### 1. Load Testing (`TransactionPerformanceTest`)

**Concurrent Transaction Processing**
- 50 concurrent users
- 20 transactions per user (1000 total transactions)
- Mixed transaction types (transfers, deposits, withdrawals)
- Performance thresholds:
  - 95th percentile response time < 1000ms
  - Success rate > 95%
  - Throughput > 10 TPS

**Stress Testing**
- 100 concurrent users
- Extreme load conditions
- System stability validation
- Graceful degradation testing

### 2. Database Performance (`DatabasePerformanceBenchmark`)

**Bulk Insert Performance**
- Tests different batch sizes (100, 500, 1000, 2000)
- Measures insert rates (records/second)
- Validates data integrity

**Query Performance Analysis**
- Account lookup queries
- Date range queries
- Status-based queries
- Pagination performance
- Index effectiveness analysis

**Concurrent Read/Write Operations**
- 10 concurrent threads
- Mixed read/write operations (67% reads, 33% writes)
- Data consistency validation
- Connection pool performance

### 3. Cache Performance (`CachePerformanceBenchmark`)

**Basic Operations**
- SET/GET/DEL operation rates
- Performance thresholds:
  - SET operations > 1000 ops/sec
  - GET operations > 5000 ops/sec
  - DEL operations > 1000 ops/sec

**Cache Hit Rate Analysis**
- Realistic access patterns (80/20 rule)
- Hit rate > 75% for typical usage
- Response time analysis for hits vs misses

**Memory Management**
- Cache eviction performance
- Memory usage optimization
- Expiration handling

**Concurrent Access**
- 20 concurrent threads
- Mixed read/write operations
- Error rate < 1%
- Operations per second > 1000

### 4. JMeter Integration (`JMeterLoadTest`)

**HTTP Load Testing**
- Programmatic JMeter test plan creation
- REST API endpoint testing
- Configurable load patterns
- Results export (JTL format)

## Performance Metrics

### Response Time Metrics
- Average response time
- 50th, 95th, 99th percentile response times
- Maximum response time
- Response time distribution

### Throughput Metrics
- Transactions per second (TPS)
- Requests per second (RPS)
- Concurrent user capacity
- System saturation point

### Resource Utilization
- CPU usage patterns
- Memory consumption
- Database connection pool usage
- Cache memory utilization

### Error Metrics
- Error rate percentage
- Error type distribution
- Timeout occurrences
- System failure points

## Performance Thresholds

| Metric | Threshold | Test |
|--------|-----------|------|
| Average Response Time | < 500ms | Load Test |
| 95th Percentile Response Time | < 1000ms | Load Test |
| Success Rate | > 95% | All Tests |
| Throughput | > 100 TPS | Load Test |
| Database Query Time | < 100ms | Database Test |
| Cache Hit Rate | > 80% | Cache Test |
| Cache Response Time | < 10ms | Cache Test |
| Concurrent User Capacity | > 50 users | Stress Test |

## Configuration

### JVM Tuning
```bash
-Xms1g -Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
```

### Database Configuration
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
```

### Redis Configuration
```properties
spring.data.redis.lettuce.pool.max-active=20
spring.data.redis.lettuce.pool.max-idle=10
spring.cache.redis.time-to-live=300000
```

## Interpreting Results

### Good Performance Indicators
- ✅ Response times consistently under thresholds
- ✅ High cache hit rates (>80%)
- ✅ Low error rates (<1%)
- ✅ Linear scalability with load
- ✅ Stable memory usage
- ✅ Efficient database query execution

### Performance Issues
- ❌ Response times increasing with load
- ❌ High error rates under load
- ❌ Memory leaks or excessive GC
- ❌ Database connection pool exhaustion
- ❌ Cache miss rates increasing
- ❌ System instability under stress

### Optimization Recommendations

**Database Optimization**
- Add missing indexes for slow queries
- Optimize query patterns
- Increase connection pool size if needed
- Consider read replicas for heavy read workloads

**Cache Optimization**
- Increase cache memory allocation
- Optimize cache key patterns
- Implement cache warming strategies
- Fine-tune expiration policies

**Application Optimization**
- Optimize business logic
- Implement connection pooling
- Add circuit breakers for external services
- Optimize serialization/deserialization

**Infrastructure Optimization**
- Scale horizontally with load balancers
- Increase server resources (CPU, RAM)
- Optimize network configuration
- Implement CDN for static content

## Continuous Performance Monitoring

### Integration with CI/CD
```yaml
# Example GitHub Actions integration
- name: Run Performance Tests
  run: |
    ./scripts/run-performance-tests.ps1 -TestType all
    
- name: Upload Performance Results
  uses: actions/upload-artifact@v3
  with:
    name: performance-results
    path: target/performance-reports/
```

### Monitoring in Production
- Set up application performance monitoring (APM)
- Configure alerts for performance degradation
- Regular performance baseline updates
- Automated performance regression detection

## Troubleshooting

### Common Issues

**Testcontainers Issues**
```bash
# Ensure Docker is running
docker --version

# Clean up containers
docker system prune -f
```

**Memory Issues**
```bash
# Increase JVM heap size
export MAVEN_OPTS="-Xms2g -Xmx8g"
```

**Database Connection Issues**
```bash
# Check PostgreSQL container logs
docker logs <container-id>
```

**Redis Connection Issues**
```bash
# Check Redis container logs
docker logs <redis-container-id>
```

### Performance Debugging

1. **Enable detailed logging**
   ```properties
   logging.level.com.suhasan.finance.transaction_service=DEBUG
   logging.level.org.hibernate.SQL=DEBUG
   ```

2. **Use profiling tools**
   - JProfiler
   - VisualVM
   - Java Flight Recorder

3. **Database query analysis**
   ```sql
   EXPLAIN ANALYZE SELECT * FROM transactions WHERE ...;
   ```

4. **Redis monitoring**
   ```bash
   redis-cli monitor
   redis-cli info memory
   ```

## Best Practices

1. **Test Environment**
   - Use production-like data volumes
   - Test with realistic network latency
   - Include external service dependencies

2. **Test Data Management**
   - Use representative test data
   - Clean up test data between runs
   - Avoid test data pollution

3. **Result Analysis**
   - Establish performance baselines
   - Track performance trends over time
   - Correlate performance with code changes

4. **Automation**
   - Integrate with CI/CD pipelines
   - Automate performance regression detection
   - Generate automated performance reports

## Support

For questions or issues with performance testing:

1. Check the test logs in `target/surefire-reports/`
2. Review the performance report in `target/performance-reports/`
3. Examine container logs for infrastructure issues
4. Consult the main project documentation

## Performance Test Checklist

- [ ] All performance tests pass
- [ ] Response times within thresholds
- [ ] Cache hit rates acceptable
- [ ] Database queries optimized
- [ ] No memory leaks detected
- [ ] Concurrent processing stable
- [ ] Error rates minimal
- [ ] Performance report generated
- [ ] Results documented
- [ ] Baseline metrics updated