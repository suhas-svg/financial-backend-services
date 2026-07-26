-- Canonical request fingerprints make idempotent transaction replays payload-safe.
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(64);


CREATE INDEX IF NOT EXISTS idx_transactions_request_fingerprint
    ON transactions(created_by, type, idempotency_key, request_fingerprint);

COMMENT ON COLUMN transactions.request_fingerprint IS
    'Canonical payload fingerprint used to reject conflicting idempotent replays';
