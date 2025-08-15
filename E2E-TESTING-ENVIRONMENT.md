# E2E Testing Environment

This document describes the comprehensive End-to-End (E2E) testing environment for the financial backend application consisting of Account Service and Transaction Service.

## Overview

The E2E testing environment provides a complete, isolated testing setup that includes:

- **PostgreSQL databases** for both Account and Transaction services
- **Redis cache** for session management and caching
- **Account Service** with full API endpoints
- **Transaction Service** with full API endpoints
- **Service orchestration** with proper dependency management
- **Health checks** and startup validation
- **Test data management** and cleanup utilities
- **Environment validation** and monitoring

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    E2E Testing Environment                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐    ┌─────────────────┐                    │
│  │ Account Service │    │Transaction Svc  │                    │
│  │   Port: 8081    │    │   Port: 8080    │                    │
│  └─────────┬───────┘    └─────────┬───────┘                    │
│            │                      │                            │
│  ┌─────────▼───────┐    ┌─────────▼───────┐    ┌─────────────┐ │
│  │ PostgreSQL      │    │ PostgreSQL      │    │   Redis     │ │
│  │ Account DB      │    │ Transaction DB  │    │   Cache     │ │
│  │ Port: 5432      │    │ Port: 5433      │    │ Port: 6379  │ │
│  └─────────────────┘    └─────────────────┘    └─────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Docker and Docker Compose installed
- Ports 5432, 5433, 6379, 8080, 8081 available
- At least 4GB RAM available for containers

### Starting the Environment

#### Using PowerShell (Windows)
```powershell
# Start the complete E2E environment
.\e2e-environment.ps1 start

# Check status
.\e2e-environment.ps1 status

# View logs
.\e2e-environment.ps1 logs
```

#### Using Bash (Linux/macOS)
```bash
# Make script executable
chmod +x e2e-environment.sh

# Start the complete E2E environment
./e2e-environment.sh start

# Check status
./e2e-environment.sh status

# View logs
./e2e-environment.sh logs
```

#### Using Docker Compose directly
```bash
# Start all services
docker compose -f docker-compose-e2e.yml up -d

# Check service health
docker compose -f docker-compose-e2e.yml ps

# View logs
docker compose -f docker-compose-e2e.yml logs -f
```

## Service Configuration

### Account Service
- **URL**: http://localhost:8083
- **Health Check**: http://localhost:8083/actuator/health
- **Database**: PostgreSQL on port 5434
- **Database Name**: account_db_e2e
- **Metrics**: http://localhost:8083/actuator/metrics

### Transaction Service
- **URL**: http://localhost:8082
- **Health Check**: http://localhost:8082/actuator/health
- **Database**: PostgreSQL on port 5435
- **Database Name**: transaction_db_e2e
- **Cache**: Redis on port 6380
- **Metrics**: http://localhost:8082/actuator/metrics

### Infrastructure Services

#### PostgreSQL Databases
- **Account Database**: localhost:5434
  - Database: account_db_e2e
  - Username: e2e_user
  - Password: e2e_password

- **Transaction Database**: localhost:5435
  - Database: transaction_db_e2e
  - Username: e2e_user
  - Password: e2e_password

#### Redis Cache
- **Host**: localhost:6380
- **Password**: e2e_redis_password

## Environment Management

### Available Commands

| Command | Description |
|---------|-------------|
| `start` | Start the complete E2E environment |
| `stop` | Stop all services |
| `restart` | Restart all services |
| `reset` | Full reset (cleanup + start) |
| `cleanup` | Clean up environment and resources |
| `status` | Show current status of all services |
| `logs` | Show logs for all or specific services |
| `validate` | Validate environment readiness |
| `setup-data` | Setup test data |

### Examples

```bash
# Start environment and wait for all services to be healthy
./e2e-environment.sh start

# Show detailed status of all services
./e2e-environment.sh status

# View logs for a specific service
./e2e-environment.sh logs account-service-e2e

# Validate that environment is ready for testing
./e2e-environment.sh validate

# Full cleanup including Docker volumes
./e2e-environment.sh cleanup --prune-volumes

# Reset environment (useful when switching between test runs)
./e2e-environment.sh reset
```

## Test Data Management

### Pre-loaded Test Data

The environment comes with pre-configured test data:

#### Test Users
- `e2e_user_1` / `e2e_user_2` / `e2e_user_3`
- Password: `password123` (hashed)
- Email: `e2e_user_X@test.com`

#### Test Accounts
- **ACC-E2E-001**: Checking account ($1,000) - User 1
- **ACC-E2E-002**: Savings account ($5,000) - User 1
- **ACC-E2E-003**: Checking account ($2,500) - User 2
- **ACC-E2E-004**: Savings account ($10,000) - User 2
- **ACC-E2E-005**: Checking account ($500) - User 3

#### Test Transactions
- Historical deposit and withdrawal transactions
- Transfer transactions between accounts
- Transaction limits and audit logs

### Data Setup and Cleanup

```bash
# Setup fresh test data
./e2e-environment.sh setup-data

# Clean up test data (automatically done during cleanup)
./e2e-environment.sh cleanup
```

## Health Checks and Monitoring

### Service Health Endpoints

All services provide health check endpoints:

```bash
# Check Account Service health
curl http://localhost:8083/actuator/health

# Check Transaction Service health
curl http://localhost:8082/actuator/health

# Check all services via validation
./e2e-environment.sh validate
```

### Startup Sequence

The environment follows a strict startup sequence:

1. **Infrastructure Services** (PostgreSQL databases, Redis)
2. **Account Service** (depends on Account DB)
3. **Transaction Service** (depends on Transaction DB, Redis, Account Service)
4. **Environment Validation**
5. **Test Data Setup**

### Health Check Configuration

- **Database Health Checks**: 10s interval, 5s timeout, 10 retries
- **Application Health Checks**: 15s interval, 10s timeout, 10 retries
- **Startup Period**: 90s for Account Service, 120s for Transaction Service

## Troubleshooting

### Common Issues

#### Services Not Starting
```bash
# Check Docker daemon
docker info

# Check port availability
netstat -an | grep -E "(5432|5433|6379|8080|8081)"

# View service logs
./e2e-environment.sh logs <service-name>
```

#### Database Connection Issues
```bash
# Test database connectivity
docker exec postgres-account-e2e pg_isready -U e2e_user -d account_db_e2e
docker exec postgres-transaction-e2e pg_isready -U e2e_user -d transaction_db_e2e
```

#### Service Communication Issues
```bash
# Test service-to-service communication
docker exec account-service-e2e curl -f http://transaction-service-e2e:8080/actuator/health
docker exec transaction-service-e2e curl -f http://account-service-e2e:8080/actuator/health
```

### Log Analysis

```bash
# View all logs
./e2e-environment.sh logs

# View specific service logs
./e2e-environment.sh logs account-service-e2e
./e2e-environment.sh logs transaction-service-e2e
./e2e-environment.sh logs postgres-account-e2e

# Follow logs in real-time
docker compose -f docker-compose-e2e.yml logs -f account-service-e2e
```

### Performance Monitoring

```bash
# Check container resource usage
docker stats

# Check service metrics
curl http://localhost:8083/actuator/metrics
curl http://localhost:8082/actuator/metrics

# Check database performance
docker exec postgres-account-e2e psql -U e2e_user -d account_db_e2e -c "SELECT * FROM pg_stat_activity;"
```

## Integration with Testing Frameworks

### Environment Variables for Tests

When running E2E tests, use these environment variables:

```bash
export ACCOUNT_SERVICE_URL=http://localhost:8083
export TRANSACTION_SERVICE_URL=http://localhost:8082
export POSTGRES_ACCOUNT_URL=jdbc:postgresql://localhost:5434/account_db_e2e
export POSTGRES_TRANSACTION_URL=jdbc:postgresql://localhost:5435/transaction_db_e2e
export REDIS_URL=redis://localhost:6380
export E2E_USERNAME=e2e_user
export E2E_PASSWORD=e2e_password
export REDIS_PASSWORD=e2e_redis_password
```

### Test Execution Flow

1. **Pre-Test Setup**
   ```bash
   ./e2e-environment.sh start
   ./e2e-environment.sh validate
   ```

2. **Test Execution**
   - Run your E2E test suite
   - Tests should use the provided service URLs
   - Tests can assume pre-loaded test data is available

3. **Post-Test Cleanup**
   ```bash
   ./e2e-environment.sh cleanup
   ```

## Security Considerations

### Test Environment Security

- **Isolated Network**: All services run in isolated Docker network
- **Test Credentials**: Uses dedicated test credentials (not production)
- **No External Access**: Services only accessible via localhost
- **Temporary Data**: All data is ephemeral and cleaned up after testing

### JWT Configuration

- **Test Secret**: Uses dedicated JWT secret for E2E testing
- **Token Expiration**: 1 hour expiration for test tokens
- **Consistent Configuration**: Same JWT secret across both services

## Maintenance

### Regular Maintenance Tasks

```bash
# Clean up old containers and volumes
./e2e-environment.sh cleanup --prune-volumes

# Update service images
docker compose -f docker-compose-e2e.yml pull

# Rebuild services after code changes
docker compose -f docker-compose-e2e.yml build --no-cache
```

### Environment Reset

```bash
# Complete environment reset
./e2e-environment.sh reset

# This will:
# 1. Stop all services
# 2. Remove containers and volumes
# 3. Start fresh environment
# 4. Load test data
# 5. Validate readiness
```

## Support

For issues with the E2E testing environment:

1. Check the troubleshooting section above
2. Review service logs using `./e2e-environment.sh logs`
3. Validate environment health using `./e2e-environment.sh validate`
4. Try a full reset using `./e2e-environment.sh reset`

The E2E testing environment is designed to be self-contained and reproducible, providing a reliable foundation for comprehensive testing of the financial backend services.