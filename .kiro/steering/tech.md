# Technology Stack

## Core Framework
- **Spring Boot 3.5.3** - Main application framework
- **Java 22** - Programming language version
- **Maven** - Build system and dependency management

## Key Dependencies
- **Spring Data JPA** - Database access and ORM
- **Spring Web** - REST API development
- **Spring Security** - Authentication and authorization
- **Spring Boot Actuator** - Application monitoring and metrics
- **PostgreSQL** - Primary database
- **Lombok** - Code generation for boilerplate reduction
- **MapStruct** - Bean mapping between DTOs and entities
- **JWT (jjwt)** - JSON Web Token implementation
- **Micrometer + Prometheus** - Metrics collection and monitoring
- **Logstash Logback Encoder** - JSON structured logging

## Build Commands

### Development
```bash
# Run the application in development mode
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Testing
```bash
# Run all tests
./mvnw test

# Run tests with coverage
./mvnw test jacoco:report
```

### Build
```bash
# Clean and compile
./mvnw clean compile

# Package the application
./mvnw clean package

# Skip tests during packaging
./mvnw clean package -DskipTests
```

### Database
- Requires PostgreSQL running locally on port 5432
- Database name: `myfirstdb`
- Default credentials: postgres/postgres
- DDL auto-update enabled for development

## Configuration
- Uses `application.properties` for configuration
- Supports environment variable overrides (e.g., `DB_HOST`, `JWT_SECRET`)
- Actuator endpoints exposed for monitoring