-- Index the two ledger account lookups on the request hot path.
--
-- findByExternalAccountId runs for every money movement (AccountLedgerResolver),
-- statement, closure and Balance Shield check. The only index on
-- external_account_id is the partial unique index restricted to CUSTOMER rows,
-- and PostgreSQL cannot use a partial index for a query that does not repeat its
-- predicate, so every lookup was a sequential scan.
--
-- The customer ledger listing (GET /api/ledger/accounts) filters by owner and
-- kind and orders by external id. The UI polls it every few seconds on the
-- dashboard, accounts and move-money pages, so it should be a single index range
-- scan rather than a scan of every customer ledger account.
CREATE INDEX IF NOT EXISTS idx_ledger_accounts_external_account_id
    ON ledger_accounts (external_account_id);

CREATE INDEX IF NOT EXISTS idx_ledger_accounts_owner_kind_external
    ON ledger_accounts (owner_id, account_kind, external_account_id);
