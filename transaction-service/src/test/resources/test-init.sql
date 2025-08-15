-- Test database initialization script for integration tests

-- Create transactions table
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(255) PRIMARY KEY,
    from_account_id VARCHAR(255),
    to_account_id VARCHAR(255),
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    transaction_type VARCHAR(50) NOT NULL,
    transaction_status VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    created_by VARCHAR(255),
    reversal_transaction_id VARCHAR(255),
    CONSTRAINT fk_reversal FOREIGN KEY (reversal_transaction_id) REFERENCES transactions(transaction_id)
);

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

-- Insert default transaction limits for testing
INSERT INTO transaction_limits (account_type, transaction_type, daily_limit, monthly_limit, per_transaction_limit, daily_count, monthly_count, active)
VALUES 
    ('STANDARD', 'TRANSFER', 10000.00, 50000.00, 5000.00, 10, 100, true),
    ('STANDARD', 'WITHDRAWAL', 5000.00, 25000.00, 2500.00, 5, 50, true),
    ('STANDARD', 'DEPOSIT', 20000.00, 100000.00, 10000.00, 20, 200, true),
    ('PREMIUM', 'TRANSFER', 50000.00, 250000.00, 25000.00, 50, 500, true),
    ('PREMIUM', 'WITHDRAWAL', 25000.00, 125000.00, 12500.00, 25, 250, true),
    ('PREMIUM', 'DEPOSIT', 100000.00, 500000.00, 50000.00, 100, 1000, true);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_transactions_from_account ON transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_to_account ON transactions(to_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(transaction_status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_by ON transactions(created_by);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(transaction_type);