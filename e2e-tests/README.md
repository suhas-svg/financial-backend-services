# Comprehensive E2E Testing Framework

A comprehensive End-to-End testing framework for the financial backend services (Account Service and Transaction Service). This framework provides structured testing capabilities for infrastructure validation, individual service testing, integration testing, and complete workflow validation.

## Features

- **TypeScript-based**: Full TypeScript support with comprehensive type definitions
- **Jest Testing Framework**: Leverages Jest with custom matchers for API testing
- **Modular Architecture**: Organized test suites for different testing concerns
- **Configuration Management**: Centralized configuration with environment variable support
- **HTTP Client Utilities**: Specialized HTTP client with timing and error handling
- **Custom Jest Matchers**: Domain-specific matchers for API and financial data validation
- **Comprehensive Logging**: Structured logging with Winston for test execution tracking
- **Performance Monitoring**: Built-in performance metrics collection and analysis
- **Test Data Management**: Utilities for test data generation and cleanup

## Project Structure

```
e2e-tests/
├── src/
│   ├── config/
│   │   └── test-config.ts          # Centralized configuration management
│   ├── core/
│   │   └── test-runner.ts          # Main test execution engine
│   ├── setup/
│   │   ├── jest-setup.ts           # Jest configuration and custom matchers
│   │   ├── global-setup.ts         # Global test setup
│   │   └── global-teardown.ts      # Global test cleanup
│   ├── types/
│   │   └── index.ts                # TypeScript type definitions
│   ├── utils/
│   │   ├── http-client.ts          # HTTP client utilities
│   │   ├── logger.ts               # Logging utilities
│   │   └── test-helpers.ts         # Common test helper functions
│   ├── tests/
│   │   └── framework.test.ts       # Framework validation tests
│   └── index.ts                    # Main exports
├── reports/                        # Test reports output directory
├── .env.example                    # Environment configuration template
├── jest.config.js                  # Jest configuration
├── tsconfig.json                   # TypeScript configuration
├── package.json                    # Dependencies and scripts
└── README.md                       # This file
```

## Prerequisites

- Node.js 18+ and npm
- Docker and Docker Compose (for service orchestration)
- PostgreSQL databases for Account and Transaction services
- Redis cache
- Running Account Service and Transaction Service instances

## Installation

1. **Install dependencies:**
   ```bash
   cd e2e-tests
   npm install
   ```

2. **Configure environment:**
   ```bash
   cp .env.example .env
   # Edit .env file with your specific configuration
   ```

3. **Build the project:**
   ```bash
   npm run build
   ```

## Configuration

The framework uses environment variables for configuration. Copy `.env.example` to `.env` and adjust the values:

### Service Configuration
- `ACCOUNT_SERVICE_URL`: Account Service base URL (default: http://localhost:8081)
- `TRANSACTION_SERVICE_URL`: Transaction Service base URL (default: http://localhost:8080)
- `ACCOUNT_HEALTH_ENDPOINT`: Health check endpoint (default: /actuator/health)
- `TRANSACTION_HEALTH_ENDPOINT`: Health check endpoint (default: /actuator/health)

### Database Configuration
- `ACCOUNT_DB_HOST`, `ACCOUNT_DB_PORT`, `ACCOUNT_DB_NAME`: Account database connection
- `TRANSACTION_DB_HOST`, `TRANSACTION_DB_PORT`, `TRANSACTION_DB_NAME`: Transaction database connection
- `DB_CONNECTION_TIMEOUT`: Database connection timeout in milliseconds

### Cache Configuration
- `REDIS_HOST`, `REDIS_PORT`: Redis connection details
- `REDIS_PASSWORD`: Redis password (optional)

### Performance Configuration
- `CONCURRENT_USERS`: Number of concurrent users for load testing
- `TEST_DURATION`: Load test duration in seconds
- `AVG_RESPONSE_TIME_THRESHOLD`: Average response time threshold in milliseconds
- `P95_RESPONSE_TIME_THRESHOLD`: 95th percentile response time threshold

### Test Data Configuration
- `CLEANUP_AFTER_TESTS`: Whether to clean up test data after tests (default: true)
- `TEST_USERS`: Custom test users in JSON format (optional)
- `TEST_ACCOUNTS`: Custom test accounts in JSON format (optional)

## Usage

### Running Tests

1. **Run all tests:**
   ```bash
   npm test
   ```

2. **Run tests with coverage:**
   ```bash
   npm run test:coverage
   ```

3. **Run tests in watch mode:**
   ```bash
   npm run test:watch
   ```

4. **Run tests with verbose output:**
   ```bash
   npm run test:verbose
   ```

### Framework Validation

The framework includes self-validation tests to ensure proper setup:

```bash
npm test -- --testPathPattern=framework.test.ts
```

### Custom Test Execution

```typescript
import { E2ETestRunner, testConfig } from './src';

async function runCustomTests() {
  const runner = new E2ETestRunner();
  const results = await runner.runAllTests();
  runner.generateReport(results);
}
```

## Custom Jest Matchers

The framework provides specialized Jest matchers for API testing:

```typescript
// API response validation
expect(response).toHaveValidApiResponse();
expect(response).toHaveHttpStatus(200);
expect(response).toHaveResponseTime(500);

// JWT token validation
expect(token).toHaveValidJwtToken();

// Financial data validation
expect(account).toHaveValidAccountStructure();
expect(transaction).toHaveValidTransactionStructure();

// Numeric range validation
expect(amount).toBeWithinRange(0, 1000);
```

## HTTP Client Usage

```typescript
import { createAccountServiceClient } from './src';

const client = createAccountServiceClient();

// Set authentication token
client.setAuthToken('your-jwt-token');

// Make API requests with automatic timing and error handling
const response = await client.get('/api/accounts');
expect(response).toHaveValidApiResponse();
expect(response).toHaveHttpStatus(200);
```

## Test Data Helpers

```typescript
import { createTestUser, createTestAccount, generateTestId } from './src';

// Generate unique test data
const testUser = createTestUser({
  username: generateTestId('user'),
  accounts: [
    createTestAccount({ accountType: 'CHECKING', initialBalance: 1000 })
  ]
});
```

## Logging

The framework provides structured logging for test execution:

```typescript
import { logger } from './src';

logger.testStart('my-test', 'my-suite');
logger.apiRequest('GET', '/api/accounts');
logger.apiResponse('GET', '/api/accounts', 200, 150);
logger.testComplete('my-test', 'PASSED', 1500);
```

## Configuration Management

```typescript
import { testConfig } from './src';

// Get service configuration
const services = testConfig.getServiceConfig();
console.log(services.accountService.url);

// Get database configuration
const databases = testConfig.getDatabaseConfig();
console.log(databases.accountDb.host);

// Validate configuration
testConfig.validateConfiguration();
```

## Development

### Adding New Test Suites

1. Create test files in `src/tests/` directory
2. Follow the naming convention: `*.test.ts`
3. Use the provided utilities and custom matchers
4. Update the test runner to include new suites

### Adding Custom Matchers

1. Add matcher implementation to `src/setup/jest-setup.ts`
2. Update type definitions in `src/types/index.ts`
3. Document the matcher in this README

### Extending Configuration

1. Update interfaces in `src/types/index.ts`
2. Modify `src/config/test-config.ts` to handle new configuration
3. Update `.env.example` with new environment variables

## Troubleshooting

### Common Issues

1. **Service Connection Failures**
   - Verify services are running and accessible
   - Check service URLs in configuration
   - Ensure health endpoints are responding

2. **Database Connection Issues**
   - Verify database servers are running
   - Check connection parameters in configuration
   - Ensure databases exist and are accessible

3. **Test Timeouts**
   - Increase timeout values in configuration
   - Check service performance and response times
   - Verify network connectivity

4. **Configuration Errors**
   - Run configuration validation: `testConfig.validateConfiguration()`
   - Check environment variable values
   - Verify required services are available

### Debug Mode

Enable debug logging by setting the LOG_LEVEL environment variable:

```bash
LOG_LEVEL=debug npm test
```

## Contributing

1. Follow TypeScript best practices
2. Add comprehensive tests for new features
3. Update documentation for configuration changes
4. Use the provided logging and error handling utilities
5. Ensure all tests pass before submitting changes

## License

MIT License - see LICENSE file for details.