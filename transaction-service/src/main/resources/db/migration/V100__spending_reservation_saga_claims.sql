CREATE TABLE IF NOT EXISTS transaction_idempotency_claims (
    claim_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3),
    state VARCHAR(40) NOT NULL DEFAULT 'CLAIMED',
    reservation_id BIGINT,
    reservation_correlation VARCHAR(160),
    reservation_fingerprint VARCHAR(64),
    reservation_amount NUMERIC(19,2),
    reservation_currency VARCHAR(3),
    reservation_state VARCHAR(40),
    transaction_id VARCHAR(36),
    failure_details VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_transaction_idempotency_claim UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_transaction_idempotency_claim_state CHECK (
        state IN ('CLAIMED', 'RESERVED', 'COMPLETED', 'RELEASED',
                  'RECONCILIATION_REQUIRED', 'CLOSED_NO_RESERVATION')
    )
);

CREATE INDEX IF NOT EXISTS idx_transaction_claim_reconciliation
    ON transaction_idempotency_claims(state, expires_at);
CREATE INDEX IF NOT EXISTS idx_transaction_claim_reservation
    ON transaction_idempotency_claims(reservation_id);
CREATE INDEX IF NOT EXISTS idx_transaction_claim_transaction
    ON transaction_idempotency_claims(transaction_id);
