ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS journal_id UUID;

CREATE INDEX IF NOT EXISTS idx_transactions_journal_id
    ON transactions(journal_id)
    WHERE journal_id IS NOT NULL;
