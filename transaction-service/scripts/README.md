# Transaction Service Test Scripts

This directory contains automated scripts for testing and validating the Transaction Service deployment.

## Scripts Overview

### 1. End-to-End Test Runner (`run-e2e-tests.ps1`)

Executes the comprehensive E2E test suite including workflow tests, integration tests, and security tests.

**Usage:**
```powershell
# Run all tests with default settings
./run-e2e-tests.ps1

# Run tests with specific profile
./run-e2e-tests.ps1 -TestProfile "test"

# Skip integration tests
./run-e2e-tests.ps1 -SkipIntegration $true

# Generate detailed reports
./run-e2e-tests.ps1 -GenerateReport $true -OutputDir "test-results"
```

**Features:**
- Prerequisite checking (Maven, Docker)
- Automated test container management
- Unit, integration, E2E, and security test execution
- Test report generation with coverage metrics
- Automatic cleanup of test resources

### 2. Deployment Validation Script (`deployment-validation.ps1`)

Validates a deployed Transaction Service instance with comprehensive health and functionality checks.

**Usage:**
```powershell
# Basic validation
./deployment-validation.ps1 -BaseUrl "http://localhost:8080" -JwtToken "your-jwt-token"

# Full validation with Account Service
./deployment-validation.ps1 -BaseUrl "https://transaction-service.example.com" -AccountServiceUrl "https://account-service.example.com" -JwtToken "your-jwt-token"

# Custom timeout
./deployment-validation.ps1 -BaseUrl "http://localhost:8080" -JwtToken "your-jwt-token" -Timeout 60
```

**Validation Tests:**
- Health endpoint checks (actuator, custom, metrics, Prometheus)
- Database connectivity validation
- Redis cache connectivity validation
- Account Service integration testing
- Authentication and authorization validation
- Transaction endpoint structure validation
- Security configuration checks
- Performance baseline testing

### 3. Unix/Linux Deployment Validation (`deployment-validation.sh`)

Shell script version of the deployment validation for Unix/Linux environments.

**Usage:**
```bash
# Make executable (Linux/Mac)
chmod +x deployment-validation.sh

# Run validation
./deployment-validation.sh "http://localhost:8080" "http://localhost:8081" "your-jwt-token"
```

## Prerequisites

### For E2E Tests:
- Java 22
- Maven (wrapper included)
- Docker (for Testcontainers)
- Docker daemon running

### For Deployment Validation:
- PowerShell 7+ (for .ps1 scripts)
- Bash (for .sh scripts)
- curl (for HTTP requests)
- Access to the deployed service

## Test Categories

### Unit Tests
- Service layer business logic
- Controller request/response handling
- Repository data access operations
- Security component validation

### Integration Tests
- Database integration with Testcontainers
- Redis cache integration
- Account Service integration with WireMock
- Security integration with JWT tokens

### End-to-End Workflow Tests
- Complete user transaction journeys
- Transaction limits enforcement scenarios
- Error handling and recovery workflows
- Transaction reversal workflows
- Concurrent transaction processing
- Authentication and authorization workflows
- Data consistency and audit trail validation

### Security Tests
- JWT token validation
- Authentication endpoint protection
- Authorization role checking
- Security header validation

## Reports Generated

### Test Reports
- **Surefire Report**: HTML report with test results and failures
- **JaCoCo Coverage Report**: Code coverage metrics and analysis
- **Summary Report**: Markdown summary with key metrics

### Deployment Validation Reports
- **JSON Report**: Detailed validation results in JSON format
- **Console Output**: Real-time validation progress and results

## Example Workflows

### Development Testing
```powershell
# Run quick unit tests during development
./run-e2e-tests.ps1 -SkipIntegration $true

# Run full test suite before commit
./run-e2e-tests.ps1 -GenerateReport $true
```

### CI/CD Pipeline
```powershell
# In CI pipeline
./run-e2e-tests.ps1 -TestProfile "ci" -GenerateReport $true -OutputDir "ci-reports"

# Post-deployment validation
./deployment-validation.ps1 -BaseUrl $env:SERVICE_URL -JwtToken $env:JWT_TOKEN
```

### Production Deployment Validation
```powershell
# Validate production deployment
./deployment-validation.ps1 -BaseUrl "https://prod-transaction-service.company.com" -AccountServiceUrl "https://prod-account-service.company.com" -JwtToken $prodToken -Timeout 60
```

## Troubleshooting

### Common Issues

1. **Docker not running**: Ensure Docker daemon is started
2. **Port conflicts**: Check if required ports (5432, 6379) are available
3. **JWT token expired**: Generate a fresh JWT token for validation
4. **Network connectivity**: Ensure services are accessible from test environment

### Debug Mode

Enable debug logging by setting environment variables:
```powershell
$env:SPRING_PROFILES_ACTIVE = "test,debug"
./run-e2e-tests.ps1
```

### Manual Test Execution

Run specific test classes manually:
```bash
# Run only E2E workflow tests
./mvnw test -Dtest="TransactionWorkflowE2ETest"

# Run with debug logging
./mvnw test -Dtest="TransactionWorkflowE2ETest" -Dlogging.level.com.suhasan.finance.transaction_service=DEBUG
```

## Integration with CI/CD

These scripts are designed to integrate with CI/CD pipelines:

1. **Pre-commit hooks**: Run unit tests before commits
2. **Pull request validation**: Run full test suite on PR creation
3. **Deployment pipeline**: Run deployment validation after deployment
4. **Monitoring**: Schedule periodic deployment validation checks

## Contributing

When adding new tests:

1. Follow the existing test structure and naming conventions
2. Add appropriate test categories and annotations
3. Update this README with new test descriptions
4. Ensure tests are deterministic and can run in parallel
5. Add proper cleanup for any resources created during tests