# Database Migration Guide

## Overview

This guide provides comprehensive instructions for managing database migrations in the Transaction Service using Flyway. The service uses Flyway for version-controlled database schema management across different environments.

## Migration Structure

```
src/main/resources/db/migration/
├── V1__Create_transaction_tables.sql
├── V2__Create_database_functions_and_views.sql
├── V3__Insert_default_transaction_limits.sql
├── V4__Create_environment_specific_configurations.sql
└── V5__Create_additional_indexes_and_optimizations.sql
```

## Environment Configuration

### Development Environment
- **Database**: `transactiondb_dev`
- **Port**: `5433`
- **User**: `postgres`
- **Profile**: `dev`

### Staging Environment
- **Database**: `transactiondb_staging`
- **Port**: `5432`
- **User**: `transaction_staging_user`
- **Profile**: `staging`

### Production Environment
- **Database**: `transactiondb`
- **Port**: `5432`
- **User**: `transaction_app_user`
- **Profile**: `prod`

## Migration Commands

### Using Maven Profiles

#### Development Environment
```bash
# Run migrations for development
./mvnw flyway:migrate -Pdev

# Clean and migrate (development only)
./mvnw flyway:clean flyway:migrate -Pdev

# Check migration status
./mvnw flyway:info -Pdev

# Validate migrations
./mvnw flyway:validate -Pdev
```

#### Staging Environment
```bash
# Run migrations for staging
./mvnw flyway:migrate -Pstaging

# Check migration status
./mvnw flyway:info -Pstaging

# Validate migrations
./mvnw flyway:validate -Pstaging
```

#### Production Environment
```bash
# Set environment variable for production password
export FLYWAY_PASSWORD=your_production_password

# Run migrations for production
./mvnw flyway:migrate -Pprod

# Check migration status
./mvnw flyway:info -Pprod

# Validate migrations (recommended before deployment)
./mvnw flyway:validate -Pprod
```

### Using Spring Boot Application

#### Run Application with Migration
```bash
# Development
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Staging
./mvnw spring-boot:run -Dspring-boot.run.profiles=staging

# Production
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Database Initialization

### Initial Setup Scripts

#### Development
```bash
# Initialize development database
psql -U postgres -h localhost -p 5433 -f scripts/init-db-dev.sql
```

#### Staging
```bash
# Initialize staging database
psql -U postgres -h staging-db -p 5432 -f scripts/init-db-staging.sql
```

#### Production
```bash
# Initialize production database (requires admin privileges)
psql -U postgres -h prod-db -p 5432 -f scripts/init-db-prod.sql
```

## Migration Details

### V1: Create Transaction Tables
- Creates `transactions` table with all required fields
- Creates `transaction_limits` table for limit configuration
- Adds primary and foreign key constraints
- Creates basic indexes for performance

### V2: Create Database Functions and Views
- Creates `transaction_summary` view for enhanced queries
- Adds functions for daily/monthly transaction summaries
- Implements transaction limit checking function
- Creates account transaction statistics function
- Adds materialized views for analytics

### V3: Insert Default Transaction Limits
- Populates default transaction limits for different account types:
  - CHECKING accounts
  - SAVINGS accounts
  - CREDIT accounts
  - BUSINESS accounts
  - PREMIUM accounts
- Configures appropriate limits for each transaction type

### V4: Create Environment-Specific Configurations
- Creates `system_configuration` table for environment settings
- Adds configuration audit table and triggers
- Implements environment-specific functions
- Creates database maintenance procedures

### V5: Create Additional Indexes and Optimizations
- Adds performance-optimized indexes
- Creates materialized views for analytics
- Implements index maintenance functions
- Adds database statistics and monitoring functions

## Best Practices

### Migration File Naming
- Use sequential version numbers: `V1__`, `V2__`, etc.
- Use descriptive names with underscores
- Never modify existing migration files after they've been applied

### Migration Content
- Always use `IF NOT EXISTS` for CREATE statements
- Include rollback comments for complex changes
- Add appropriate indexes for performance
- Include data validation checks

### Environment Management
- Use environment-specific profiles
- Never use `flyway:clean` in production
- Always validate migrations before applying
- Keep environment configurations separate

## Troubleshooting

### Common Issues

#### Migration Checksum Mismatch
```bash
# Repair migration checksums
./mvnw flyway:repair -Pdev
```

#### Failed Migration
```bash
# Check migration status
./mvnw flyway:info -Pdev

# Repair if needed
./mvnw flyway:repair -Pdev

# Re-run migration
./mvnw flyway:migrate -Pdev
```

#### Database Connection Issues
1. Verify database is running
2. Check connection parameters in application properties
3. Ensure user has appropriate permissions
4. Verify network connectivity

### Rollback Strategies

#### Development Environment
```bash
# Clean and re-migrate (development only)
./mvnw flyway:clean flyway:migrate -Pdev
```

#### Production Environment
- Create new migration with rollback SQL
- Never use `flyway:clean` in production
- Always test rollback procedures in staging first

## Monitoring and Maintenance

### Database Health Checks
```sql
-- Check migration status
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;

-- Verify table structure
\d transactions
\d transaction_limits

-- Check data integrity
SELECT COUNT(*) FROM transactions;
SELECT COUNT(*) FROM transaction_limits WHERE active = true;
```

### Performance Monitoring
```sql
-- Check index usage
SELECT * FROM get_index_usage_stats();

-- Find unused indexes
SELECT * FROM find_unused_indexes();

-- Update statistics
SELECT update_database_statistics();
```

### Maintenance Tasks
```sql
-- Daily maintenance
SELECT daily_maintenance();

-- Weekly maintenance
SELECT weekly_maintenance();

-- Rebuild indexes (if needed)
SELECT rebuild_transaction_indexes();
```

## Security Considerations

### Production Security
- Use strong passwords for database users
- Limit user permissions to minimum required
- Enable SSL/TLS for database connections
- Regularly rotate database passwords
- Monitor database access logs

### Migration Security
- Review all migration scripts before applying
- Use separate users for migration vs. application
- Audit all schema changes
- Backup database before major migrations

## Backup and Recovery

### Before Migration
```bash
# Create backup before migration
pg_dump -U postgres -h localhost -p 5432 transactiondb > backup_pre_migration.sql
```

### After Migration
```bash
# Verify migration success
./mvnw flyway:info -Pprod

# Create post-migration backup
pg_dump -U postgres -h localhost -p 5432 transactiondb > backup_post_migration.sql
```

## Support and Documentation

### Additional Resources
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Spring Boot Database Migration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto-database-initialization)

### Contact Information
For migration issues or questions, contact the development team or refer to the project documentation.