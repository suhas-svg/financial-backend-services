CREATE TABLE account_spending_limits (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    transfer_daily_limit NUMERIC(19,2) NOT NULL DEFAULT 10000.00 CHECK (transfer_daily_limit >= 0),
    withdrawal_daily_limit NUMERIC(19,2) NOT NULL DEFAULT 2000.00 CHECK (withdrawal_daily_limit >= 0),
    pending_transfer_daily_limit NUMERIC(19,2),
    pending_withdrawal_daily_limit NUMERIC(19,2),
    pending_effective_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE spending_limit_reservations (
    reservation_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    operation_type VARCHAR(20) NOT NULL CHECK (operation_type IN ('TRANSFER','WITHDRAWAL')),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    usage_date DATE NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_limit_reservation UNIQUE (account_id, operation_type, idempotency_key)
);
CREATE INDEX idx_limit_usage ON spending_limit_reservations(account_id, operation_type, usage_date);

CREATE TABLE spending_limit_audit_events (
    event_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    user_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    operation_type VARCHAR(20),
    amount NUMERIC(19,2),
    daily_limit NUMERIC(19,2),
    daily_used NUMERIC(19,2),
    details VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_limit_audit_created ON spending_limit_audit_events(created_at DESC);
