-- Add idempotency and processing state support for transactions

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS processing_state VARCHAR(64);

UPDATE transactions
SET processing_state = CASE
    WHEN status = 'COMPLETED' THEN 'COMPLETED'
    ELSE 'INITIATED'
END
WHERE processing_state IS NULL;

ALTER TABLE transactions
    ALTER COLUMN processing_state SET DEFAULT 'INITIATED';

ALTER TABLE transactions
    ALTER COLUMN processing_state SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_transaction_status'
          AND conrelid = 'transactions'::regclass
    ) THEN
        ALTER TABLE transactions DROP CONSTRAINT chk_transaction_status;
    END IF;
END $$;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_status
        CHECK (status IN (
            'PENDING',
            'PROCESSING',
            'COMPLETED',
            'FAILED',
            'FAILED_REQUIRES_MANUAL_ACTION',
            'REVERSED',
            'CANCELLED'
        ));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_transaction_processing_state'
          AND conrelid = 'transactions'::regclass
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT chk_transaction_processing_state
                CHECK (processing_state IN (
                    'INITIATED',
                    'DEBIT_APPLIED',
                    'CREDIT_APPLIED',
                    'COMPLETED',
                    'COMPENSATED',
                    'MANUAL_ACTION_REQUIRED'
                ));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_transactions_idempotency_scope
    ON transactions(created_by, type, idempotency_key);
