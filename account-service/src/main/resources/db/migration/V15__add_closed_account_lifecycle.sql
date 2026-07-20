-- Account rows and financial evidence are retained permanently. CLOSED is a
-- terminal lifecycle state; closure preconditions are enforced by services.
CREATE INDEX IF NOT EXISTS idx_accounts_closed
    ON accounts(status)
    WHERE status = 'CLOSED';
