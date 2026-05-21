ALTER TABLE accounts
    ADD COLUMN ledger_balance NUMERIC(38,2);

ALTER TABLE accounts
    ADD COLUMN available_balance NUMERIC(38,2);

UPDATE accounts
SET ledger_balance = balance
WHERE ledger_balance IS NULL;

UPDATE accounts
SET available_balance = balance
WHERE available_balance IS NULL;

ALTER TABLE accounts
    ALTER COLUMN ledger_balance SET NOT NULL;

ALTER TABLE accounts
    ALTER COLUMN available_balance SET NOT NULL;

CREATE TABLE IF NOT EXISTS account_debit_holds (
    hold_id VARCHAR(100) PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    captured_at TIMESTAMP,
    released_at TIMESTAMP,
    captured_by_transaction_id VARCHAR(100),
    released_by_transaction_id VARCHAR(100),
    release_reason VARCHAR(255),
    CONSTRAINT chk_account_debit_holds_status
        CHECK (status IN ('PLACED', 'CAPTURED', 'RELEASED'))
);

CREATE INDEX IF NOT EXISTS idx_account_debit_holds_account_id
    ON account_debit_holds (account_id);

CREATE INDEX IF NOT EXISTS idx_account_debit_holds_transaction_id
    ON account_debit_holds (transaction_id);
