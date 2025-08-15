# Performance Testing for Account Service

This directory contains K6 performance tests for the Account Service application. The tests are designed to validate system performance under various load conditions and identify potential bottlenecks.

## Prerequisites

1. **K6 Installation**: Install K6 from [k6.io](https://k6.io/docs/getting-started/installation/)
2. **Running Service**: Ensure the Account Service is running (default: http://localhost:8080)
3. **Database**: PostgreSQL database should be running and accessible

## Test Types

### 1. Load Test (`load-test.js`)
- **Purpose**: Validate system performance under expected production load
- **Users**: Gradually ramps up to 100 concurrent users
- **Duration**: ~6 minutes
- **Thresholds**:
  - 95th percentile response time < 500ms
  - Error rate < 1%

### 2. Stress Test (`stress-test.js`)
- **Purpose**: Find the system's breaking point
- **Users**: Gradually ramps up to 500 concurrent users
- **Duration**: ~15 minutes
- **Thresholds**:
  - 95th percentile response time < 1000ms
  - Error rate < 5%

### 3. Spike Test (`spike-test.js`)
- **Purpose**: Test system resilience to sudden load increases
- **Users**: Sudden spikes up to 300 concurrent users
- **Duration**: ~3 minutes
- **Thresholds**:
  - 95th percentile response time < 2000ms
  - Error rate < 10%

## Running Tests

### Option 1: Run All Tests (Recommended)

**Linux/macOS:**
```bash
cd performance-tests
chmod +x run-performance-tests.sh
./run-performance-tests.sh
```

**Windows:**
```powershell
cd performance-tests
.\run-performance-tests.ps1
```

### Option 2: Run Individual Tests

```bash
# Load test
k6 run --env BASE_URL=http://localhost:8080 load-test.js

# Stress test
k6 run --env BASE_URL=http://localhost:8080 stress-test.js

# Spike test
k6 run --env BASE_URL=http://localhost:8080 spike-test.js
```

### Option 3: Custom Configuration

```bash
# Run with custom base URL
k6 run --env BASE_URL=http://your-service-url:8080 load-test.js

# Run with output to file
k6 run --out json=results.json load-test.js

# Run with custom thresholds
k6 run --env BASE_URL=http://localhost:8080 --summary-trend-stats="avg,min,med,max,p(95),p(99)" load-test.js
```

## Test Scenarios

Each test includes the following scenarios:

1. **User Authentication**: Register and login test users
2. **Account Creation**: Create checking and savings accounts
3. **Account Retrieval**: Get all accounts and specific accounts by ID
4. **Health Checks**: Validate service health endpoints

## Metrics and Thresholds

### Key Metrics Tracked:
- **Response Time**: HTTP request duration (p95, p99)
- **Error Rate**: Percentage of failed requests
- **Throughput**: Requests per second
- **Custom Metrics**: Business-specific error rates

### Performance Thresholds:
- **Load Test**: Production-ready performance expectations
- **Stress Test**: Degraded but acceptable performance under stress
- **Spike Test**: System survival during traffic spikes

## Results and Reporting

### Output Files:
- **JSON Results**: Detailed metrics data (`*_timestamp.json`)
- **Summary Files**: Human-readable summaries (`*_timestamp_summary.txt`)
- **Performance Report**: Comprehensive markdown report (`performance_report_timestamp.md`)

### Key Performance Indicators:
1. **Response Time P95**: 95% of requests complete within threshold
2. **Error Rate**: Percentage of failed requests
3. **Throughput**: Requests handled per second
4. **Resource Utilization**: CPU, memory, database connections

## Interpreting Results

### Good Performance Indicators:
- ✅ Response times within thresholds
- ✅ Error rates below limits
- ✅ Consistent throughput
- ✅ Stable resource utilization

### Warning Signs:
- ⚠️ Response times approaching thresholds
- ⚠️ Increasing error rates
- ⚠️ Declining throughput under load
- ⚠️ High resource utilization

### Critical Issues:
- ❌ Response times exceeding thresholds
- ❌ Error rates above limits
- ❌ System crashes or timeouts
- ❌ Resource exhaustion

## Performance Optimization Tips

1. **Database Optimization**:
   - Optimize queries and indexes
   - Configure connection pooling
   - Monitor database performance

2. **Application Tuning**:
   - JVM heap size optimization
   - Thread pool configuration
   - Caching strategies

3. **Infrastructure Scaling**:
   - Horizontal scaling (multiple instances)
   - Vertical scaling (more CPU/memory)
   - Load balancing

4. **Monitoring and Alerting**:
   - Set up performance monitoring
   - Configure alerts for thresholds
   - Regular performance testing

## Continuous Integration Integration

### GitHub Actions Example:
```yaml
- name: Run Performance Tests
  run: |
    cd performance-tests
    ./run-performance-tests.sh
  env:
    BASE_URL: http://localhost:8080
```

### Jenkins Pipeline Example:
```groovy
stage('Performance Tests') {
    steps {
        script {
            sh 'cd performance-tests && ./run-performance-tests.sh'
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'performance-tests/results/*', fingerprint: true
        }
    }
}
```

## Troubleshooting

### Common Issues:

1. **Service Not Running**:
   ```
   Error: Service is not running at http://localhost:8080
   ```
   - Solution: Start the Account Service application

2. **Authentication Failures**:
   ```
   Error: No auth token available
   ```
   - Solution: Check user registration/login endpoints

3. **High Error Rates**:
   - Check application logs
   - Verify database connectivity
   - Monitor resource utilization

4. **K6 Installation Issues**:
   - Verify K6 is in PATH
   - Check K6 version compatibility
   - Review installation documentation

## Best Practices

1. **Test Environment**:
   - Use production-like environment
   - Consistent test data
   - Isolated test runs

2. **Test Execution**:
   - Run tests regularly
   - Monitor during execution
   - Document results

3. **Performance Baselines**:
   - Establish performance baselines
   - Track performance trends
   - Set realistic thresholds

4. **Result Analysis**:
   - Analyze trends over time
   - Correlate with application changes
   - Share results with team

## Support

For questions or issues with performance testing:
1. Check the troubleshooting section
2. Review K6 documentation: https://k6.io/docs/
3. Consult application logs and metrics
4. Contact the development team

---

**Happy Performance Testing! 🚀**