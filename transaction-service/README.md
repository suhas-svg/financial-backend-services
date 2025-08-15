# Transaction Service

A comprehensive financial transaction processing microservice built with Spring Boot 3.5.3 and Java 22.

## Overview

The Transaction Service handles all financial transactions within the system, providing secure, auditable, and scalable transaction processing capabilities including transfers, deposits, withdrawals, and transaction history management.

## Features

- **Transaction Processing**: Secure transfer, deposit, and withdrawal operations
- **Transaction History**: Comprehensive transaction tracking and audit trails
- **Transaction Limits**: Configurable daily/monthly transaction limits
- **Balance Validation**: Real-time balance checks and updates
- **Account Integration**: Seamless integration with Account Service
- **Fraud Prevention**: Basic fraud detection and transaction validation
- **Caching**: Redis-based caching for improved performance
- **Security**: JWT-based authentication and authorization

## Technology Stack

- **Java 22** - Programming language
- **Spring Boot 3.5.3** - Application framework
- **PostgreSQL** - Primary database
- **Redis** - Caching layer
- **Maven** - Build system
- **Docker** - Containerization

## API Endpoints

### Transaction Operations
```
POST   /api/transactions/transfer        # Transfer between accounts
POST   /api/transactions/deposit         # Deposit to account
POST   /api/transactions/withdraw        # Withdraw from account
```

### Transaction Queries
```
GET    /api/transactions/{id}            # Get transaction details
GET    /api/transactions/account/{id}    # Account transaction history
GET    /api/transactions/user            # User transaction history
GET    /api/transactions/status/{status} # Get transactions by status
```

### Transaction Management
```
POST   /api/transactions/{id}/reverse    # Reverse transaction
GET    /api/transactions/account/{id}/stats # Transaction statistics
```

### Health & Monitoring
```
GET    /api/transactions/health          # Service health check
GET    /actuator/health                  # Actuator health (port 9002)
GET    /actuator/metrics                 # Application metrics
```

## Quick Start

### Prerequisites
- Java 22
- Maven 3.9+
- PostgreSQL 15+
- Redis 7+
- Account Service running on port 8080

### 1. Database Setup
```bash
# Create transaction database
createdb transactiondb

# Or use Docker
docker run --name postgres-transactions -e POSTGRES_DB=transactiondb -e POSTGRES_PASSWORD=postgres -p 5433:5432 -d postgres:15-alpine
```

### 2. Redis Setup
```bash
# Start Redis
docker run --name redis-transactions -p 6379:6379 -d redis:7-alpine
```

### 3. Start the Service
```bash
# Clone and navigate to transaction service
cd transaction-service

# Run the application
./mvnw spring-boot:run
```

### 4. Using Docker Compose
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f transaction-app
```

## Configuration

### Application Properties
Key configuration properties in `application.properties`:

```properties
# Server Configuration
server.port=8081
management.server.port=9002

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5433/transactiondb
spring.datasource.username=postgres
spring.datasource.password=postgres

# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Account Service Integration
account-service.base-url=http://localhost:8080
account-service.timeout=5000

# JWT Security (shared with Account Service)
security.jwt.secret=AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14=
```

### Environment Variables
```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/transactiondb
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Account Service
ACCOUNT_SERVICE_URL=http://localhost:8080

# Security
JWT_SECRET=your-jwt-secret-key
```

## Testing

### Run Tests
```bash
# Unit tests
./mvnw test

# Integration tests
./mvnw test -Dtest="**/*IntegrationTest"

# All tests with coverage
./mvnw test jacoco:report
```

### Test Endpoints
```bash
# Run the comprehensive test script
powershell -ExecutionPolicy Bypass -File ../test-transaction-service.ps1
```

### Manual Testing
1. **Start Account Service** (port 8080)
2. **Start Transaction Service** (port 8081)
3. **Register a user** via Account Service
4. **Create accounts** via Account Service
5. **Test transactions** via Transaction Service

## Database Schema

### Main Tables
- **transactions** - Core transaction records
- **transaction_limits** - Configurable transaction limits

### Key Indexes
- Account-based queries (from_account_id, to_account_id)
- Date-based queries (created_at)
- Status-based queries (status)
- User-based queries (created_by)

## Integration with Account Service

### Communication Pattern
- **Synchronous HTTP**: Real-time account validation and balance updates
- **Shared Authentication**: JWT tokens for user context
- **Error Handling**: Graceful handling of Account Service unavailability

### Account Service Dependencies
- Account validation and information retrieval
- Balance updates after successful transactions
- User authentication and authorization

## Monitoring

### Health Checks
- Application health: `http://localhost:9002/actuator/health`
- Custom health: `http://localhost:8081/api/transactions/health`
- Database connectivity validation
- Redis connectivity validation

### Metrics
- Transaction volume and success rates
- Processing latency and performance
- Error rates and failure patterns
- Cache hit rates and performance

### Logging
- Structured JSON logging
- Transaction audit trails
- Error tracking and debugging
- Performance monitoring

## Security

### Authentication
- JWT token validation (shared secret with Account Service)
- User context extraction from tokens
- Role-based authorization

### Transaction Security
- Input validation and sanitization
- Transaction limit enforcement
- Balance validation before processing
- Audit trail for all operations

### Data Protection
- Sensitive data encryption
- Secure communication with Account Service
- Rate limiting and abuse prevention

## Development

### Project Structure
```
src/main/java/com/suhasan/finance/transaction_service/
├── entity/          # JPA entities
├── dto/             # Data transfer objects
├── repository/      # Data access layer
├── service/         # Business logic
├── controller/      # REST endpoints
├── client/          # External service clients
├── config/          # Configuration classes
├── security/        # Security configuration
└── exception/       # Exception handling
```

### Adding New Features
1. Define entities and DTOs
2. Create repository interfaces
3. Implement service logic
4. Add REST controllers
5. Write comprehensive tests
6. Update documentation

## Troubleshooting

### Common Issues

**Service won't start**
- Check if ports 8081 and 9002 are available
- Verify PostgreSQL and Redis are running
- Check database connection settings

**Authentication failures**
- Ensure JWT secret matches Account Service
- Verify Account Service is running and accessible
- Check token format and expiration

**Transaction failures**
- Verify account exists and is active
- Check sufficient balance for transactions
- Validate transaction limits configuration

**Database connection issues**
- Verify PostgreSQL is running on correct port
- Check database credentials and permissions
- Ensure database `transactiondb` exists

### Logs and Debugging
```bash
# View application logs
docker-compose logs -f transaction-app

# Check database logs
docker-compose logs -f postgres-transactions

# Monitor Redis
docker-compose logs -f redis
```

## Contributing

1. Follow the existing code structure and patterns
2. Write comprehensive tests for new features
3. Update documentation for API changes
4. Follow security best practices
5. Ensure proper error handling and logging

## License

This project is part of the Financial Account System microservices architecture.