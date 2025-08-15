-- E2E Test Data for Account Service
-- This file contains sample data for comprehensive E2E testing

-- Insert test users
INSERT INTO users (id, username, password_hash, email, created_at, updated_at) VALUES
('550e8400-e29b-41d4-a716-446655440001', 'e2e_user_1', '$2a$10$N9qo8uLOickgx2ZMRZoMye.Uo0fy7EjkC.nDRUU/E/vbukZTRc.S6', 'e2e_user_1@test.com', NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440002', 'e2e_user_2', '$2a$10$N9qo8uLOickgx2ZMRZoMye.Uo0fy7EjkC.nDRUU/E/vbukZTRc.S6', 'e2e_user_2@test.com', NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440003', 'e2e_user_3', '$2a$10$N9qo8uLOickgx2ZMRZoMye.Uo0fy7EjkC.nDRUU/E/vbukZTRc.S6', 'e2e_user_3@test.com', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Insert test accounts
INSERT INTO accounts (id, account_number, account_type, balance, owner_id, created_at, updated_at) VALUES
('660e8400-e29b-41d4-a716-446655440001', 'ACC-E2E-001', 'CHECKING', 1000.00, '550e8400-e29b-41d4-a716-446655440001', NOW(), NOW()),
('660e8400-e29b-41d4-a716-446655440002', 'ACC-E2E-002', 'SAVINGS', 5000.00, '550e8400-e29b-41d4-a716-446655440001', NOW(), NOW()),
('660e8400-e29b-41d4-a716-446655440003', 'ACC-E2E-003', 'CHECKING', 2500.00, '550e8400-e29b-41d4-a716-446655440002', NOW(), NOW()),
('660e8400-e29b-41d4-a716-446655440004', 'ACC-E2E-004', 'SAVINGS', 10000.00, '550e8400-e29b-41d4-a716-446655440002', NOW(), NOW()),
('660e8400-e29b-41d4-a716-446655440005', 'ACC-E2E-005', 'CHECKING', 500.00, '550e8400-e29b-41d4-a716-446655440003', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Insert test account limits (if table exists)
INSERT INTO account_limits (account_id, daily_withdrawal_limit, daily_transfer_limit, monthly_withdrawal_limit, monthly_transfer_limit, created_at, updated_at) VALUES
('660e8400-e29b-41d4-a716-446655440001', 1000.00, 5000.00, 30000.00, 150000.00, NOW(), NOW()),
('660e8400-e29b-41d4-a716-446655440002', 2000.00, 10000.00, 60000.00, 300000.00, NOW(), NOW()),
('660e8400-e29b-41d4-a716-446655440003', 1500.00, 7500.00, 45000.00, 225000.00, NOW(), NOW()),
('660e8400-e29b-41d4-a716-446655440004', 3000.00, 15000.00, 90000.00, 450000.00, NOW(), NOW()),
('660e8400-e29b-41d4-a716-446655440005', 500.00, 2500.00, 15000.00, 75000.00, NOW(), NOW())
ON CONFLICT (account_id) DO NOTHING;

-- Create indexes for better performance during testing
CREATE INDEX IF NOT EXISTS idx_accounts_owner_id ON accounts(owner_id);
CREATE INDEX IF NOT EXISTS idx_accounts_account_number ON accounts(account_number);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Update sequences to avoid conflicts
SELECT setval('users_id_seq', (SELECT MAX(id::bigint) FROM users WHERE id ~ '^[0-9]+$'), true);
SELECT setval('accounts_id_seq', (SELECT MAX(id::bigint) FROM accounts WHERE id ~ '^[0-9]+$'), true);