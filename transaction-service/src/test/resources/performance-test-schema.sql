-- Performance test database schema
-- This script creates optimized indexes for performance testing

-- Create transactions table with proper indexes
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(255) PRIMARY KEY,
    from_account_id VARCHAR(255),
    to_account_id VARCHAR(255),
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    created_by VARCHAR(255),
    reversal_transaction_id VARCHAR(255)
);

-- Create indexes for performance optimization
CREATE INDEX IF NOT EXISTS idx_transactions_from_account ON transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_to_account ON transactions(to_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_created_by ON transactions(created_by);
CREATE INDEX IF NOT EXISTS idx_transactions_processed_at ON transactions(processed_at);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_transactions_account_date ON transactions(from_account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status_date ON transactions(status, created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_type_date ON transactions(type, created_at);

-- Create transaction_limits table
CREATE TABLE IF NOT EXISTS transaction_limits (
    id BIGSERIAL PRIMARY KEY,
    account_type VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    daily_limit DECIMAL(19,2),
    monthly_limit DECIMAL(19,2),
    per_transaction_limit DECIMAL(19,2),
    daily_count INTEGER,
    monthly_count INTEGER,
    active BOOLEAN DEFAULT true,
    UNIQUE(account_type, transaction_type)
);

-- Insert some default transaction limits for testing
INSERT INTO transaction_limits (account_type, transaction_type, daily_limit, monthly_limit, per_transaction_limit, daily_count, monthly_count) 
VALUES 
    ('STANDARD', 'TRANSFER', 10000.00, 50000.00, 5000.00, 50, 200),
    ('STANDARD', 'WITHDRAWAL', 5000.00, 25000.00, 2500.00, 20, 100),
    ('STANDARD', 'DEPOSIT', 20000.00, 100000.00, 10000.00, 100, 500),
    ('PREMIUM', 'TRANSFER', 50000.00, 250000.00, 25000.00, 200, 1000),
    ('PREMIUM', 'WITHDRAWAL', 25000.00, 125000.00, 12500.00, 100, 500),
    ('PREMIUM', 'DEPOSIT', 100000.00, 500000.00, 50000.00, 500, 2500)
ON CONFLICT (account_type, transaction_type) DO NOTHING;

-- Create a function to generate test data efficiently
CREATE OR REPLACE FUNCTION generate_test_transactions(num_records INTEGER)
RETURNS VOID AS $$
DECLARE
    i INTEGER;
    transaction_types TEXT[] := ARRAY['TRANSFER', 'DEPOSIT', 'WITHDRAWAL'];
    transaction_statuses TEXT[] := ARRAY['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'];
BEGIN
    FOR i IN 1..num_records LOOP
        INSERT INTO transactions (
            transaction_id,
            from_account_id,
            to_account_id,
            amount,
            currency,
            type,
            status,
            description,
            created_at,
            processed_at,
            created_by
        ) VALUES (
            'perf-test-' || i || '-' || gen_random_uuid(),
            'account-' || (i % 1000 + 1),
            'account-' || ((i + 500) % 1000 + 1001),
            (random() * 9000 + 100)::DECIMAL(19,2),
            'USD',
            transaction_types[1 + (random() * array_length(transaction_types, 1))::INTEGER % array_length(transaction_types, 1)],
            transaction_statuses[1 + (random() * array_length(transaction_statuses, 1))::INTEGER % array_length(transaction_statuses, 1)],
            'Performance test transaction ' || i,
            CURRENT_TIMESTAMP - (random() * INTERVAL '30 days'),
            CURRENT_TIMESTAMP - (random() * INTERVAL '30 days'),
            'perf-test-user'
        );
        
        -- Commit every 1000 records to avoid long transactions
        IF i % 1000 = 0 THEN
            COMMIT;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Create a function to analyze query performance
CREATE OR REPLACE FUNCTION analyze_query_performance(query_text TEXT)
RETURNS TABLE(execution_time_ms NUMERIC, rows_returned BIGINT) AS $$
DECLARE
    start_time TIMESTAMP;
    end_time TIMESTAMP;
    result_count BIGINT;
BEGIN
    start_time := clock_timestamp();
    
    EXECUTE query_text;
    GET DIAGNOSTICS result_count = ROW_COUNT;
    
    end_time := clock_timestamp();
    
    RETURN QUERY SELECT 
        EXTRACT(EPOCH FROM (end_time - start_time)) * 1000,
        result_count;
END;
$$ LANGUAGE plpgsql;

-- Create materialized view for transaction statistics (for performance testing)
CREATE MATERIALIZED VIEW IF NOT EXISTS transaction_stats AS
SELECT 
    DATE(created_at) as transaction_date,
    from_account_id,
    type,
    status,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    MIN(amount) as min_amount,
    MAX(amount) as max_amount
FROM transactions
GROUP BY DATE(created_at), from_account_id, type, status;

-- Create index on materialized view
CREATE INDEX IF NOT EXISTS idx_transaction_stats_date_account ON transaction_stats(transaction_date, from_account_id);

-- Create a function to refresh statistics
CREATE OR REPLACE FUNCTION refresh_transaction_stats()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW transaction_stats;
END;
$$ LANGUAGE plpgsql;