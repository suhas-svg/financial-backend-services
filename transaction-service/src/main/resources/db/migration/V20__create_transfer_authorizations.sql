CREATE TABLE transfer_authorizations (
    authorization_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    action_fingerprint VARCHAR(64) NOT NULL,
    challenge_id VARCHAR(36) NOT NULL,
    from_account_id VARCHAR(64) NOT NULL,
    to_account_id VARCHAR(64) NOT NULL,
    beneficiary_id VARCHAR(36),
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(500),
    reference VARCHAR(100),
    reason_codes VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    authorized_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    executed_transaction_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_transfer_authorization_idempotency UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_transfer_authorization_status_expiry ON transfer_authorizations(status, expires_at);
