WITH required_system_accounts(account_kind, currency) AS (
    VALUES
        ('CLEARING', 'USD'), ('SUSPENSE', 'USD'), ('FEE', 'USD'),
        ('CLEARING', 'EUR'), ('SUSPENSE', 'EUR'), ('FEE', 'EUR'),
        ('CLEARING', 'GBP'), ('SUSPENSE', 'GBP'), ('FEE', 'GBP'),
        ('CLEARING', 'INR'), ('SUSPENSE', 'INR'), ('FEE', 'INR')
)
INSERT INTO ledger_accounts (
    ledger_account_id,
    account_kind,
    currency,
    external_account_id,
    owner_id,
    status,
    created_at,
    version
)
SELECT
    md5('ledger-system:' || account_kind || ':' || currency)::uuid,
    account_kind,
    currency,
    NULL,
    NULL,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    0
FROM required_system_accounts
ON CONFLICT DO NOTHING;

INSERT INTO ledger_balance_projections (
    ledger_account_id,
    posted_balance,
    pending_balance,
    pending_debits,
    pending_credits,
    available_balance,
    last_event_sequence,
    projection_version,
    updated_at,
    opening_balance
)
SELECT
    account.ledger_account_id,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    CURRENT_TIMESTAMP,
    0
FROM ledger_accounts account
WHERE account.account_kind IN ('CLEARING', 'SUSPENSE', 'FEE')
  AND account.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM ledger_balance_projections projection
      WHERE projection.ledger_account_id = account.ledger_account_id
  );
