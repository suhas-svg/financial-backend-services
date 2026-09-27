-- Durability backstop for the daily ledger reconciliation run.
--
-- The application now takes a transaction-scoped advisory lock
-- (pg_try_advisory_xact_lock) before starting a daily run, which prevents two
-- concurrent reconciliations of the same business date. An advisory lock is
-- released implicitly when its transaction ends, so it cannot record "a run was
-- started" on its own.
--
-- This partial unique index makes the invariant visible in the schema: at most
-- one IN_PROGRESS run may exist per (business_date, reconciliation_type). A
-- completed or failed run does not block a later re-run, so this constrains only
-- the window in which money is being reconciled.
CREATE UNIQUE INDEX IF NOT EXISTS uk_reconciliation_run_in_progress
    ON reconciliation_runs (business_date, reconciliation_type)
    WHERE status = 'RUNNING';

COMMENT ON INDEX uk_reconciliation_run_in_progress IS
    'At most one in-progress reconciliation run per business date and type; complements the pg_try_advisory_xact_lock guard.';
