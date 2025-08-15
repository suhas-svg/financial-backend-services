-- Transaction Service Database Initialization Script

-- Create database if it doesn't exist (this is handled by Docker environment variables)
-- CREATE DATABASE IF NOT EXISTS transactiondb;

-- Use the transaction database
-- \c transactiondb;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Insert default transaction limits
INSERT INTO transaction_limits (account_type, transaction_type, daily_limit, monthly_limit, per_transaction_limit, daily_count, monthly_count, active) 
VALUES 
    -- Checking account limits
    ('CHECKING', 'TRANSFER', 5000.00, 50000.00, 2000.00, 20, 200, true),
    ('CHECKING', 'WITHDRAWAL', 1000.00, 10000.00, 500.00, 10, 100, true),
    ('CHECKING', 'DEPOSIT', NULL, NULL, 10000.00, NULL, NULL, true),
    
    -- Savings account limits
    ('SAVINGS', 'TRANSFER', 2000.00, 20000.00, 1000.00, 10, 100, true),
    ('SAVINGS', 'WITHDRAWAL', 500.00, 5000.00, 300.00, 5, 50, true),
    ('SAVINGS', 'DEPOSIT', NULL, NULL, 10000.00, NULL, NULL, true),
    
    -- Credit card limits
    ('CREDIT', 'TRANSFER', 10000.00, 100000.00, 5000.00, 50, 500, true),
    ('CREDIT', 'WITHDRAWAL', 2000.00, 20000.00, 1000.00, 20, 200, true),
    ('CREDIT', 'DEPOSIT', NULL, NULL, 10000.00, NULL, NULL, true)
ON CONFLICT (account_type, transaction_type) DO NOTHING;

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_transactions_from_account ON transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_to_account ON transactions(to_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_created_by ON transactions(created_by);

-- Create composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_transactions_account_date ON transactions(from_account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON transactions(created_by, created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status_date ON transactions(status, created_at);

-- Create a view for transaction summaries
CREATE OR REPLACE VIEW transaction_summary AS
SELECT 
    t.transaction_id,
    t.from_account_id,
    t.to_account_id,
    t.amount,
    t.currency,
    t.type,
    t.status,
    t.description,
    t.created_at,
    t.processed_at,
    t.created_by,
    CASE 
        WHEN t.type = 'TRANSFER' THEN 'Transfer'
        WHEN t.type = 'DEPOSIT' THEN 'Deposit'
        WHEN t.type = 'WITHDRAWAL' THEN 'Withdrawal'
        WHEN t.type = 'FEE' THEN 'Fee'
        WHEN t.type = 'INTEREST' THEN 'Interest'
        WHEN t.type = 'REVERSAL' THEN 'Reversal'
        ELSE 'Other'
    END as type_description,
    CASE 
        WHEN t.status = 'PENDING' THEN 'Pending'
        WHEN t.status = 'PROCESSING' THEN 'Processing'
        WHEN t.status = 'COMPLETED' THEN 'Completed'
        WHEN t.status = 'FAILED' THEN 'Failed'
        WHEN t.status = 'REVERSED' THEN 'Reversed'
        WHEN t.status = 'CANCELLED' THEN 'Cancelled'
        ELSE 'Unknown'
    END as status_description
FROM transactions t;

-- Create a function to get daily transaction summary
CREATE OR REPLACE FUNCTION get_daily_transaction_summary(account_id VARCHAR, transaction_date DATE DEFAULT CURRENT_DATE)
RETURNS TABLE(
    transaction_type VARCHAR,
    total_amount DECIMAL,
    transaction_count BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.type::VARCHAR as transaction_type,
        COALESCE(SUM(t.amount), 0) as total_amount,
        COUNT(*)::BIGINT as transaction_count
    FROM transactions t
    WHERE (t.from_account_id = account_id OR t.to_account_id = account_id)
        AND t.status = 'COMPLETED'
        AND DATE(t.created_at) = transaction_date
    GROUP BY t.type;
END;
$$ LANGUAGE plpgsql;

-- Create a function to get monthly transaction summary
CREATE OR REPLACE FUNCTION get_monthly_transaction_summary(account_id VARCHAR, transaction_month INTEGER DEFAULT EXTRACT(MONTH FROM CURRENT_DATE), transaction_year INTEGER DEFAULT EXTRACT(YEAR FROM CURRENT_DATE))
RETURNS TABLE(
    transaction_type VARCHAR,
    total_amount DECIMAL,
    transaction_count BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.type::VARCHAR as transaction_type,
        COALESCE(SUM(t.amount), 0) as total_amount,
        COUNT(*)::BIGINT as transaction_count
    FROM transactions t
    WHERE (t.from_account_id = account_id OR t.to_account_id = account_id)
        AND t.status = 'COMPLETED'
        AND EXTRACT(MONTH FROM t.created_at) = transaction_month
        AND EXTRACT(YEAR FROM t.created_at) = transaction_year
    GROUP BY t.type;
END;
$$ LANGUAGE plpgsql;

-- Insert some sample data for testing (optional)
-- This will be inserted only if the table is empty
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM transactions LIMIT 1) THEN
        -- Sample transactions will be created by the application
        RAISE NOTICE 'Transaction tables initialized successfully';
    END IF;
END $$;