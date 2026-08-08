ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS chk_transaction_processing_state;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_processing_state
        CHECK (processing_state IN (
            'INITIATED',
            'AWAITING_AUTHORIZATION',
            'AUTHORIZED',
            'AUTHORIZATION_EXPIRED',
            'AUTHORIZATION_CANCELLED',
            'HOLD_PLACED',
            'HOLD_CAPTURED',
            'HOLD_RELEASED',
            'DEBIT_APPLIED',
            'CREDIT_APPLIED',
            'FAILED',
            'COMPLETED',
            'COMPENSATED',
            'MANUAL_ACTION_REQUIRED'
        ));
