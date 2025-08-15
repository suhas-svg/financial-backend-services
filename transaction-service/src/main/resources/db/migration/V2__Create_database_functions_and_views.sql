-- ================================================
-- Transaction Service Database Functions and Views
-- Version: 2.0 (Fixed)
-- Description: Create database functions, views, and stored procedures
-- ================================================

-- ================================================
-- Create transaction summary view
-- ================================================
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
    t.reference,
    t.created_at,
    t.processed_at,
    t.created_by,
    t.processed_by,
    
    -- Human-readable descriptions
    CASE 
        WHEN t.type = 'TRANSFER' THEN 'Transfer between accounts'
        WHEN t.type = 'DEPOSIT' THEN 'Deposit to account'
        WHEN t.type = 'WITHDRAWAL' THEN 'Withdrawal from account'
        WHEN t.type = 'FEE' THEN 'Service fee'
        WHEN t.type = 'INTEREST' THEN 'Interest payment'
        WHEN t.type = 'REVERSAL' THEN 'Transaction reversal'
        WHEN t.type = 'REFUND' THEN 'Transaction refund'
        ELSE 'Other'
    END as type_description,
    
    CASE 
        WHEN t.status = 'PENDING' THEN 'Pending processing'
        WHEN t.status = 'PROCESSING' THEN 'Being processed'
        WHEN t.status = 'COMPLETED' THEN 'Completed successfully'
        WHEN t.status = 'FAILED' THEN 'Failed'
        WHEN t.status = 'REVERSED' THEN 'Reversed'
        WHEN t.status = 'CANCELLED' THEN 'Cancelled'
        ELSE 'Unknown'
    END as status_description,
    
    -- Reversal information
    t.original_transaction_id,
    t.reversal_transaction_id,
    t.reversed_at,
    t.reversed_by,
    t.reversal_reason,
    
    -- Processing time calculation
    CASE 
        WHEN t.processed_at IS NOT NULL AND t.created_at IS NOT NULL 
        THEN EXTRACT(EPOCH FROM (t.processed_at - t.created_at))
        ELSE NULL
    END as processing_time_seconds
    
FROM transactions t;

COMMENT ON VIEW transaction_summary IS 'Enhanced view of transactions with human-readable descriptions and calculated fields';

-- ================================================
-- Function: Get daily transaction summary for an account
-- ================================================
CREATE OR REPLACE FUNCTION get_daily_transaction_summary(
    p_account_id VARCHAR,
    p_transaction_date DATE DEFAULT CURRENT_DATE
)
RETURNS TABLE(
    transaction_type VARCHAR,
    total_amount DECIMAL(19,2),
    transaction_count BIGINT,
    avg_amount DECIMAL(19,2),
    max_amount DECIMAL(19,2),
    min_amount DECIMAL(19,2)
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.type::VARCHAR as transaction_type,
        COALESCE(SUM(t.amount), 0)::DECIMAL(19,2) as total_amount,
        COUNT(*)::BIGINT as transaction_count,
        COALESCE(AVG(t.amount), 0)::DECIMAL(19,2) as avg_amount,
        COALESCE(MAX(t.amount), 0)::DECIMAL(19,2) as max_amount,
        COALESCE(MIN(t.amount), 0)::DECIMAL(19,2) as min_amount
    FROM transactions t
    WHERE (t.from_account_id = p_account_id OR t.to_account_id = p_account_id)
        AND t.status = 'COMPLETED'
        AND DATE(t.created_at) = p_transaction_date
    GROUP BY t.type
    ORDER BY total_amount DESC;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_daily_transaction_summary IS 'Returns daily transaction summary statistics for a specific account';

-- ================================================
-- Function: Get account transaction statistics
-- ================================================
CREATE OR REPLACE FUNCTION get_account_transaction_stats(
    p_account_id VARCHAR,
    p_days_back INTEGER DEFAULT 30
)
RETURNS TABLE(
    total_transactions BIGINT,
    total_amount DECIMAL(19,2),
    avg_transaction_amount DECIMAL(19,2),
    max_transaction_amount DECIMAL(19,2),
    min_transaction_amount DECIMAL(19,2),
    successful_transactions BIGINT,
    failed_transactions BIGINT,
    success_rate DECIMAL(5,2),
    most_common_type VARCHAR,
    last_transaction_date TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    WITH transaction_stats AS (
        SELECT 
            COUNT(*) as total_count,
            SUM(t.amount) as total_amt,
            AVG(t.amount) as avg_amt,
            MAX(t.amount) as max_amt,
            MIN(t.amount) as min_amt,
            COUNT(*) FILTER (WHERE t.status = 'COMPLETED') as success_count,
            COUNT(*) FILTER (WHERE t.status = 'FAILED') as failed_count,
            MAX(t.created_at) as last_transaction
        FROM transactions t
        WHERE (t.from_account_id = p_account_id OR t.to_account_id = p_account_id)
            AND t.created_at >= CURRENT_DATE - INTERVAL '1 day' * p_days_back
    ),
    most_common AS (
        SELECT t.type
        FROM transactions t
        WHERE (t.from_account_id = p_account_id OR t.to_account_id = p_account_id)
            AND t.created_at >= CURRENT_DATE - INTERVAL '1 day' * p_days_back
        GROUP BY t.type
        ORDER BY COUNT(*) DESC
        LIMIT 1
    )
    SELECT 
        ts.total_count::BIGINT,
        COALESCE(ts.total_amt, 0)::DECIMAL(19,2),
        COALESCE(ts.avg_amt, 0)::DECIMAL(19,2),
        COALESCE(ts.max_amt, 0)::DECIMAL(19,2),
        COALESCE(ts.min_amt, 0)::DECIMAL(19,2),
        ts.success_count::BIGINT,
        ts.failed_count::BIGINT,
        CASE 
            WHEN ts.total_count > 0 THEN (ts.success_count::DECIMAL / ts.total_count * 100)::DECIMAL(5,2)
            ELSE 0::DECIMAL(5,2)
        END,
        COALESCE(mc.type, 'NONE')::VARCHAR,
        ts.last_transaction
    FROM transaction_stats ts
    CROSS JOIN most_common mc;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_account_transaction_stats IS 'Returns comprehensive transaction statistics for an account over a specified period';

-- ================================================
-- Function: Update transaction limits timestamp
-- ================================================
CREATE OR REPLACE FUNCTION update_transaction_limits_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for transaction_limits table (only if it doesn't exist)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_transaction_limits_updated_at') THEN
        CREATE TRIGGER trg_transaction_limits_updated_at
            BEFORE UPDATE ON transaction_limits
            FOR EACH ROW
            EXECUTE FUNCTION update_transaction_limits_timestamp();
    END IF;
END
$$;

COMMENT ON FUNCTION update_transaction_limits_timestamp IS 'Trigger function to automatically update the updated_at timestamp';

-- ================================================
-- Create materialized view for transaction analytics
-- ================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS transaction_analytics AS
SELECT 
    DATE(t.created_at) as transaction_date,
    t.type as transaction_type,
    t.status as transaction_status,
    COUNT(*) as transaction_count,
    SUM(t.amount) as total_amount,
    AVG(t.amount) as avg_amount,
    MAX(t.amount) as max_amount,
    MIN(t.amount) as min_amount,
    COUNT(*) FILTER (WHERE t.status = 'COMPLETED') as successful_count,
    COUNT(*) FILTER (WHERE t.status = 'FAILED') as failed_count,
    CASE 
        WHEN COUNT(*) > 0 THEN (COUNT(*) FILTER (WHERE t.status = 'COMPLETED')::DECIMAL / COUNT(*) * 100)
        ELSE 0
    END as success_rate
FROM transactions t
WHERE t.created_at >= CURRENT_DATE - INTERVAL '1 year'
GROUP BY DATE(t.created_at), t.type, t.status
ORDER BY transaction_date DESC, transaction_type;

-- Create index on materialized view
CREATE INDEX IF NOT EXISTS idx_transaction_analytics_date 
    ON transaction_analytics(transaction_date);

CREATE INDEX IF NOT EXISTS idx_transaction_analytics_type 
    ON transaction_analytics(transaction_type);

COMMENT ON MATERIALIZED VIEW transaction_analytics IS 'Materialized view for transaction analytics and reporting';

-- ================================================
-- Function to refresh transaction analytics
-- ================================================
CREATE OR REPLACE FUNCTION refresh_transaction_analytics()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY transaction_analytics;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_transaction_analytics IS 'Function to refresh the transaction analytics materialized view';