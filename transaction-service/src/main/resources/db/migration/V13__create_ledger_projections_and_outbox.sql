CREATE TABLE ledger_balance_projections (
    ledger_account_id UUID PRIMARY KEY,
    posted_balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    pending_balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    pending_debits NUMERIC(19,2) NOT NULL DEFAULT 0,
    pending_credits NUMERIC(19,2) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    last_event_sequence BIGINT NOT NULL DEFAULT 0,
    projection_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_projection_ledger_account
        FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (ledger_account_id),
    CONSTRAINT chk_projection_pending_debits CHECK (pending_debits >= 0),
    CONSTRAINT chk_projection_pending_credits CHECK (pending_credits >= 0)
);

CREATE TABLE ledger_projection_outbox (
    outbox_id UUID PRIMARY KEY,
    ledger_account_id UUID NOT NULL,
    external_account_id VARCHAR(255) NOT NULL,
    source_event_id UUID NOT NULL UNIQUE,
    projection_version BIGINT NOT NULL,
    posted_balance NUMERIC(19,2) NOT NULL,
    pending_balance NUMERIC(19,2) NOT NULL,
    available_balance NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP,
    last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_outbox_ledger_account
        FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (ledger_account_id),
    CONSTRAINT chk_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_outbox_currency CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3),
    CONSTRAINT uk_outbox_projection_version UNIQUE (ledger_account_id, projection_version)
);

CREATE INDEX idx_projection_outbox_due
    ON ledger_projection_outbox (next_attempt_at, created_at)
    WHERE delivered_at IS NULL;
