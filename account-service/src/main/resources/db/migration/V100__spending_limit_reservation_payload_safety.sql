ALTER TABLE spending_limit_reservations
    ADD COLUMN IF NOT EXISTS owner_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS transaction_correlation VARCHAR(160),
    ADD COLUMN IF NOT EXISTS state VARCHAR(40) NOT NULL DEFAULT 'RESERVED',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS outcome VARCHAR(120),
    ADD COLUMN IF NOT EXISTS outcome_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

UPDATE spending_limit_reservations r
SET owner_id = COALESCE(r.owner_id, a.owner_id),
    currency = COALESCE(r.currency, UPPER(a.currency)),
    updated_at = COALESCE(r.updated_at, r.created_at),
    expires_at = COALESCE(r.expires_at, r.created_at + INTERVAL '30 minutes')
FROM accounts a
WHERE a.id = r.account_id
  AND (r.owner_id IS NULL OR r.currency IS NULL OR r.updated_at IS NULL OR r.expires_at IS NULL);

CREATE INDEX IF NOT EXISTS idx_limit_reservation_key
    ON spending_limit_reservations(account_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_limit_reservation_correlation
    ON spending_limit_reservations(transaction_correlation);
CREATE INDEX IF NOT EXISTS idx_limit_reservation_reconciliation
    ON spending_limit_reservations(state, expires_at);

ALTER TABLE spending_limit_reservations
    DROP CONSTRAINT IF EXISTS chk_limit_reservation_state;
ALTER TABLE spending_limit_reservations
    ADD CONSTRAINT chk_limit_reservation_state CHECK (
        state IN ('RESERVED', 'CONSUMED', 'RELEASED', 'EXPIRED', 'RECONCILIATION_REQUIRED')
    );
