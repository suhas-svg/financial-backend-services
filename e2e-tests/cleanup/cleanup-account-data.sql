-- E2E Test Data Cleanup for Account Service
-- This file removes test data after E2E testing

-- Disable foreign key checks temporarily (if supported)
SET session_replication_role = replica;

-- Delete test account limits
DELETE FROM account_limits WHERE account_id IN (
    SELECT id FROM accounts WHERE account_number LIKE 'ACC-E2E-%'
);

-- Delete test accounts
DELETE FROM accounts WHERE account_number LIKE 'ACC-E2E-%';
DELETE FROM accounts WHERE owner_id IN (
    SELECT id FROM users WHERE username LIKE 'e2e_user_%'
);

-- Delete test users
DELETE FROM users WHERE username LIKE 'e2e_user_%';
DELETE FROM users WHERE email LIKE '%@test.com';

-- Delete any test sessions or tokens
DELETE FROM user_sessions WHERE user_id IN (
    SELECT id FROM users WHERE username LIKE 'e2e_user_%'
);

-- Delete any test audit logs
DELETE FROM audit_logs WHERE user_id IN (
    SELECT id FROM users WHERE username LIKE 'e2e_user_%'
);

-- Re-enable foreign key checks
SET session_replication_role = DEFAULT;

-- Reset sequences if needed
-- Note: This is optional and depends on whether you want to reset auto-increment values
-- SELECT setval('users_id_seq', COALESCE((SELECT MAX(id::bigint) FROM users WHERE id ~ '^[0-9]+$'), 1), false);
-- SELECT setval('accounts_id_seq', COALESCE((SELECT MAX(id::bigint) FROM accounts WHERE id ~ '^[0-9]+$'), 1), false);

-- Vacuum tables to reclaim space
VACUUM ANALYZE users;
VACUUM ANALYZE accounts;
VACUUM ANALYZE account_limits;