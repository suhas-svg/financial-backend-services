-- ================================================
-- Development Environment Database Initialization
-- ================================================

-- Create development database
CREATE DATABASE IF NOT EXISTS transactiondb_dev;

-- Connect to the development database
\c transactiondb_dev;

-- Create development user
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'transaction_dev_user') THEN
        CREATE ROLE transaction_dev_user WITH LOGIN PASSWORD 'dev_password';
    END IF;
END $$;

-- Grant permissions to development user
GRANT ALL PRIVILEGES ON DATABASE transactiondb_dev TO transaction_dev_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO transaction_dev_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO transaction_dev_user;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO transaction_dev_user;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO transaction_dev_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO transaction_dev_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO transaction_dev_user;

-- Development-specific settings
SET log_statement = 'all';
SET log_min_duration_statement = 0;

-- Create extensions needed for development
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Development-specific configuration
INSERT INTO system_configuration (config_key, config_value, description, environment) VALUES
    ('dev.debug.enabled', 'true', 'Enable debug mode for development', 'DEV'),
    ('dev.sample.data.enabled', 'true', 'Enable sample data generation', 'DEV'),
    ('dev.performance.logging', 'true', 'Enable performance logging', 'DEV')
ON CONFLICT (config_key) DO UPDATE SET 
    config_value = EXCLUDED.config_value,
    updated_at = CURRENT_TIMESTAMP;

COMMENT ON DATABASE transactiondb_dev IS 'Development database for transaction service';

-- Log successful initialization
DO $$
BEGIN
    RAISE NOTICE 'Development database initialized successfully at %', CURRENT_TIMESTAMP;
END $$;