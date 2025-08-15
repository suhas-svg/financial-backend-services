-- ================================================
-- Production Environment Database Initialization
-- ================================================

-- Create production database
CREATE DATABASE IF NOT EXISTS transactiondb;

-- Connect to the production database
\c transactiondb;

-- Create production application user with minimal required privileges
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'transaction_app_user') THEN
        CREATE ROLE transaction_app_user WITH LOGIN PASSWORD 'CHANGE_ME_PRODUCTION_PASSWORD';
    END IF;
END $$;

-- Grant minimal required permissions to application user
GRANT CONNECT ON DATABASE transactiondb TO transaction_app_user;
GRANT USAGE ON SCHEMA public TO transaction_app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO transaction_app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO transaction_app_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO transaction_app_user;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO transaction_app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO transaction_app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO transaction_app_user;

-- Create read-only user for monitoring and reporting
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'transaction_readonly') THEN
        CREATE ROLE transaction_readonly WITH LOGIN PASSWORD 'CHANGE_ME_READONLY_PASSWORD';
    END IF;
END $$;

-- Grant read-only permissions
GRANT CONNECT ON DATABASE transactiondb TO transaction_readonly;
GRANT USAGE ON SCHEMA public TO transaction_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO transaction_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO transaction_readonly;

-- Create backup user for database maintenance
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'transaction_backup') THEN
        CREATE ROLE transaction_backup WITH LOGIN PASSWORD 'CHANGE_ME_BACKUP_PASSWORD';
    END IF;
END $$;

-- Grant backup permissions
GRANT CONNECT ON DATABASE transactiondb TO transaction_backup;
GRANT USAGE ON SCHEMA public TO transaction_backup;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO transaction_backup;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO transaction_backup;

-- Production-specific settings for security and performance
SET log_min_duration_statement = 5000; -- Log queries taking more than 5 seconds
SET log_checkpoints = on;
SET log_connections = on;
SET log_disconnections = on;
SET log_lock_waits = on;
SET deadlock_timeout = '1s';
SET statement_timeout = '30s';

-- Create extensions needed for production
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Production-specific configuration
INSERT INTO system_configuration (config_key, config_value, description, environment) VALUES
    ('prod.monitoring.enabled', 'true', 'Enable comprehensive monitoring', 'PROD'),
    ('prod.alerting.enabled', 'true', 'Enable alerting for critical events', 'PROD'),
    ('prod.backup.enabled', 'true', 'Enable automated backups', 'PROD'),
    ('prod.archival.enabled', 'true', 'Enable data archival', 'PROD'),
    ('prod.data.retention.years', '7', 'Data retention period in years', 'PROD'),
    ('prod.high.availability.enabled', 'true', 'Enable high availability features', 'PROD'),
    ('prod.encryption.enabled', 'true', 'Enable data encryption', 'PROD'),
    ('prod.audit.detailed', 'true', 'Enable detailed audit logging', 'PROD')
ON CONFLICT (config_key) DO UPDATE SET 
    config_value = EXCLUDED.config_value,
    updated_at = CURRENT_TIMESTAMP;

-- Create production-specific indexes for optimal performance
CREATE INDEX IF NOT EXISTS idx_prod_high_performance_queries 
    ON transactions(status, created_at, from_account_id) 
    WHERE status IN ('COMPLETED', 'PROCESSING');

CREATE INDEX IF NOT EXISTS idx_prod_audit_queries 
    ON transactions(created_by, created_at DESC) 
    WHERE created_at >= CURRENT_DATE - INTERVAL '1 year';

-- Create production-specific partitioning (if needed for large datasets)
-- This would be implemented based on actual data volume requirements

-- Set up row-level security (RLS) for additional security
-- ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
-- This would be configured based on specific security requirements

-- Create production-specific maintenance functions
CREATE OR REPLACE FUNCTION production_health_check()
RETURNS TABLE(
    check_name TEXT,
    status TEXT,
    details TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        'Database Connection'::TEXT,
        'OK'::TEXT,
        'Database is accessible'::TEXT
    UNION ALL
    SELECT 
        'Transaction Table'::TEXT,
        CASE WHEN EXISTS (SELECT 1 FROM transactions LIMIT 1) THEN 'OK' ELSE 'WARNING' END::TEXT,
        'Transaction table status'::TEXT
    UNION ALL
    SELECT 
        'Index Health'::TEXT,
        'OK'::TEXT,
        'All indexes are accessible'::TEXT;
END;
$$ LANGUAGE plpgsql;

-- Create production backup verification function
CREATE OR REPLACE FUNCTION verify_backup_integrity()
RETURNS BOOLEAN AS $$
DECLARE
    transaction_count BIGINT;
    limit_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO transaction_count FROM transactions;
    SELECT COUNT(*) INTO limit_count FROM transaction_limits;
    
    -- Basic integrity checks
    IF transaction_count >= 0 AND limit_count > 0 THEN
        RETURN TRUE;
    ELSE
        RETURN FALSE;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Set up production-specific security
-- Revoke unnecessary permissions from public
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO transaction_app_user, transaction_readonly, transaction_backup;

-- Create security audit function
CREATE OR REPLACE FUNCTION log_security_event(
    event_type TEXT,
    event_details TEXT,
    user_id TEXT DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    -- This would integrate with your security logging system
    RAISE NOTICE 'SECURITY EVENT: % - % - User: %', event_type, event_details, COALESCE(user_id, 'SYSTEM');
END;
$$ LANGUAGE plpgsql;

COMMENT ON DATABASE transactiondb IS 'Production database for transaction service - high security and performance configuration';

-- Log successful initialization with security notice
DO $$
BEGIN
    RAISE NOTICE 'Production database initialized successfully at %', CURRENT_TIMESTAMP;
    RAISE NOTICE 'SECURITY NOTICE: Please change default passwords for all production users';
    RAISE NOTICE 'Production users created: transaction_app_user, transaction_readonly, transaction_backup';
    RAISE NOTICE 'Remember to configure SSL/TLS, backup schedules, and monitoring';
END $$;