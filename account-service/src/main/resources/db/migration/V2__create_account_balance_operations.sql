-- Idempotent balance operation table for internal account-service integrations.

CREATE TABLE IF NOT EXISTS account_balance_operations (
    account_id BIGINT NOT NULL,
    operation_id VARCHAR(100) NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    delta NUMERIC(19,2) NOT NULL,
    reason VARCHAR(255),
    allow_negative BOOLEAN NOT NULL,
    applied BOOLEAN NOT NULL,
    resulting_balance NUMERIC(19,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (account_id, operation_id),
    CONSTRAINT chk_account_balance_operations_status
        CHECK (status IN ('APPLIED', 'REJECTED', 'REPLAYED'))
);

CREATE INDEX IF NOT EXISTS idx_account_balance_operations_transaction_id
    ON account_balance_operations (transaction_id);
