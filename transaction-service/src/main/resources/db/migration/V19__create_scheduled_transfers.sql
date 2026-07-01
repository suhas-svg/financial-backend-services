CREATE TABLE scheduled_transfers (
    schedule_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    from_account_id VARCHAR(64) NOT NULL,
    to_account_id VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(500),
    reference VARCHAR(100),
    schedule_type VARCHAR(32) NOT NULL,
    frequency VARCHAR(32),
    next_run_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_scheduled_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_scheduled_transfer_currency_shape CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3),
    CONSTRAINT ck_scheduled_transfer_type CHECK (schedule_type IN ('ONE_TIME', 'RECURRING')),
    CONSTRAINT ck_scheduled_transfer_frequency CHECK (
        (schedule_type = 'ONE_TIME' AND frequency IS NULL)
        OR (schedule_type = 'RECURRING' AND frequency IS NOT NULL)
    ),
    CONSTRAINT ck_scheduled_transfer_frequency_value CHECK (
        frequency IS NULL OR frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY')
    ),
    CONSTRAINT ck_scheduled_transfer_status CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELED', 'COMPLETED'))
);

CREATE TABLE scheduled_transfer_runs (
    run_id VARCHAR(36) PRIMARY KEY,
    schedule_id VARCHAR(36) NOT NULL,
    scheduled_for TIMESTAMP NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(36),
    idempotency_key VARCHAR(160) NOT NULL,
    failure_reason VARCHAR(1000),
    CONSTRAINT ck_scheduled_transfer_run_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    CONSTRAINT fk_scheduled_transfer_runs_schedule
        FOREIGN KEY (schedule_id) REFERENCES scheduled_transfers(schedule_id),
    CONSTRAINT uq_scheduled_transfer_run UNIQUE (schedule_id, scheduled_for)
);

CREATE INDEX idx_scheduled_transfers_user_id ON scheduled_transfers(user_id);
CREATE INDEX idx_scheduled_transfers_status_next_run ON scheduled_transfers(status, next_run_at);
CREATE INDEX idx_scheduled_transfer_runs_schedule_id ON scheduled_transfer_runs(schedule_id);
CREATE INDEX idx_scheduled_transfer_runs_status ON scheduled_transfer_runs(status);
CREATE INDEX idx_scheduled_transfer_runs_transaction_id ON scheduled_transfer_runs(transaction_id);
