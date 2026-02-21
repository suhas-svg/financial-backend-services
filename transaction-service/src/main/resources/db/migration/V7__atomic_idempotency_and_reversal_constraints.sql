-- ============================================================================
-- V7: Atomic idempotency and reversal integrity constraints
-- ============================================================================
-- Fixes:
--   C1  — Idempotency check-then-insert is not atomic.
--          The V6 index on (created_by, type, idempotency_key) was a plain
--          non-unique index. Concurrent requests with the same idempotency
--          key could both pass the SELECT check and both insert, causing
--          double-debit / double-credit.
--   C1b — Reversal duplicate race.
--          No constraint prevented two concurrent reversals of the same
--          original transaction from both being committed to the DB.
-- ============================================================================

-- Step 1: Clean up V6's non-unique index if it exists (replaced by unique constraint below).
DROP INDEX IF EXISTS idx_transaction_idempotency;

-- Step 2: Enforce idempotency uniqueness atomically at the DB level.
--         The constraint covers (created_by, type, idempotency_key).
--         NULL idempotency_key rows are excluded — they represent non-idempotent ops.
--         DEFERRABLE INITIALLY IMMEDIATE means the check runs at statement time
--         (default), but can be deferred within a transaction if needed.
ALTER TABLE transactions
    ADD CONSTRAINT uk_transaction_idempotency_key
        UNIQUE (created_by, type, idempotency_key)
        DEFERRABLE INITIALLY IMMEDIATE;

-- Step 3: Enforce that only ONE successful reversal can ever exist for a given
--         original transaction. The partial index covers only non-failed reversals
--         so that failed reversal attempts do not block legitimate retry attempts.
CREATE UNIQUE INDEX IF NOT EXISTS uk_reversal_per_original_transaction
    ON transactions (original_transaction_id)
    WHERE type = 'REVERSAL'
      AND status NOT IN ('FAILED', 'FAILED_REQUIRES_MANUAL_ACTION');

-- Step 4: Add an index to accelerate the isTransactionReversed() query which
--         is called on every reversal attempt. Without this, it was a full scan.
CREATE INDEX IF NOT EXISTS idx_transactions_original_type_status
    ON transactions (original_transaction_id, type, status)
    WHERE original_transaction_id IS NOT NULL;
