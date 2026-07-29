CREATE INDEX IF NOT EXISTS idx_journal_effective_date_order
    ON journal_transactions (effective_date, created_at, journal_reference);
