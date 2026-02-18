# MCP Financial Server - Testing Guide

This document provides comprehensive information about the testing strategy, test suites, and how to run tests for the MCP Financial Server.

## Testing Strategy

The MCP Financial Server follows a comprehensive testing strategy with multiple layers:

### 1. Unit Tests (`tests/unit/`)
- **Purpose**: Test individual components in isolation
- **Coverage**: All MCP tools, authentication, validation, error handling
- **Mocking**: External dependencies are mocked
- **Speed**: Fast execution (< 1 second per test)

### 2. Integration Tests (`tests/integration/`)
- **Purpose**: Test component interactions and service integrations
- **Coverage**: HTTP clients, circuit breakers, service communication
- **Mocking**: Minimal mocking, real component interactions
- **Speed**: Medium execution (1-5 seconds per test)

### 3. End-to-End Tests (`tests/e2e/`)
- **Purpose**: Test complete workflows and MCP protocol compliance
- **Coverage**: Full user scenarios, protocol compliance
- **Mocking**: External services mocked, internal components real
- **Speed**: Slower execution (5-30 seconds per test)

### 4. Performance Tests (`tests/performance/`)
- **Purpose**: Test performance, load handling, and resource usage
- **Coverage**: Throughput, latency, memory usage, concurrent operations
- **Mocking**: Optimized mocks for performance measurement
- **Speed**: Variable (10 seconds to 5 minutes)

## Test Structure

```
tests/
├── unit/                    # Unit tests
│   ├── test_account_tools.py
│   ├── test_transaction_tools.py
│   ├── test_query_tools.py
│   ├── test_auth_middleware.py
│   ├── test_jwt_handler.py
│   ├── test_permissions.py
│   ├── test_error_handling.py
│   ├── test_validation.py
│   ├── test_monitoring.py
│   └── test_server.py
├── integration/             # Integration tests
│   ├── test_service_clients.py
│   ├── test_circuit_breaker.py
│   ├── test_jwt_compatibility.py
│   ├── test_error_scenarios.py
│   ├── test_monitoring_tools.py
│   └── test_end_to_end_scenarios.py
├── e2e/                     # End-to-end tests
│   └── test_mcp_protocol_compliance.py
├── performance/             # Performance tests
│   └── test_load_testing.py
├── coverage/                # Coverage reports
├── reports/                 # Test execution reports
├── conftest.py             # Shared test fixtures
└── test_runner.py          # Comprehensive test runner
```

## Running Tests

### Prerequisites

1. **Install Dependencies**:
   ```bash
   pip install -e ".[dev]"
   ```

2. **Environment Setup**:
   ```bash
   # Copy environment file
   cp .env.example .env
   
   # Set test environment variables
   export ENVIRONMENT=test
   export JWT_SECRET=test-secret-key
   ```

### Quick Test Execution

```bash
# Run all tests
python run_tests.py

# Run specific test suite
python run_tests.py unit
python run_tests.py integration
python run_tests.py e2e
python run_tests.py performance

# Run with verbose output
python run_tests.py --verbose

# Run linting only
python run_tests.py lint

# Generate coverage report
python run_tests.py coverage
```

### Advanced Test Execution

```bash
# Run tests with pytest directly
pytest tests/unit/ -v
pytest tests/integration/ -v --tb=short
pytest tests/e2e/ -v -s

# Run specific test file
pytest tests/unit/test_account_tools.py -v

# Run specific test method
pytest tests/unit/test_account_tools.py::TestAccountTools::test_create_account_success -v

# Run tests with coverage
pytest tests/unit/ --cov=mcp_financial --cov-report=html

# Run tests in parallel
pytest tests/unit/ -n auto

# Run tests with timeout
pytest tests/unit/ --timeout=300
```

### Test Markers

Tests are organized using pytest markers:

```bash
# Run only unit tests
pytest -m unit

# Run only integration tests
pytest -m integration

# Run only e2e tests
pytest -m e2e

# Run authentication tests
pytest -m auth

# Run performance tests
pytest -m performance

# Run slow tests
pytest -m slow

# Exclude slow tests
pytest -m "not slow"
```

## Test Configuration

### pytest.ini
The `pytest.ini` file contains test configuration:
- Test discovery patterns
- Coverage settings
- Timeout configuration
- Logging configuration
- Parallel execution settings

### conftest.py
Shared test fixtures and configuration:
- Mock JWT tokens
- Sample test data
- HTTP client fixtures
- Authentication contexts
- Error simulation fixtures

## Test Data and Fixtures

### Authentication Fixtures
```python
@pytest.fixture
def customer_token():
    return "Bearer customer.jwt.token"

@pytest.fixture
def admin_token():
    return "Bearer admin.jwt.token"

@pytest.fixture
def expired_token():
    return "Bearer expired.jwt.token"
```

### Sample Data Fixtures
```python
@pytest.fixture
def sample_account():
    return {
        "id": "acc_test_123",
        "ownerId": "user_456",
        "accountType": "CHECKING",
        "balance": 1000.00
    }

@pytest.fixture
def sample_transaction():
    return {
        "id": "txn_test_789",
        "accountId": "acc_test_123",
        "amount": 100.00,
        "transactionType": "DEPOSIT"
    }
```

## Coverage Requirements

### Minimum Coverage Targets
- **Overall Coverage**: 80%
- **Unit Tests**: 90%
- **Integration Tests**: 70%
- **Critical Components**: 95%

### Coverage Reports
Coverage reports are generated in multiple formats:
- **Terminal**: Real-time coverage during test execution
- **HTML**: Detailed interactive report (`tests/coverage/html/index.html`)
- **XML**: Machine-readable format for CI/CD (`tests/coverage/coverage.xml`)

### Viewing Coverage Reports
```bash
# Generate and view HTML coverage report
python run_tests.py coverage
open tests/coverage/html/index.html

# View coverage in terminal
pytest tests/unit/ --cov=mcp_financial --cov-report=term-missing
```

## Performance Testing

### Performance Metrics
- **Latency**: Response time for individual operations
- **Throughput**: Requests per second under load
- **Memory Usage**: Memory consumption under various loads
- **Concurrent Operations**: Behavior under concurrent requests
- **Circuit Breaker**: Performance impact of circuit breaker operations

### Performance Test Scenarios
1. **Single Request Latency**: Measure individual operation response times
2. **Concurrent Throughput**: Test performance under concurrent load
3. **Sustained Load**: Test behavior under sustained high load
4. **Spike Load**: Test response to sudden load increases
5. **Resource Exhaustion**: Test behavior when resources are limited

### Running Performance Tests
```bash
# Run all performance tests
python run_tests.py performance

# Run specific performance test
pytest tests/performance/test_load_testing.py::TestPerformanceMetrics::test_concurrent_request_throughput -v -s

# Run performance tests with profiling
pytest tests/performance/ --profile
```

## Continuous Integration

### GitHub Actions Integration
The test suite integrates with GitHub Actions for automated testing:

```yaml
name: Test Suite
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.11'
      - name: Install dependencies
        run: pip install -e ".[dev]"
      - name: Run tests
        run: python run_tests.py
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

### Pre-commit Hooks
Set up pre-commit hooks for automatic testing:

```bash
# Install pre-commit
pip install pre-commit

# Set up hooks
pre-commit install

# Run hooks manually
pre-commit run --all-files
```

## Test Development Guidelines

### Writing Unit Tests
1. **Test Isolation**: Each test should be independent
2. **Mock External Dependencies**: Use mocks for HTTP clients, databases
3. **Test Edge Cases**: Include error conditions and boundary cases
4. **Clear Test Names**: Use descriptive test method names
5. **Arrange-Act-Assert**: Follow the AAA pattern

### Example Unit Test
```python
@pytest.mark.asyncio
async def test_create_account_success(self, account_tools, mock_auth_handler, mock_account_client):
    """Test successful account creation."""
    # Arrange
    mock_auth_handler.extract_user_context.return_value = mock_user_context
    mock_account_client.create_account.return_value = expected_account_data
    
    # Act
    result = await account_tools.create_account("user_123", "CHECKING", 1000.0, "token")
    
    # Assert
    assert len(result) == 1
    response_data = json.loads(result[0].text)
    assert response_data["success"] is True
    assert response_data["data"]["id"] == "acc_test_123"
```

### Writing Integration Tests
1. **Real Component Interactions**: Minimize mocking between internal components
2. **Service Integration**: Test actual HTTP client behavior
3. **Error Scenarios**: Test network failures, timeouts, service unavailability
4. **Authentication Flow**: Test end-to-end authentication

### Writing E2E Tests
1. **Complete Workflows**: Test full user scenarios
2. **Protocol Compliance**: Verify MCP protocol adherence
3. **Cross-Component**: Test interactions across all components
4. **Real Data Flow**: Use realistic data structures

### Writing Performance Tests
1. **Measurable Metrics**: Define clear performance criteria
2. **Baseline Comparisons**: Compare against performance baselines
3. **Resource Monitoring**: Monitor CPU, memory, network usage
4. **Load Patterns**: Test various load patterns (steady, spike, burst)

## Troubleshooting Tests

### Common Issues

1. **Import Errors**:
   ```bash
   # Ensure src directory is in Python path
   export PYTHONPATH="${PYTHONPATH}:$(pwd)/src"
   ```

2. **Async Test Issues**:
   ```python
   # Use pytest-asyncio for async tests
   @pytest.mark.asyncio
   async def test_async_function():
       result = await async_function()
       assert result is not None
   ```

3. **Mock Issues**:
   ```python
   # Use AsyncMock for async functions
   mock_client = AsyncMock()
   mock_client.get_account.return_value = expected_data
   ```

4. **Fixture Scope Issues**:
   ```python
   # Use appropriate fixture scope
   @pytest.fixture(scope="session")  # For expensive setup
   @pytest.fixture(scope="function")  # For test isolation
   ```

### Debug Mode
```bash
# Run tests with debug output
pytest tests/unit/ -v -s --tb=long

# Run single test with debugging
pytest tests/unit/test_account_tools.py::test_create_account_success -v -s --pdb
```

### Test Logging
```bash
# Enable test logging
pytest tests/unit/ --log-cli-level=DEBUG

# Capture logs in test output
pytest tests/unit/ --capture=no
```

## Test Metrics and Reporting

### Test Execution Reports
Test execution generates comprehensive reports:
- **JSON Reports**: Machine-readable test results
- **HTML Reports**: Human-readable test summaries
- **Coverage Reports**: Code coverage analysis
- **Performance Reports**: Performance metrics and benchmarks

### Viewing Reports
```bash
# View latest test report
cat tests/reports/test_report_latest.json

# View coverage report
open tests/coverage/html/index.html

# View performance metrics
cat tests/reports/performance_report_latest.json
```

## Best Practices

### Test Organization
1. **Group Related Tests**: Use test classes to group related functionality
2. **Descriptive Names**: Use clear, descriptive test and fixture names
3. **Documentation**: Add docstrings to complex tests
4. **Consistent Structure**: Follow consistent test structure across files

### Test Maintenance
1. **Regular Updates**: Keep tests updated with code changes
2. **Flaky Test Management**: Identify and fix flaky tests promptly
3. **Performance Monitoring**: Monitor test execution times
4. **Coverage Monitoring**: Maintain coverage levels over time

### Test Data Management
1. **Realistic Data**: Use realistic test data structures
2. **Data Isolation**: Ensure test data doesn't interfere between tests
3. **Data Cleanup**: Clean up test data after test execution
4. **Sensitive Data**: Never use real sensitive data in tests

## Contributing to Tests

### Adding New Tests
1. **Identify Test Type**: Determine appropriate test category (unit/integration/e2e)
2. **Follow Conventions**: Use existing patterns and naming conventions
3. **Add Markers**: Tag tests with appropriate pytest markers
4. **Update Documentation**: Update this guide if adding new test patterns

### Test Review Checklist
- [ ] Tests are properly categorized and marked
- [ ] Tests follow naming conventions
- [ ] Tests are isolated and don't depend on external state
- [ ] Tests include both positive and negative scenarios
- [ ] Tests have appropriate assertions
- [ ] Tests are documented with clear docstrings
- [ ] Tests maintain or improve coverage
- [ ] Performance tests include baseline comparisons

## Resources

### Documentation
- [pytest Documentation](https://docs.pytest.org/)
- [pytest-asyncio Documentation](https://pytest-asyncio.readthedocs.io/)
- [pytest-cov Documentation](https://pytest-cov.readthedocs.io/)

### Tools
- **pytest**: Test framework
- **pytest-asyncio**: Async test support
- **pytest-cov**: Coverage reporting
- **pytest-xdist**: Parallel test execution
- **pytest-mock**: Enhanced mocking
- **httpx**: HTTP client testing
- **respx**: HTTP request mocking