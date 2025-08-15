-- ================================================
-- Staging Environment Database Initialization
-- ================================================

-- Create staging database
CREATE DATABASE IF NOT EXISTS transactiondb_staging;

-- Connect to the staging database
\c transactiondb_staging;

-- Create staging user with limited privileges
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'transaction_staging_user') THEN
        CREATE ROLE transaction_staging_user WITH LOGIN PASSWORD 'staging_secure_password';
    END IF;
END $$;

-- Grant appropriate permissions to staging user
GRANT CONNECT ON DATABASE transactiondb_staging TO transaction_staging_user;
GRANT USAGE ON SCHEMA public TO transaction_staging_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO transaction_staging_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO transaction_staging_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO transaction_staging_user;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO transaction_staging_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO transaction_staging_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO transaction_staging_user;

-- Create read-only user for staging monitoring
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'transaction_staging_readonly') THEN
        CREATE ROLE transaction_staging_readonly WITH LOGIN PASSWORD 'staging_readonly_password';
    END IF;
END $$;

-- Grant read-only permissions
GRANT CONNECT ON DATABASE transactiondb_staging TO transaction_staging_readonly;
GRANT USAGE ON SCHEMA public TO transaction_staging_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO transaction_staging_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO transaction_staging_readonly;

-- Staging-specific settings
SET log_min_duration_statement = 1000; -- Log queries taking more than 1 second
SET log_checkpoints = on;
SET log_connections = on;
SET log_disconnections = on;

-- Create extensions needed for staging
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Staging-specific configuration
INSERT INTO system_configuration (config_key, config_value, description, environment) VALUES
    ('staging.monitoring.enabled', 'true', 'Enable monitoring for staging', 'STAGING'),
    ('staging.backup.enabled', 'true', 'Enable automated backups', 'STAGING'),
    ('staging.performance.tracking', 'true', 'Enable performance tracking', 'STAGING'),
    ('staging.data.retention.days', '90', 'Data retention period in days', 'STAGING')
ON CONFLICT (config_key) DO UPDATE SET 
    config_value = EXCLUDED.config_value,
    updated_at = CURRENT_TIMESTAMP;

-- Create staging-specific indexes for performance
CREATE INDEX IF NOT EXISTS idx_staging_performance_monitoring 
    ON transactions(created_at, status, type) 
    WHERE created_at >= CURRENT_DATE - INTERVAL '30 days';

-- Set up staging-specific maintenance schedule
-- This would typically be handled by cron jobs or scheduled tasks

COMMENT ON DATABASE transactiondb_staging IS 'Staging database for transaction service - mirrors production setup';

-- Log successful initialization
DO $$
BEGIN
    RAISE NOTICE 'Staging database initialized successfully at %', CURRENT_TIMESTAMP;
    RAISE NOTICE 'Staging users created: transaction_staging_user, transaction_staging_readonly';
END $$;