-- Full System Integration Test Database Initialization
-- This script sets up the database schema for both Account Service and Transaction Service

-- Create schemas for both services
CREATE SCHEMA IF NOT EXISTS account_service;
CREATE SCHEMA IF NOT EXISTS transaction_service;

-- Account Service Tables
CREATE TABLE IF NOT EXISTS account_service.accounts (
    id BIGSERIAL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_accounts_owner_id ON account_service.accounts(owner_id);
CREATE INDEX IF NOT EXISTS idx_accounts_active ON account_service.accounts(active);

-- Transaction Service Tables
CREATE TABLE IF NOT EXISTS transaction_service.transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(255) UNIQUE NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    from_account_id VARCHAR(255),
    to_account_id VARCHAR(255),
    amount DECIMAL(19,2) NOT NULL,
    description TEXT,
    reference_id VARCHAR(255),
    reversal_transaction_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    processed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_transactions_transaction_id ON transaction_service.transactions(transaction_id);
CREATE INDEX IF NOT EXISTS idx_transactions_from_account ON transaction_service.transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_to_account ON transaction_service.transactions(to_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transaction_service.transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transaction_service.transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transaction_service.transactions(created_at);

-- Transaction Limits Table
CREATE TABLE IF NOT EXISTS transaction_service.transaction_limits (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    daily_limit DECIMAL(19,2) NOT NULL,
    monthly_limit DECIMAL(19,2) NOT NULL,
    per_transaction_limit DECIMAL(19,2) NOT NULL,
    daily_used DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    monthly_used DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    last_reset_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_transaction_limits_account ON transaction_service.transaction_limits(account_id);

-- Audit Log Table
CREATE TABLE IF NOT EXISTS transaction_service.audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    event_data JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address INET,
    user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_events_entity ON transaction_service.audit_events(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_user ON transaction_service.audit_events(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_timestamp ON transaction_service.audit_events(timestamp);

-- Insert default transaction limits for different account types
INSERT INTO transaction_service.transaction_limits (account_id, account_type, daily_limit, monthly_limit, per_transaction_limit)
VALUES 
    ('DEFAULT_STANDARD', 'STANDARD', 5000.00, 50000.00, 1000.00),
    ('DEFAULT_PREMIUM', 'PREMIUM', 50000.00, 500000.00, 10000.00),
    ('DEFAULT_BUSINESS', 'BUSINESS', 100000.00, 1000000.00, 25000.00)
ON CONFLICT (account_id) DO NOTHING;

-- Create functions for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for automatic timestamp updates
DROP TRIGGER IF EXISTS update_accounts_updated_at ON account_service.accounts;
CREATE TRIGGER update_accounts_updated_at 
    BEFORE UPDATE ON account_service.accounts 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_transactions_updated_at ON transaction_service.transactions;
CREATE TRIGGER update_transactions_updated_at 
    BEFORE UPDATE ON transaction_service.transactions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_transaction_limits_updated_at ON transaction_service.transaction_limits;
CREATE TRIGGER update_transaction_limits_updated_at 
    BEFORE UPDATE ON transaction_service.transaction_limits 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions (adjust as needed for your setup)
GRANT ALL PRIVILEGES ON SCHEMA account_service TO testuser;
GRANT ALL PRIVILEGES ON SCHEMA transaction_service TO testuser;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA account_service TO testuser;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA transaction_service TO testuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA account_service TO testuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA transaction_service TO testuser;

-- Insert some test data for integration testing
INSERT INTO account_service.accounts (owner_id, account_type, balance, created_by)
VALUES 
    ('system-test-user-1', 'STANDARD', 1000.00, 'system'),
    ('system-test-user-2', 'PREMIUM', 5000.00, 'system'),
    ('system-test-user-3', 'BUSINESS', 10000.00, 'system')
ON CONFLICT DO NOTHING;

-- Create a view for transaction reporting
CREATE OR REPLACE VIEW transaction_service.transaction_summary AS
SELECT 
    DATE(created_at) as transaction_date,
    type,
    status,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(amount) as average_amount
FROM transaction_service.transactions
GROUP BY DATE(created_at), type, status
ORDER BY transaction_date DESC, type;

-- Create a view for account balances with transaction history
CREATE OR REPLACE VIEW account_service.account_with_transaction_count AS
SELECT 
    a.*,
    COALESCE(t.transaction_count, 0) as transaction_count,
    COALESCE(t.last_transaction_date, a.created_at) as last_transaction_date
FROM account_service.accounts a
LEFT JOIN (
    SELECT 
        COALESCE(from_account_id, to_account_id) as account_id,
        COUNT(*) as transaction_count,
        MAX(created_at) as last_transaction_date
    FROM transaction_service.transactions
    WHERE status = 'COMPLETED'
    GROUP BY COALESCE(from_account_id, to_account_id)
) t ON a.id::text = t.account_id;

COMMIT;