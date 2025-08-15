-- E2E Test Data for Transaction Service
-- This file contains sample data for comprehensive E2E testing

-- Insert test transaction types (if table exists)
INSERT INTO transaction_types (id, type_name, description, created_at) VALUES
(1, 'DEPOSIT', 'Deposit transaction', NOW()),
(2, 'WITHDRAWAL', 'Withdrawal transaction', NOW()),
(3, 'TRANSFER', 'Transfer between accounts', NOW()),
(4, 'FEE', 'Transaction fee', NOW()),
(5, 'INTEREST', 'Interest payment', NOW())
ON CONFLICT (id) DO NOTHING;

-- Insert test transactions
INSERT INTO transactions (id, transaction_id, account_id, amount, transaction_type, status, description, created_at, updated_at) VALUES
('770e8400-e29b-41d4-a716-446655440001', 'TXN-E2E-001', '660e8400-e29b-41d4-a716-446655440001', 500.00, 'DEPOSIT', 'COMPLETED', 'E2E Test Initial Deposit', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
('770e8400-e29b-41d4-a716-446655440002', 'TXN-E2E-002', '660e8400-e29b-41d4-a716-446655440002', 2000.00, 'DEPOSIT', 'COMPLETED', 'E2E Test Savings Deposit', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
('770e8400-e29b-41d4-a716-446655440003', 'TXN-E2E-003', '660e8400-e29b-41d4-a716-446655440001', -100.00, 'WITHDRAWAL', 'COMPLETED', 'E2E Test ATM Withdrawal', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
('770e8400-e29b-41d4-a716-446655440004', 'TXN-E2E-004', '660e8400-e29b-41d4-a716-446655440003', 1000.00, 'DEPOSIT', 'COMPLETED', 'E2E Test Payroll Deposit', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),
('770e8400-e29b-41d4-a716-446655440005', 'TXN-E2E-005', '660e8400-e29b-41d4-a716-446655440004', 5000.00, 'DEPOSIT', 'COMPLETED', 'E2E Test Large Deposit', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;

-- Insert test transfer transactions
INSERT INTO transfer_transactions (id, from_account_id, to_account_id, amount, status, description, created_at, updated_at) VALUES
('880e8400-e29b-41d4-a716-446655440001', '660e8400-e29b-41d4-a716-446655440002', '660e8400-e29b-41d4-a716-446655440001', 200.00, 'COMPLETED', 'E2E Test Transfer - Savings to Checking', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('880e8400-e29b-41d4-a716-446655440002', '660e8400-e29b-41d4-a716-446655440003', '660e8400-e29b-41d4-a716-446655440005', 300.00, 'COMPLETED', 'E2E Test Transfer - Between Users', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

-- Insert test transaction limits
INSERT INTO transaction_limits (user_id, daily_withdrawal_limit, daily_transfer_limit, monthly_withdrawal_limit, monthly_transfer_limit, created_at, updated_at) VALUES
('550e8400-e29b-41d4-a716-446655440001', 2000.00, 10000.00, 60000.00, 300000.00, NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440002', 2500.00, 12500.00, 75000.00, 375000.00, NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440003', 1000.00, 5000.00, 30000.00, 150000.00, NOW(), NOW())
ON CONFLICT (user_id) DO NOTHING;

-- Insert test audit logs
INSERT INTO audit_logs (id, entity_type, entity_id, action, old_values, new_values, user_id, created_at) VALUES
('990e8400-e29b-41d4-a716-446655440001', 'TRANSACTION', '770e8400-e29b-41d4-a716-446655440001', 'CREATE', '{}', '{"amount": 500.00, "type": "DEPOSIT", "status": "COMPLETED"}', '550e8400-e29b-41d4-a716-446655440001', NOW() - INTERVAL '7 days'),
('990e8400-e29b-41d4-a716-446655440002', 'TRANSACTION', '770e8400-e29b-41d4-a716-446655440002', 'CREATE', '{}', '{"amount": 2000.00, "type": "DEPOSIT", "status": "COMPLETED"}', '550e8400-e29b-41d4-a716-446655440001', NOW() - INTERVAL '6 days'),
('990e8400-e29b-41d4-a716-446655440003', 'TRANSFER', '880e8400-e29b-41d4-a716-446655440001', 'CREATE', '{}', '{"amount": 200.00, "from_account": "660e8400-e29b-41d4-a716-446655440002", "to_account": "660e8400-e29b-41d4-a716-446655440001"}', '550e8400-e29b-41d4-a716-446655440001', NOW() - INTERVAL '2 days')
ON CONFLICT (id) DO NOTHING;

-- Create indexes for better performance during testing
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_transaction_type ON transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_transfer_transactions_from_account ON transfer_transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transfer_transactions_to_account ON transfer_transactions(to_account_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type_id ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_transaction_limits_user_id ON transaction_limits(user_id);

-- Update sequences to avoid conflicts
SELECT setval('transactions_id_seq', (SELECT MAX(id::bigint) FROM transactions WHERE id ~ '^[0-9]+$'), true);
SELECT setval('transfer_transactions_id_seq', (SELECT MAX(id::bigint) FROM transfer_transactions WHERE id ~ '^[0-9]+$'), true);
SELECT setval('audit_logs_id_seq', (SELECT MAX(id::bigint) FROM audit_logs WHERE id ~ '^[0-9]+$'), true);