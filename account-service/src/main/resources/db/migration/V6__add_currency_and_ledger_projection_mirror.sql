ALTER TABLE accounts
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';

ALTER TABLE accounts
    ADD COLUMN pending_balance NUMERIC(38,2) NOT NULL DEFAULT 0;

ALTER TABLE accounts
    ADD COLUMN ledger_projection_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE accounts
    ADD COLUMN ledger_projection_synced_at TIMESTAMP;

ALTER TABLE accounts
    ADD COLUMN ledger_projection_source_event_id VARCHAR(100);

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_currency
        CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3);

-- PostgreSQL-only currency immutability trigger
CREATE OR REPLACE FUNCTION prevent_account_currency_change()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.currency IS DISTINCT FROM OLD.currency THEN
        RAISE EXCEPTION 'Account currency is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_accounts_currency_immutable
    BEFORE UPDATE OF currency ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION prevent_account_currency_change();
