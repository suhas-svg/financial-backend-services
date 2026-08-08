ALTER TABLE transaction_disputes
    ADD COLUMN IF NOT EXISTS reimbursement_transaction_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS reimbursement_amount NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS reimbursement_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS reimbursed_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uq_dispute_reimbursement_transaction
    ON transaction_disputes (reimbursement_transaction_id)
    WHERE reimbursement_transaction_id IS NOT NULL;

COMMENT ON COLUMN transaction_disputes.reimbursement_transaction_id IS
    'Explicit operator-approved refund transaction; approval alone never credits the customer';
