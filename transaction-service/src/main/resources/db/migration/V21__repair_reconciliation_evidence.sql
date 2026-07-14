ALTER TABLE ledger_balance_projections
    ADD COLUMN opening_balance NUMERIC(19, 2);

WITH latest_states AS (
    SELECT DISTINCT ON (journal_id) journal_id, state
    FROM journal_state_events
    ORDER BY journal_id, event_sequence DESC
), journal_movement AS (
    SELECT posting.ledger_account_id,
           SUM(CASE WHEN posting.direction = 'CREDIT' THEN posting.amount ELSE -posting.amount END) AS amount
    FROM journal_postings posting
    JOIN latest_states state ON state.journal_id = posting.journal_id
    WHERE state.state IN ('POSTED', 'REVERSED')
    GROUP BY posting.ledger_account_id
)
UPDATE ledger_balance_projections projection
SET opening_balance = projection.posted_balance - COALESCE(movement.amount, 0)
FROM journal_movement movement
WHERE movement.ledger_account_id = projection.ledger_account_id;

UPDATE ledger_balance_projections
SET opening_balance = posted_balance
WHERE opening_balance IS NULL;

ALTER TABLE ledger_balance_projections
    ALTER COLUMN opening_balance SET NOT NULL;

ALTER TABLE reconciliation_exceptions
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN expected_amount NUMERIC(19, 2),
    ADD COLUMN actual_amount NUMERIC(19, 2),
    ADD COLUMN delta_amount NUMERIC(19, 2);
