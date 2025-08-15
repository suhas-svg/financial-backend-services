# Project Structure

## Root Package
`com.suhasan.finance.account_service` - Note the underscore in package name due to Maven naming constraints

## Standard Spring Boot Layout
```
src/
├── main/
│   ├── java/com/suhasan/finance/account_service/
│   │   ├── controller/     # REST controllers
│   │   ├── dto/           # Data Transfer Objects
│   │   ├── entity/        # JPA entities
│   │   ├── exception/     # Custom exceptions
│   │   ├── filter/        # Security and request filters
│   │   ├── mapper/        # MapStruct mappers
│   │   ├── repository/    # JPA repositories
│   │   ├── security/      # Security configuration
│   │   ├── service/       # Business logic services
│   │   └── AccountServiceApplication.java
│   └── resources/
│       ├── application.properties
│       ├── logback-spring.xml
│       ├── static/        # Static web resources
│       └── templates/     # Template files
└── test/
    └── java/              # Test classes mirror main structure
```

## Architecture Patterns

### Layered Architecture
- **Controller Layer**: REST endpoints and request handling
- **Service Layer**: Business logic and transaction management
- **Repository Layer**: Data access using Spring Data JPA
- **Entity Layer**: JPA entities representing database tables
- **DTO Layer**: Data transfer objects for API contracts

### Key Conventions
- Use `@RestController` for REST endpoints
- Service classes should be annotated with `@Service`
- Repository interfaces extend `JpaRepository`
- Use MapStruct for entity-DTO mapping
- Lombok annotations for reducing boilerplate code
- Custom exceptions in dedicated package
- Security filters for JWT processing

### Configuration Files
- `application.properties` - Main configuration
- `logback-spring.xml` - Logging configuration
- Maven wrapper scripts (`mvnw`, `mvnw.cmd`) for build consistency