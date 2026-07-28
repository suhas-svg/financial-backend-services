ALTER TABLE spending_limit_reservations
    ADD COLUMN IF NOT EXISTS owner_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS request_scope VARCHAR(256),
    ADD COLUMN IF NOT EXISTS transaction_correlation VARCHAR(160),
    ADD COLUMN IF NOT EXISTS state VARCHAR(40) NOT NULL DEFAULT 'RESERVED',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS outcome VARCHAR(1000),
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

WITH unambiguous AS (
    SELECT account_id, idempotency_key, MIN(reservation_id) AS reservation_id
    FROM spending_limit_reservations
    GROUP BY account_id, idempotency_key
    HAVING COUNT(*) = 1
)
UPDATE spending_limit_reservations r
SET request_scope = r.account_id::text || '|' || r.idempotency_key
FROM unambiguous u
WHERE r.reservation_id = u.reservation_id
  AND r.request_scope IS NULL;

WITH ambiguous AS (
    SELECT account_id, idempotency_key
    FROM spending_limit_reservations
    GROUP BY account_id, idempotency_key
    HAVING COUNT(*) > 1
)
UPDATE spending_limit_reservations r
SET state = 'RECONCILIATION_REQUIRED',
    outcome = COALESCE(r.outcome, 'AMBIGUOUS_LEGACY_IDEMPOTENCY_SCOPE'),
    outcome_at = COALESCE(r.outcome_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(r.updated_at, CURRENT_TIMESTAMP)
FROM ambiguous a
WHERE r.account_id = a.account_id
  AND r.idempotency_key = a.idempotency_key;

CREATE INDEX IF NOT EXISTS idx_limit_reservation_key
    ON spending_limit_reservations(account_id, idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_limit_reservation_request_scope
    ON spending_limit_reservations(request_scope);
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
