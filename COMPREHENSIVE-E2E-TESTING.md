# Comprehensive Full System End-to-End Testing

This document describes the comprehensive end-to-end testing suite for the Account Service and Transaction Service integration.

## Overview

The comprehensive E2E testing suite validates the complete integration between Account Service and Transaction Service using multiple testing approaches:

1. **Docker Compose Integration Tests** - Full system orchestration with real service communication
2. **Java Testcontainers Tests** - Programmatic integration testing with isolated containers
3. **PowerShell Integration Tests** - External API validation and workflow testing

## Test Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                 Comprehensive E2E Test Suite                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ Docker Compose  │  │ Java            │  │ PowerShell   │ │
│  │ Integration     │  │ Testcontainers  │  │ Integration  │ │
│  │                 │  │                 │  │              │ │
│  │ • Real services │  │ • Isolated      │  │ • API        │ │
│  │ • Network comm  │  │ • Programmatic  │  │ • Black-box  │ │
│  │ • Production-   │  │ • Fine control  │  │ • HTTP client│ │
│  │   like env      │  │                 │  │              │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│                    Test Coverage                            │
│                                                             │
│ ✅ Service-to-service communication                         │
│ ✅ Database consistency across services                     │
│ ✅ Complete user transaction journeys                       │
│ ✅ Error handling and resilience                           │
│ ✅ Concurrent operations                                    │
│ ✅ Data consistency and ACID properties                     │
│ ✅ Performance and response times                           │
│ ✅ Authentication and authorization                         │
└─────────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 22
- Maven (wrapper included)
- PowerShell 7+

### Run All Tests

```powershell
# Run comprehensive test suite with all approaches
./run-comprehensive-e2e-tests.ps1 -TestType "all" -CleanStart $true -GenerateReport $true

# Run specific test type
./run-comprehensive-e2e-tests.ps1 -TestType "docker"     # Docker Compose only
./run-comprehensive-e2e-tests.ps1 -TestType "java"       # Java Testcontainers only
./run-comprehensive-e2e-tests.ps1 -TestType "powershell" # PowerShell only

# Run tests in parallel (faster execution)
./run-comprehensive-e2e-tests.ps1 -TestType "all" -Parallel $true
```

### Individual Test Execution

```powershell
# Docker Compose approach
./comprehensive-e2e-test.ps1 -CleanStart $true -UseDocker $true

# Java Testcontainers approach
cd transaction-service
./mvnw test -Dtest="FullSystemIntegrationE2ETest" -Dspring.profiles.active=e2e

# PowerShell integration approach
./full-system-e2e-test.ps1 -CleanStart $true
```

## Test Scenarios

### 1. Complete User Journey
- **Account Creation**: Create accounts in Account Service
- **Deposit Transaction**: Add funds via Transaction Service
- **Balance Verification**: Verify Account Service balance updates
- **Transfer Transaction**: Move funds between accounts
- **Withdrawal Transaction**: Remove funds from account
- **Transaction History**: Verify complete audit trail
- **Final Balance Check**: Ensure data consistency

### 2. Error Handling Scenarios
- **Invalid Account**: Transaction with non-existent account
- **Insufficient Funds**: Withdrawal exceeding balance
- **Service Unavailability**: Handle Account Service downtime
- **Timeout Scenarios**: Network timeout handling
- **Invalid Transfers**: Same-account transfer prevention

### 3. Concurrent Operations
- **Parallel Transactions**: Multiple simultaneous operations
- **Race Condition Handling**: Database transaction isolation
- **Cache Consistency**: Redis cache under concurrent load
- **Balance Accuracy**: Ensure no lost updates

### 4. Data Consistency
- **ACID Properties**: Transaction atomicity and consistency
- **Cross-Service Sync**: Account and transaction data alignment
- **Audit Trail**: Complete transaction history
- **Balance Reconciliation**: Account balance accuracy

### 5. Performance Validation
- **Response Times**: Service response time baselines
- **Throughput**: Concurrent transaction processing
- **Resource Usage**: Memory and CPU utilization
- **Database Performance**: Query execution times

## Test Infrastructure

### Docker Compose Setup
```yaml
# docker-compose-full-e2e.yml
services:
  postgres-e2e:     # Shared database
  redis-e2e:        # Transaction Service cache
  account-service-e2e:   # Account Service instance
  transaction-service-e2e: # Transaction Service instance
```

### Java Testcontainers Setup
```java
@Container
static PostgreSQLContainer<?> postgres = ...;

@Container
static GenericContainer<?> redis = ...;

@Container
static GenericContainer<?> accountService = ...;
```

### PowerShell Integration
- Direct HTTP API calls
- Service health monitoring
- End-to-end workflow validation
- Performance measurement

## Test Reports

The comprehensive test suite generates detailed reports:

### Markdown Report
- Executive summary with success rates
- Detailed test results by category
- Performance metrics
- Recommendations for production readiness

### JSON Summary
- Programmatic test results
- Integration with CI/CD pipelines
- Automated decision making

### Example Report Structure
```markdown
# Comprehensive Full System E2E Test Report

## Executive Summary
- Total Tests: 15
- Passed: 14
- Failed: 1
- Success Rate: 93.33%

## Test Suite Results
### Docker Compose Integration Tests
- Executed: true
- Success: true
- Duration: 45000ms

### Java Testcontainers Integration Tests
- Executed: true
- Success: true
- Duration: 32000ms

### PowerShell Integration Tests
- Executed: true
- Success: false
- Duration: 28000ms
```

## CI/CD Integration

### GitHub Actions Example
```yaml
name: Comprehensive E2E Tests
on: [push, pull_request]

jobs:
  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run Comprehensive E2E Tests
        run: |
          ./run-comprehensive-e2e-tests.ps1 -TestType "all" -GenerateReport $true
      - name: Upload Test Reports
        uses: actions/upload-artifact@v3
        with:
          name: e2e-test-reports
          path: comprehensive-test-results/
```

### Jenkins Pipeline Example
```groovy
pipeline {
    agent any
    stages {
        stage('E2E Tests') {
            steps {
                script {
                    powershell './run-comprehensive-e2e-tests.ps1 -TestType "all"'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'comprehensive-test-results/**/*'
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'comprehensive-test-results',
                        reportFiles: '*.md',
                        reportName: 'E2E Test Report'
                    ])
                }
            }
        }
    }
}
```

## Troubleshooting

### Common Issues

1. **Docker Daemon Not Running**
   ```bash
   # Start Docker daemon
   sudo systemctl start docker
   ```

2. **Port Conflicts**
   ```bash
   # Check port usage
   netstat -tulpn | grep :8080
   # Kill conflicting processes
   sudo kill -9 <PID>
   ```

3. **Container Build Failures**
   ```bash
   # Clean Docker cache
   docker system prune -a
   # Rebuild images
   docker-compose -f docker-compose-full-e2e.yml build --no-cache
   ```

4. **Test Timeouts**
   ```powershell
   # Increase timeout values
   ./run-comprehensive-e2e-tests.ps1 -ServiceTimeout 300
   ```

### Debug Mode

Enable debug logging for detailed troubleshooting:

```powershell
# Enable debug logging
$env:SPRING_PROFILES_ACTIVE = "e2e,debug"
./run-comprehensive-e2e-tests.ps1 -TestType "java"
```

### Manual Verification

If tests fail, manually verify services:

```bash
# Check service health
curl http://localhost:8081/actuator/health  # Account Service
curl http://localhost:8080/actuator/health  # Transaction Service

# Check database connectivity
docker exec -it postgres-full-e2e psql -U testuser -d fullsystem_test -c "SELECT 1;"

# Check Redis connectivity
docker exec -it redis-full-e2e redis-cli ping
```

## Best Practices

### Test Development
1. **Isolation**: Each test should be independent
2. **Cleanup**: Always clean up test data
3. **Deterministic**: Tests should produce consistent results
4. **Fast Feedback**: Optimize for quick execution

### Production Readiness
1. **Success Rate**: Aim for 95%+ success rate
2. **Performance**: Monitor response times
3. **Resilience**: Test failure scenarios
4. **Monitoring**: Set up production monitoring based on test scenarios

### Maintenance
1. **Regular Updates**: Keep tests current with code changes
2. **Performance Monitoring**: Track test execution times
3. **Infrastructure Updates**: Keep Docker images and dependencies updated
4. **Documentation**: Maintain test documentation

## Contributing

When adding new E2E tests:

1. **Follow Patterns**: Use existing test structure
2. **Add Documentation**: Update this README
3. **Test Coverage**: Ensure new features are covered
4. **Performance**: Consider test execution time
5. **Cleanup**: Ensure proper resource cleanup

## Support

For issues with the E2E test suite:

1. Check the troubleshooting section
2. Review test logs and reports
3. Verify prerequisites are met
4. Check Docker and service logs
5. Create an issue with detailed error information

---

*This comprehensive E2E testing suite ensures the Account Service and Transaction Service integration is production-ready and maintains high quality standards.*