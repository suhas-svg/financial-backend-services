# Infrastructure Testing Guide

This guide explains how to solve the database connectivity issues and run infrastructure validation tests with the proper E2E environment.

## Problem

The infrastructure validation tests were failing because they were trying to connect to databases that don't exist:

```
Database connectivity failed after 5 attempts: database "account_db" does not exist
```

## Solution

We've created a comprehensive E2E infrastructure setup that provides:

1. **E2E Docker Compose Environment** - Complete infrastructure stack
2. **E2E Configuration** - Proper database names, ports, and credentials
3. **Infrastructure Setup Scripts** - Automated infrastructure management
4. **Test Runner Scripts** - Integrated test execution with infrastructure

## Quick Start

### Option 1: Run Unit Tests (No Infrastructure Required)

```bash
# Run unit tests that don't require actual infrastructure
npm run test:e2e:unit
```

### Option 2: Run Full Infrastructure Tests

```bash
# Install dependencies
npm install

# Run the complete infrastructure test suite
.\run-infrastructure-tests.ps1

# Or with cleanup after tests
.\run-infrastructure-tests.ps1 -CleanupAfter
```

### Option 3: Manual Infrastructure Management

```bash
# Start E2E infrastructure
npm run e2e:start

# Wait for services to be ready (about 2-3 minutes)
npm run e2e:validate

# Run infrastructure tests
npm run test:e2e

# Stop infrastructure when done
npm run e2e:stop
```

## Infrastructure Components

The E2E environment provides:

| Component | Local Port | Docker Service | Database/Credentials |
|-----------|------------|----------------|---------------------|
| Account Service | 8083 | account-service-e2e | - |
| Transaction Service | 8082 | transaction-service-e2e | - |
| Account Database | 5434 | postgres-account-e2e | account_db_e2e / e2e_user:e2e_password |
| Transaction Database | 5435 | postgres-transaction-e2e | transaction_db_e2e / e2e_user:e2e_password |
| Redis Cache | 6380 | redis-e2e | Password: e2e_redis_password |

## Configuration Files

### `.env.e2e` - E2E Environment Configuration
Contains the correct database names, ports, and credentials that match the docker-compose setup.

### `docker-compose-e2e.yml` - E2E Infrastructure
Complete Docker Compose setup with all required services, databases, and networking.

## Available Scripts

### NPM Scripts

```bash
# Test scripts
npm run test:e2e              # Run E2E infrastructure tests
npm run test:e2e:unit         # Run unit tests only
npm run test:infrastructure   # Run specific infrastructure stack test

# Infrastructure management
npm run e2e:start            # Start E2E infrastructure
npm run e2e:stop             # Stop E2E infrastructure
npm run e2e:status           # Show infrastructure status
npm run e2e:validate         # Validate infrastructure health
npm run e2e:clean            # Clean up infrastructure
```

### PowerShell Scripts

```powershell
# Complete test runner
.\run-infrastructure-tests.ps1                    # Setup, test, keep running
.\run-infrastructure-tests.ps1 -CleanupAfter      # Setup, test, cleanup
.\run-infrastructure-tests.ps1 -SetupOnly         # Just setup
.\run-infrastructure-tests.ps1 -TestOnly          # Just test
.\run-infrastructure-tests.ps1 -UnitTestsOnly     # Unit tests only

# Infrastructure management
.\scripts\setup-e2e-infrastructure.ps1 -Start     # Start infrastructure
.\scripts\setup-e2e-infrastructure.ps1 -Stop      # Stop infrastructure
.\scripts\setup-e2e-infrastructure.ps1 -Validate  # Validate infrastructure
```

## Test Types

### 1. Unit Tests (`infrastructure-validation-unit.test.ts`)
- Test validator classes without requiring actual infrastructure
- Validate configuration and error handling
- Fast execution, no external dependencies

### 2. Integration Tests (`infrastructure-validation.test.ts`)
- Test actual database connectivity
- Test Redis cache functionality
- Test service health endpoints
- Requires running E2E infrastructure

## Troubleshooting

### Common Issues

1. **Docker not running**
   ```
   Error: Docker is not running
   ```
   **Solution**: Start Docker Desktop

2. **Ports already in use**
   ```
   Error: Port 5434 is already in use
   ```
   **Solution**: Stop conflicting services or change ports in docker-compose-e2e.yml

3. **Services not ready**
   ```
   Error: Service health check failed
   ```
   **Solution**: Wait longer for services to start, or check logs with `npm run e2e:status`

4. **Database connection refused**
   ```
   Error: connection refused
   ```
   **Solution**: Ensure E2E infrastructure is running with `npm run e2e:start`

### Debugging

```bash
# Check infrastructure status
npm run e2e:status

# View logs for all services
npm run e2e:logs

# View logs for specific service
docker-compose -f docker-compose-e2e.yml -p e2e-financial-services logs account-service-e2e

# Validate infrastructure health
npm run e2e:validate

# Test database connectivity manually
docker-compose -f docker-compose-e2e.yml -p e2e-financial-services exec postgres-account-e2e psql -U e2e_user -d account_db_e2e -c "SELECT 1;"
```

## Development Workflow

### For Infrastructure Development

1. **Start infrastructure**:
   ```bash
   npm run e2e:start
   ```

2. **Develop and test**:
   ```bash
   npm run test:e2e:unit  # Quick unit tests
   npm run test:e2e       # Full integration tests
   ```

3. **Debug issues**:
   ```bash
   npm run e2e:validate   # Check infrastructure health
   npm run e2e:status     # Check service status
   ```

4. **Clean up**:
   ```bash
   npm run e2e:stop       # Stop services
   npm run e2e:clean      # Full cleanup
   ```

### For CI/CD Integration

```bash
# In CI pipeline
npm install
npm run e2e:start
npm run e2e:validate
npm run test:e2e
npm run e2e:clean
```

## Next Steps

After the infrastructure validation tests pass, you can:

1. **Add more test suites** using the same E2E infrastructure
2. **Extend the infrastructure** by adding more services to docker-compose-e2e.yml
3. **Create test data** using the data setup scripts
4. **Add performance tests** using the validated infrastructure

The infrastructure validation provides a solid foundation for comprehensive E2E testing of the financial services system.