-- E2E Test Data Cleanup for Transaction Service
-- This file removes test data after E2E testing

-- Disable foreign key checks temporarily (if supported)
SET session_replication_role = replica;

-- Delete test audit logs
DELETE FROM audit_logs WHERE entity_id IN (
    SELECT id FROM transactions WHERE transaction_id LIKE 'TXN-E2E-%'
);
DELETE FROM audit_logs WHERE entity_id IN (
    SELECT id FROM transfer_transactions WHERE id LIKE '880e8400-e29b-41d4-a716-44665544%'
);

-- Delete test transfer transactions
DELETE FROM transfer_transactions WHERE id LIKE '880e8400-e29b-41d4-a716-44665544%';
DELETE FROM transfer_transactions WHERE from_account_id LIKE '660e8400-e29b-41d4-a716-44665544%';
DELETE FROM transfer_transactions WHERE to_account_id LIKE '660e8400-e29b-41d4-a716-44665544%';

-- Delete test transactions
DELETE FROM transactions WHERE transaction_id LIKE 'TXN-E2E-%';
DELETE FROM transactions WHERE account_id LIKE '660e8400-e29b-41d4-a716-44665544%';

-- Delete test transaction limits
DELETE FROM transaction_limits WHERE user_id LIKE '550e8400-e29b-41d4-a716-44665544%';

-- Delete test transaction statistics (if table exists)
DELETE FROM transaction_statistics WHERE user_id LIKE '550e8400-e29b-41d4-a716-44665544%';

-- Delete test notification logs (if table exists)
DELETE FROM notification_logs WHERE user_id LIKE '550e8400-e29b-41d4-a716-44665544%';

-- Re-enable foreign key checks
SET session_replication_role = DEFAULT;

-- Reset sequences if needed
-- Note: This is optional and depends on whether you want to reset auto-increment values
-- SELECT setval('transactions_id_seq', COALESCE((SELECT MAX(id::bigint) FROM transactions WHERE id ~ '^[0-9]+$'), 1), false);
-- SELECT setval('transfer_transactions_id_seq', COALESCE((SELECT MAX(id::bigint) FROM transfer_transactions WHERE id ~ '^[0-9]+$'), 1), false);
-- SELECT setval('audit_logs_id_seq', COALESCE((SELECT MAX(id::bigint) FROM audit_logs WHERE id ~ '^[0-9]+$'), 1), false);

-- Vacuum tables to reclaim space
VACUUM ANALYZE transactions;
VACUUM ANALYZE transfer_transactions;
VACUUM ANALYZE transaction_limits;
VACUUM ANALYZE audit_logs;