-- ================================================
-- Transaction Service Default Data Migration
-- Version: 3.0
-- Description: Insert default transaction limits for different account types
-- ================================================

-- ================================================
-- Insert default transaction limits
-- ================================================

-- Clear any existing data (for clean migration)
DELETE FROM transaction_limits WHERE id > 0;

-- Reset the sequence
ALTER SEQUENCE transaction_limits_id_seq RESTART WITH 1;

-- ================================================
-- CHECKING Account Limits
-- ================================================
INSERT INTO transaction_limits (
    account_type, 
    transaction_type, 
    daily_limit, 
    monthly_limit, 
    per_transaction_limit, 
    daily_count, 
    monthly_count, 
    active
) VALUES 
    -- Transfer limits for checking accounts
    ('CHECKING', 'TRANSFER', 5000.00, 50000.00, 2000.00, 20, 200, true),
    
    -- Withdrawal limits for checking accounts
    ('CHECKING', 'WITHDRAWAL', 1000.00, 10000.00, 500.00, 10, 100, true),
    
    -- Deposit limits for checking accounts (no daily/monthly limits, only per-transaction)
    ('CHECKING', 'DEPOSIT', NULL, NULL, 10000.00, NULL, NULL, true),
    
    -- Fee limits for checking accounts
    ('CHECKING', 'FEE', 100.00, 500.00, 50.00, 10, 50, true),
    
    -- Interest limits for checking accounts
    ('CHECKING', 'INTEREST', NULL, NULL, 1000.00, NULL, NULL, true),
    
    -- Reversal limits for checking accounts
    ('CHECKING', 'REVERSAL', NULL, NULL, NULL, NULL, NULL, true),
    
    -- Refund limits for checking accounts
    ('CHECKING', 'REFUND', NULL, NULL, 5000.00, NULL, NULL, true);

-- ================================================
-- SAVINGS Account Limits
-- ================================================
INSERT INTO transaction_limits (
    account_type, 
    transaction_type, 
    daily_limit, 
    monthly_limit, 
    per_transaction_limit, 
    daily_count, 
    monthly_count, 
    active
) VALUES 
    -- Transfer limits for savings accounts (more restrictive)
    ('SAVINGS', 'TRANSFER', 2000.00, 20000.00, 1000.00, 10, 100, true),
    
    -- Withdrawal limits for savings accounts (federal regulation compliance)
    ('SAVINGS', 'WITHDRAWAL', 500.00, 5000.00, 300.00, 6, 60, true),
    
    -- Deposit limits for savings accounts
    ('SAVINGS', 'DEPOSIT', NULL, NULL, 10000.00, NULL, NULL, true),
    
    -- Fee limits for savings accounts
    ('SAVINGS', 'FEE', 50.00, 250.00, 25.00, 5, 25, true),
    
    -- Interest limits for savings accounts (higher interest payments)
    ('SAVINGS', 'INTEREST', NULL, NULL, 2000.00, NULL, NULL, true),
    
    -- Reversal limits for savings accounts
    ('SAVINGS', 'REVERSAL', NULL, NULL, NULL, NULL, NULL, true),
    
    -- Refund limits for savings accounts
    ('SAVINGS', 'REFUND', NULL, NULL, 2000.00, NULL, NULL, true);

-- ================================================
-- CREDIT Account Limits
-- ================================================
INSERT INTO transaction_limits (
    account_type, 
    transaction_type, 
    daily_limit, 
    monthly_limit, 
    per_transaction_limit, 
    daily_count, 
    monthly_count, 
    active
) VALUES 
    -- Transfer limits for credit accounts (higher limits)
    ('CREDIT', 'TRANSFER', 10000.00, 100000.00, 5000.00, 50, 500, true),
    
    -- Withdrawal limits for credit accounts (cash advances)
    ('CREDIT', 'WITHDRAWAL', 2000.00, 20000.00, 1000.00, 20, 200, true),
    
    -- Deposit limits for credit accounts (payments)
    ('CREDIT', 'DEPOSIT', NULL, NULL, 25000.00, NULL, NULL, true),
    
    -- Fee limits for credit accounts
    ('CREDIT', 'FEE', 200.00, 1000.00, 100.00, 20, 100, true),
    
    -- Interest limits for credit accounts (interest charges)
    ('CREDIT', 'INTEREST', NULL, NULL, 5000.00, NULL, NULL, true),
    
    -- Reversal limits for credit accounts
    ('CREDIT', 'REVERSAL', NULL, NULL, NULL, NULL, NULL, true),
    
    -- Refund limits for credit accounts
    ('CREDIT', 'REFUND', NULL, NULL, 10000.00, NULL, NULL, true);

-- ================================================
-- BUSINESS Account Limits (Higher limits for business accounts)
-- ================================================
INSERT INTO transaction_limits (
    account_type, 
    transaction_type, 
    daily_limit, 
    monthly_limit, 
    per_transaction_limit, 
    daily_count, 
    monthly_count, 
    active
) VALUES 
    -- Transfer limits for business accounts
    ('BUSINESS', 'TRANSFER', 25000.00, 500000.00, 10000.00, 100, 1000, true),
    
    -- Withdrawal limits for business accounts
    ('BUSINESS', 'WITHDRAWAL', 10000.00, 100000.00, 5000.00, 50, 500, true),
    
    -- Deposit limits for business accounts
    ('BUSINESS', 'DEPOSIT', NULL, NULL, 50000.00, NULL, NULL, true),
    
    -- Fee limits for business accounts
    ('BUSINESS', 'FEE', 500.00, 2500.00, 250.00, 50, 250, true),
    
    -- Interest limits for business accounts
    ('BUSINESS', 'INTEREST', NULL, NULL, 10000.00, NULL, NULL, true),
    
    -- Reversal limits for business accounts
    ('BUSINESS', 'REVERSAL', NULL, NULL, NULL, NULL, NULL, true),
    
    -- Refund limits for business accounts
    ('BUSINESS', 'REFUND', NULL, NULL, 25000.00, NULL, NULL, true);

-- ================================================
-- PREMIUM Account Limits (VIP customers with higher limits)
-- ================================================
INSERT INTO transaction_limits (
    account_type, 
    transaction_type, 
    daily_limit, 
    monthly_limit, 
    per_transaction_limit, 
    daily_count, 
    monthly_count, 
    active
) VALUES 
    -- Transfer limits for premium accounts
    ('PREMIUM', 'TRANSFER', 50000.00, 1000000.00, 25000.00, 200, 2000, true),
    
    -- Withdrawal limits for premium accounts
    ('PREMIUM', 'WITHDRAWAL', 20000.00, 200000.00, 10000.00, 100, 1000, true),
    
    -- Deposit limits for premium accounts
    ('PREMIUM', 'DEPOSIT', NULL, NULL, 100000.00, NULL, NULL, true),
    
    -- Fee limits for premium accounts (reduced fees)
    ('PREMIUM', 'FEE', 100.00, 500.00, 50.00, 20, 100, true),
    
    -- Interest limits for premium accounts
    ('PREMIUM', 'INTEREST', NULL, NULL, 25000.00, NULL, NULL, true),
    
    -- Reversal limits for premium accounts
    ('PREMIUM', 'REVERSAL', NULL, NULL, NULL, NULL, NULL, true),
    
    -- Refund limits for premium accounts
    ('PREMIUM', 'REFUND', NULL, NULL, 50000.00, NULL, NULL, true);

-- ================================================
-- Add audit information
-- ================================================
UPDATE transaction_limits SET 
    created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

-- ================================================
-- Verify data insertion
-- ================================================
DO $$
DECLARE
    limit_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO limit_count FROM transaction_limits WHERE active = true;
    
    IF limit_count < 35 THEN
        RAISE EXCEPTION 'Transaction limits data insertion failed. Expected at least 35 records, found %', limit_count;
    ELSE
        RAISE NOTICE 'Successfully inserted % transaction limit records', limit_count;
    END IF;
END $$;

-- ================================================
-- Create summary of inserted limits
-- ================================================
SELECT 
    account_type,
    COUNT(*) as limit_count,
    COUNT(*) FILTER (WHERE active = true) as active_limits
FROM transaction_limits 
GROUP BY account_type 
ORDER BY account_type;

COMMENT ON TABLE transaction_limits IS 'Default transaction limits have been populated for CHECKING, SAVINGS, CREDIT, BUSINESS, and PREMIUM account types';