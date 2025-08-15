-- ================================================
-- Transaction Service Database Schema Migration
-- Version: 1.0
-- Description: Create initial transaction tables and indexes
-- ================================================

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ================================================
-- Create transaction_limits table
-- ================================================
CREATE TABLE IF NOT EXISTS transaction_limits (
    id BIGSERIAL PRIMARY KEY,
    account_type VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    daily_limit DECIMAL(19,2),
    monthly_limit DECIMAL(19,2),
    per_transaction_limit DECIMAL(19,2),
    daily_count INTEGER,
    monthly_count INTEGER,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_transaction_limits_account_type_transaction_type 
        UNIQUE (account_type, transaction_type)
);

-- ================================================
-- Create transactions table
-- ================================================
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(36) PRIMARY KEY,
    from_account_id VARCHAR(255) NOT NULL,
    to_account_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    description VARCHAR(500),
    reference VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    processed_by VARCHAR(255),
    
    -- Audit fields
    audit_trail TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    
    -- Balance tracking for audit
    from_account_balance_before DECIMAL(19,2),
    from_account_balance_after DECIMAL(19,2),
    to_account_balance_before DECIMAL(19,2),
    to_account_balance_after DECIMAL(19,2),
    
    -- Reversal tracking fields
    original_transaction_id VARCHAR(36),
    reversal_transaction_id VARCHAR(36),
    reversed_at TIMESTAMP,
    reversed_by VARCHAR(255),
    reversal_reason VARCHAR(500),
    
    -- Constraints
    CONSTRAINT chk_transaction_type CHECK (type IN ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL', 'FEE', 'INTEREST', 'REVERSAL', 'REFUND')),
    CONSTRAINT chk_transaction_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED', 'CANCELLED')),
    CONSTRAINT chk_currency_code CHECK (LENGTH(currency) = 3),
    
    -- Foreign key constraints for reversal tracking
    CONSTRAINT fk_original_transaction 
        FOREIGN KEY (original_transaction_id) REFERENCES transactions(transaction_id),
    CONSTRAINT fk_reversal_transaction 
        FOREIGN KEY (reversal_transaction_id) REFERENCES transactions(transaction_id)
);

-- ================================================
-- Create indexes for performance optimization
-- ================================================

-- Primary indexes for transaction queries
CREATE INDEX IF NOT EXISTS idx_transactions_from_account 
    ON transactions(from_account_id);

CREATE INDEX IF NOT EXISTS idx_transactions_to_account 
    ON transactions(to_account_id);

CREATE INDEX IF NOT EXISTS idx_transactions_created_at 
    ON transactions(created_at);

CREATE INDEX IF NOT EXISTS idx_transactions_status 
    ON transactions(status);

CREATE INDEX IF NOT EXISTS idx_transactions_type 
    ON transactions(type);

CREATE INDEX IF NOT EXISTS idx_transactions_created_by 
    ON transactions(created_by);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_transactions_account_date 
    ON transactions(from_account_id, created_at);

CREATE INDEX IF NOT EXISTS idx_transactions_user_date 
    ON transactions(created_by, created_at);

CREATE INDEX IF NOT EXISTS idx_transactions_status_date 
    ON transactions(status, created_at);

CREATE INDEX IF NOT EXISTS idx_transactions_type_date 
    ON transactions(type, created_at);

-- Indexes for reversal tracking
CREATE INDEX IF NOT EXISTS idx_transactions_original_transaction 
    ON transactions(original_transaction_id) WHERE original_transaction_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_reversal_transaction 
    ON transactions(reversal_transaction_id) WHERE reversal_transaction_id IS NOT NULL;

-- Partial indexes for active transactions
CREATE INDEX IF NOT EXISTS idx_transactions_active_pending 
    ON transactions(created_at, from_account_id) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_transactions_active_processing 
    ON transactions(created_at, from_account_id) WHERE status = 'PROCESSING';

-- Index for transaction limits
CREATE INDEX IF NOT EXISTS idx_transaction_limits_account_type 
    ON transaction_limits(account_type);

CREATE INDEX IF NOT EXISTS idx_transaction_limits_active 
    ON transaction_limits(active) WHERE active = true;

-- ================================================
-- Add comments for documentation
-- ================================================
COMMENT ON TABLE transactions IS 'Core transaction table storing all financial transactions';
COMMENT ON TABLE transaction_limits IS 'Configuration table for transaction limits by account type';

COMMENT ON COLUMN transactions.transaction_id IS 'Unique identifier for the transaction (UUID)';
COMMENT ON COLUMN transactions.from_account_id IS 'Source account identifier';
COMMENT ON COLUMN transactions.to_account_id IS 'Destination account identifier';
COMMENT ON COLUMN transactions.amount IS 'Transaction amount (must be positive)';
COMMENT ON COLUMN transactions.currency IS 'ISO 4217 currency code (3 characters)';
COMMENT ON COLUMN transactions.type IS 'Type of transaction (TRANSFER, DEPOSIT, WITHDRAWAL, etc.)';
COMMENT ON COLUMN transactions.status IS 'Current status of the transaction';
COMMENT ON COLUMN transactions.original_transaction_id IS 'Reference to original transaction for reversals';
COMMENT ON COLUMN transactions.reversal_transaction_id IS 'Reference to reversal transaction';

COMMENT ON COLUMN transaction_limits.account_type IS 'Type of account (CHECKING, SAVINGS, CREDIT)';
COMMENT ON COLUMN transaction_limits.transaction_type IS 'Type of transaction this limit applies to';
COMMENT ON COLUMN transaction_limits.daily_limit IS 'Maximum daily transaction amount';
COMMENT ON COLUMN transaction_limits.monthly_limit IS 'Maximum monthly transaction amount';
COMMENT ON COLUMN transaction_limits.per_transaction_limit IS 'Maximum amount per single transaction';