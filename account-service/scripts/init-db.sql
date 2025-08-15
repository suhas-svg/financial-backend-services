-- Database initialization script for Account Service
-- This script runs when the PostgreSQL container starts for the first time

-- Create additional databases for different environments if needed
DO $$
BEGIN
    -- Create development database if it doesn't exist
    IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'myfirstdb_dev') THEN
        CREATE DATABASE myfirstdb_dev;
    END IF;
    
    -- Create staging database if it doesn't exist
    IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'myfirstdb_staging') THEN
        CREATE DATABASE myfirstdb_staging;
    END IF;
    
    -- Create test database if it doesn't exist
    IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'myfirstdb_test') THEN
        CREATE DATABASE myfirstdb_test;
    END IF;
END
$$;

-- Create extensions that might be needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set up basic configuration
ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements';
ALTER SYSTEM SET log_statement = 'all';
ALTER SYSTEM SET log_min_duration_statement = 1000;

-- Create a read-only user for monitoring
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'monitoring') THEN
        CREATE ROLE monitoring WITH LOGIN PASSWORD 'monitoring_password';
        GRANT CONNECT ON DATABASE postgres TO monitoring;
        GRANT USAGE ON SCHEMA public TO monitoring;
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO monitoring;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO monitoring;
    END IF;
END
$$;