# Infrastructure Validation Test Suite

This document describes the infrastructure validation test suite that validates database connectivity, Redis cache functionality, and service health checks for the comprehensive E2E testing framework.

## Overview

The infrastructure validation test suite provides comprehensive validation of:

1. **Database Connectivity Validation** (Requirement 1.1)
   - PostgreSQL connection validation for both account and transaction databases
   - Database schema validation to ensure migrations are applied correctly
   - Database health checks with retry mechanisms

2. **Redis Cache Validation** (Requirement 1.2)
   - Redis connectivity validation with connection pooling tests
   - Cache functionality tests (set, get, expire operations)
   - Redis health checks with proper error handling

3. **Service Health Check Validation** (Requirement 1.4)
   - Service health endpoint validation for both Account and Transaction services
   - Startup timeout handling with configurable wait times
   - Service readiness validation with dependency checking

## Components

### Database Validator (`src/utils/database-validator.ts`)

Provides PostgreSQL database validation functionality:

- **DatabaseValidator**: Main class for database connectivity and schema validation
- **DatabaseValidatorFactory**: Factory class for creating database validators

Key methods:
- `validateConnectivity()`: Tests database connection with retry mechanism
- `validateSchema()`: Validates database schema and table existence
- `healthCheck()`: Comprehensive database health check

### Redis Validator (`src/utils/redis-validator.ts`)

Provides Redis cache validation functionality:

- **RedisValidator**: Main class for Redis connectivity and functionality validation
- **RedisValidatorFactory**: Factory class for creating Redis validators

Key methods:
- `validateConnectivity()`: Tests Redis connection with retry mechanism
- `validateCacheFunctionality()`: Tests cache operations (SET, GET, EXPIRE, DELETE)
- `validateConnectionPooling()`: Tests connection pooling behavior
- `analyzeCachePerformance()`: Analyzes cache hit/miss ratios and performance

### Service Validator (`src/utils/service-validator.ts`)

Provides service health check validation functionality:

- **ServiceValidator**: Main class for service health validation
- **ServiceValidatorFactory**: Factory class for creating service validators

Key methods:
- `validateHealthEndpoint()`: Tests service health endpoints with retry
- `waitForStartup()`: Waits for service startup with configurable timeout
- `validateReadiness()`: Validates service readiness with dependency checking
- `comprehensiveHealthCheck()`: Performs comprehensive health validation

## Test Files

### Main Infrastructure Tests (`src/tests/infrastructure-validation.test.ts`)

Comprehensive integration tests that validate the entire infrastructure stack:

- Database connectivity and schema validation for both databases
- Redis cache functionality and performance testing
- Service health checks and readiness validation
- Complete infrastructure stack validation

### Unit Tests (`src/tests/infrastructure-validation-unit.test.ts`)

Unit tests that validate the infrastructure validation classes without requiring actual infrastructure:

- Configuration validation
- Validator class instantiation
- Error handling and logging
- Timeout and retry configuration

## Configuration

The infrastructure validation uses the main test configuration (`src/config/test-config.ts`) with the following key settings:

### Database Configuration
```typescript
databases: {
  accountDb: {
    host: 'localhost',
    port: 5432,
    database: 'account_db',
    username: 'postgres',
    password: 'postgres',
    connectionTimeout: 10000
  },
  transactionDb: {
    host: 'localhost',
    port: 5433,
    database: 'transaction_db',
    username: 'postgres',
    password: 'postgres',
    connectionTimeout: 10000
  }
}
```

### Redis Configuration
```typescript
cache: {
  redis: {
    host: 'localhost',
    port: 6379,
    password: undefined,
    connectionTimeout: 5000
  }
}
```

### Service Configuration
```typescript
services: {
  accountService: {
    url: 'http://localhost:8081',
    healthEndpoint: '/actuator/health',
    startupTimeout: 180000,
    requestTimeout: 30000
  },
  transactionService: {
    url: 'http://localhost:8080',
    healthEndpoint: '/actuator/health',
    startupTimeout: 180000,
    requestTimeout: 30000
  }
}
```

## Usage

### Running Infrastructure Validation Tests

1. **Run all infrastructure tests:**
   ```bash
   npm test -- --testPathPattern="infrastructure-validation"
   ```

2. **Run unit tests only:**
   ```bash
   npm test -- --testPathPattern="infrastructure-validation-unit"
   ```

3. **Run specific test suites:**
   ```bash
   # Database tests only
   npm test -- --testNamePattern="Database Connectivity Validation"
   
   # Redis tests only
   npm test -- --testNamePattern="Redis Cache Validation"
   
   # Service tests only
   npm test -- --testNamePattern="Service Health Check Validation"
   ```

### Using Validators Programmatically

```typescript
import { DatabaseValidatorFactory } from './src/utils/database-validator';
import { RedisValidatorFactory } from './src/utils/redis-validator';
import { ServiceValidatorFactory } from './src/utils/service-validator';
import { testConfig } from './src/config/test-config';

const config = testConfig.getConfig();

// Database validation
const accountDbValidator = DatabaseValidatorFactory.createAccountDbValidator(config.databases.accountDb);
const isConnected = await accountDbValidator.validateConnectivity();

// Redis validation
const redisValidator = RedisValidatorFactory.createValidator(config.cache.redis);
const isFunctional = await redisValidator.validateCacheFunctionality();

// Service validation
const accountServiceValidator = ServiceValidatorFactory.createAccountServiceValidator(config.services.accountService);
const isHealthy = await accountServiceValidator.validateHealthEndpoint();
```

## Error Handling

All validators implement comprehensive error handling:

- **Retry Mechanisms**: Configurable retry attempts with exponential backoff
- **Timeout Handling**: Configurable timeouts for all operations
- **Graceful Degradation**: Proper error messages and logging
- **Resource Cleanup**: Automatic cleanup of connections and resources

## Logging

The infrastructure validation provides detailed logging:

- **Infrastructure Status**: UP/DOWN status for each component
- **Test Lifecycle**: Test start/completion with timing
- **Performance Metrics**: Response times and throughput measurements
- **Error Details**: Comprehensive error information for debugging

## Dependencies

The infrastructure validation requires the following npm packages:

```json
{
  "dependencies": {
    "pg": "^8.11.3",
    "redis": "^4.6.10"
  },
  "devDependencies": {
    "@types/pg": "^8.10.7"
  }
}
```

## Expected Infrastructure

For the tests to pass, the following infrastructure must be available:

1. **PostgreSQL Databases:**
   - Account database on localhost:5432 with database name 'account_db'
   - Transaction database on localhost:5433 with database name 'transaction_db'
   - Both with username/password: postgres/postgres

2. **Redis Cache:**
   - Redis server on localhost:6379
   - No authentication required (or configure password in .env)

3. **Services:**
   - Account Service running on http://localhost:8081
   - Transaction Service running on http://localhost:8080
   - Both with health endpoints at /actuator/health

## Troubleshooting

### Common Issues

1. **Database Connection Errors:**
   - Ensure PostgreSQL is running on the correct ports
   - Verify database names exist
   - Check username/password credentials

2. **Redis Connection Errors:**
   - Ensure Redis server is running on port 6379
   - Check if authentication is required

3. **Service Health Check Failures:**
   - Ensure services are running on correct ports
   - Verify health endpoints are accessible
   - Check service startup time (may need to increase timeout)

### Debug Mode

Enable debug logging by setting the LOG_LEVEL environment variable:

```bash
LOG_LEVEL=debug npm test
```

This will provide detailed information about connection attempts, retry mechanisms, and error details.