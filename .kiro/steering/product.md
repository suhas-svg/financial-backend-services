---
inclusion: always
---

# Financial Account Service - Product Guidelines

This is a financial account service microservice for managing user accounts in a financial system. The service provides REST APIs for account operations and is designed to be part of a larger financial services ecosystem.

## Domain Context
- **Financial Domain**: All code operates within `com.suhasan.finance` namespace
- **Account-Centric**: Focus on account-related business logic and data management
- **Microservice Architecture**: Designed as an independent service with clear boundaries

## Core Business Rules
- All financial operations must be transactional and consistent
- Account data integrity is paramount - validate all inputs
- Authentication is required for all account operations
- Audit trails should be maintained for financial transactions
- Follow financial industry standards for data handling and security

## API Design Principles
- RESTful endpoints following standard HTTP methods
- Consistent JSON response formats
- Proper HTTP status codes for different scenarios
- Comprehensive error handling with meaningful messages
- API versioning strategy for backward compatibility

## Security Requirements
- JWT-based authentication for all protected endpoints
- Role-based authorization for different account operations
- Sensitive financial data must be properly encrypted
- Input validation and sanitization for all user inputs
- Rate limiting for API endpoints to prevent abuse

## Data Management
- PostgreSQL as primary database with proper indexing
- JPA entities should reflect financial domain concepts
- Use database transactions for multi-step operations
- Implement proper connection pooling and timeout handling
- Consider data archival strategies for historical records

## Monitoring and Observability
- Structured JSON logging for all operations
- Metrics collection via Micrometer/Prometheus
- Health checks via Spring Boot Actuator
- Performance monitoring for database queries
- Alert on critical financial operation failures