-- ================================================
-- Database Migration Validation Script
-- ================================================

-- Set client encoding and error handling
\set ON_ERROR_STOP on
\set ECHO all

-- Display current database and user
SELECT current_database() as database_name, current_user as current_user, version() as postgresql_version;

-- ================================================
-- Validate Flyway Migration History
-- ================================================
\echo '=== Flyway Migration History ==='
SELECT 
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_by,
    installed_on,
    execution_time,
    success
FROM flyway_schema_history 
ORDER BY installed_rank;

-- Check for failed migrations
SELECT COUNT(*) as failed_migrations 
FROM flyway_schema_history 
WHERE success = false;

-- ================================================
-- Validate Table Structure
-- ================================================
\echo '=== Table Structure Validation ==='

-- Check if all required tables exist
DO $$
DECLARE
    missing_tables TEXT[] := ARRAY[]::TEXT[];
    table_name TEXT;
BEGIN
    -- List of required tables
    FOR table_name IN SELECT unnest(ARRAY['transactions', 'transaction_limits', 'system_configuration', 'system_configuration_audit']) LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = table_name AND table_schema = 'public') THEN
            missing_tables := array_append(missing_tables, table_name);
        END IF;
    END LOOP;
    
    IF array_length(missing_tables, 1) > 0 THEN
        RAISE EXCEPTION 'Missing required tables: %', array_to_string(missing_tables, ', ');
    ELSE
        RAISE NOTICE 'All required tables exist';
    END IF;
END $$;

-- Validate transactions table structure
\echo '=== Transactions Table Structure ==='
SELECT 
    column_name,
    data_type,
    is_nullable,
    column_default,
    character_maximum_length
FROM information_schema.columns 
WHERE table_name = 'transactions' 
    AND table_schema = 'public'
ORDER BY ordinal_position;

-- Validate transaction_limits table structure
\echo '=== Transaction Limits Table Structure ==='
SELECT 
    column_name,
    data_type,
    is_nullable,
    column_default,
    character_maximum_length
FROM information_schema.columns 
WHERE table_name = 'transaction_limits' 
    AND table_schema = 'public'
ORDER BY ordinal_position;

-- ================================================
-- Validate Indexes
-- ================================================
\echo '=== Index Validation ==='

-- Check critical indexes exist
DO $$
DECLARE
    missing_indexes TEXT[] := ARRAY[]::TEXT[];
    index_name TEXT;
    critical_indexes TEXT[] := ARRAY[
        'idx_transactions_from_account',
        'idx_transactions_to_account', 
        'idx_transactions_created_at',
        'idx_transactions_status',
        'idx_transaction_limits_lookup'
    ];
BEGIN
    FOR index_name IN SELECT unnest(critical_indexes) LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = index_name AND schemaname = 'public') THEN
            missing_indexes := array_append(missing_indexes, index_name);
        END IF;
    END LOOP;
    
    IF array_length(missing_indexes, 1) > 0 THEN
        RAISE WARNING 'Missing critical indexes: %', array_to_string(missing_indexes, ', ');
    ELSE
        RAISE NOTICE 'All critical indexes exist';
    END IF;
END $$;

-- List all indexes on transaction tables
SELECT 
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes 
WHERE tablename IN ('transactions', 'transaction_limits', 'system_configuration')
    AND schemaname = 'public'
ORDER BY tablename, indexname;

-- ================================================
-- Validate Constraints
-- ================================================
\echo '=== Constraint Validation ==='

-- Check primary key constraints
SELECT 
    tc.table_name,
    tc.constraint_name,
    tc.constraint_type,
    kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_schema = 'public'
    AND tc.table_name IN ('transactions', 'transaction_limits', 'system_configuration')
    AND tc.constraint_type = 'PRIMARY KEY'
ORDER BY tc.table_name;

-- Check foreign key constraints
SELECT 
    tc.table_name,
    tc.constraint_name,
    tc.constraint_type,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage ccu 
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.table_schema = 'public'
    AND tc.table_name IN ('transactions', 'transaction_limits', 'system_configuration')
    AND tc.constraint_type = 'FOREIGN KEY'
ORDER BY tc.table_name;

-- Check unique constraints
SELECT 
    tc.table_name,
    tc.constraint_name,
    tc.constraint_type,
    kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_schema = 'public'
    AND tc.table_name IN ('transactions', 'transaction_limits', 'system_configuration')
    AND tc.constraint_type = 'UNIQUE'
ORDER BY tc.table_name;

-- ================================================
-- Validate Functions and Views
-- ================================================
\echo '=== Functions and Views Validation ==='

-- Check if required functions exist
DO $$
DECLARE
    missing_functions TEXT[] := ARRAY[]::TEXT[];
    function_name TEXT;
    required_functions TEXT[] := ARRAY[
        'get_daily_transaction_summary',
        'get_monthly_transaction_summary',
        'check_transaction_limits',
        'get_account_transaction_stats'
    ];
BEGIN
    FOR function_name IN SELECT unnest(required_functions) LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = function_name) THEN
            missing_functions := array_append(missing_functions, function_name);
        END IF;
    END LOOP;
    
    IF array_length(missing_functions, 1) > 0 THEN
        RAISE WARNING 'Missing required functions: %', array_to_string(missing_functions, ', ');
    ELSE
        RAISE NOTICE 'All required functions exist';
    END IF;
END $$;

-- List all custom functions
SELECT 
    routine_name,
    routine_type,
    data_type as return_type
FROM information_schema.routines 
WHERE routine_schema = 'public'
    AND routine_name NOT LIKE 'pg_%'
ORDER BY routine_name;

-- Check if required views exist
DO $$
DECLARE
    missing_views TEXT[] := ARRAY[]::TEXT[];
    view_name TEXT;
    required_views TEXT[] := ARRAY[
        'transaction_summary'
    ];
BEGIN
    FOR view_name IN SELECT unnest(required_views) LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.views WHERE table_name = view_name AND table_schema = 'public') THEN
            missing_views := array_append(missing_views, view_name);
        END IF;
    END LOOP;
    
    IF array_length(missing_views, 1) > 0 THEN
        RAISE WARNING 'Missing required views: %', array_to_string(missing_views, ', ');
    ELSE
        RAISE NOTICE 'All required views exist';
    END IF;
END $$;

-- List all views
SELECT 
    table_name as view_name,
    view_definition
FROM information_schema.views 
WHERE table_schema = 'public'
ORDER BY table_name;

-- ================================================
-- Validate Data Integrity
-- ================================================
\echo '=== Data Integrity Validation ==='

-- Check transaction_limits data
SELECT 
    'transaction_limits' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE active = true) as active_records,
    COUNT(DISTINCT account_type) as unique_account_types,
    COUNT(DISTINCT transaction_type) as unique_transaction_types
FROM transaction_limits;

-- Validate transaction_limits data completeness
SELECT 
    account_type,
    COUNT(*) as limit_count,
    COUNT(*) FILTER (WHERE active = true) as active_limits
FROM transaction_limits 
GROUP BY account_type 
ORDER BY account_type;

-- Check system_configuration data
SELECT 
    'system_configuration' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE active = true) as active_records,
    COUNT(DISTINCT environment) as unique_environments
FROM system_configuration;

-- ================================================
-- Validate Extensions
-- ================================================
\echo '=== Extensions Validation ==='

-- Check if required extensions are installed
SELECT 
    extname as extension_name,
    extversion as version
FROM pg_extension 
WHERE extname IN ('uuid-ossp', 'pg_stat_statements')
ORDER BY extname;

-- ================================================
-- Performance Validation
-- ================================================
\echo '=== Performance Validation ==='

-- Check table sizes
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size,
    pg_total_relation_size(schemaname||'.'||tablename) as size_bytes
FROM pg_tables 
WHERE schemaname = 'public'
    AND tablename IN ('transactions', 'transaction_limits', 'system_configuration')
ORDER BY size_bytes DESC;

-- Check index sizes
SELECT 
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(schemaname||'.'||indexname)) as size
FROM pg_indexes 
WHERE schemaname = 'public'
    AND tablename IN ('transactions', 'transaction_limits', 'system_configuration')
ORDER BY pg_relation_size(schemaname||'.'||indexname) DESC;

-- ================================================
-- Security Validation
-- ================================================
\echo '=== Security Validation ==='

-- Check table permissions
SELECT 
    grantee,
    table_name,
    privilege_type,
    is_grantable
FROM information_schema.role_table_grants 
WHERE table_schema = 'public'
    AND table_name IN ('transactions', 'transaction_limits', 'system_configuration')
ORDER BY table_name, grantee;

-- ================================================
-- Final Validation Summary
-- ================================================
\echo '=== Migration Validation Summary ==='

DO $$
DECLARE
    table_count INTEGER;
    index_count INTEGER;
    function_count INTEGER;
    view_count INTEGER;
    limit_count INTEGER;
    config_count INTEGER;
BEGIN
    -- Count objects
    SELECT COUNT(*) INTO table_count 
    FROM information_schema.tables 
    WHERE table_schema = 'public' 
        AND table_name IN ('transactions', 'transaction_limits', 'system_configuration', 'system_configuration_audit');
    
    SELECT COUNT(*) INTO index_count 
    FROM pg_indexes 
    WHERE schemaname = 'public' 
        AND tablename IN ('transactions', 'transaction_limits', 'system_configuration');
    
    SELECT COUNT(*) INTO function_count 
    FROM pg_proc p
    JOIN pg_namespace n ON p.pronamespace = n.oid
    WHERE n.nspname = 'public' 
        AND p.proname NOT LIKE 'pg_%';
    
    SELECT COUNT(*) INTO view_count 
    FROM information_schema.views 
    WHERE table_schema = 'public';
    
    SELECT COUNT(*) INTO limit_count 
    FROM transaction_limits 
    WHERE active = true;
    
    SELECT COUNT(*) INTO config_count 
    FROM system_configuration 
    WHERE active = true;
    
    -- Display summary
    RAISE NOTICE '=== MIGRATION VALIDATION SUMMARY ===';
    RAISE NOTICE 'Tables created: %', table_count;
    RAISE NOTICE 'Indexes created: %', index_count;
    RAISE NOTICE 'Functions created: %', function_count;
    RAISE NOTICE 'Views created: %', view_count;
    RAISE NOTICE 'Active transaction limits: %', limit_count;
    RAISE NOTICE 'Active system configurations: %', config_count;
    
    -- Validation result
    IF table_count >= 4 AND index_count >= 10 AND function_count >= 5 AND limit_count >= 30 THEN
        RAISE NOTICE 'VALIDATION RESULT: SUCCESS - All migration components are present and valid';
    ELSE
        RAISE WARNING 'VALIDATION RESULT: INCOMPLETE - Some migration components may be missing';
    END IF;
END $$;

\echo '=== Migration validation completed ==='