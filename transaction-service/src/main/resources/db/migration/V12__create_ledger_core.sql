CREATE TABLE ledger_accounts (
    ledger_account_id UUID PRIMARY KEY,
    account_kind VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    external_account_id VARCHAR(255),
    owner_id VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ledger_account_kind
        CHECK (account_kind IN ('CUSTOMER', 'CLEARING', 'SUSPENSE', 'FEE')),
    CONSTRAINT chk_ledger_account_currency
        CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3),
    CONSTRAINT chk_ledger_account_status
        CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_customer_ledger_identity
        CHECK ((account_kind = 'CUSTOMER' AND external_account_id IS NOT NULL AND owner_id IS NOT NULL)
            OR (account_kind <> 'CUSTOMER' AND external_account_id IS NULL))
);

CREATE UNIQUE INDEX uk_customer_ledger_external_account
    ON ledger_accounts (external_account_id)
    WHERE account_kind = 'CUSTOMER';

CREATE UNIQUE INDEX uk_system_ledger_kind_currency
    ON ledger_accounts (account_kind, currency)
    WHERE account_kind <> 'CUSTOMER' AND status = 'ACTIVE';

CREATE TABLE journal_transactions (
    journal_id UUID PRIMARY KEY,
    journal_reference VARCHAR(100) NOT NULL UNIQUE,
    journal_type VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    effective_date DATE NOT NULL,
    description VARCHAR(500),
    correlation_id VARCHAR(100) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_scope VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    reversal_of_journal_id UUID,
    CONSTRAINT chk_journal_type CHECK (journal_type IN (
        'DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'FEE', 'REVERSAL', 'CORRECTION', 'OPENING_BALANCE')),
    CONSTRAINT chk_journal_currency CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3),
    CONSTRAINT fk_journal_reversal_original
        FOREIGN KEY (reversal_of_journal_id) REFERENCES journal_transactions (journal_id),
    CONSTRAINT chk_journal_not_self_reversal
        CHECK (reversal_of_journal_id IS NULL OR reversal_of_journal_id <> journal_id),
    CONSTRAINT uk_journal_idempotency UNIQUE (idempotency_scope, idempotency_key)
);

CREATE UNIQUE INDEX uk_journal_reversal
    ON journal_transactions (reversal_of_journal_id)
    WHERE reversal_of_journal_id IS NOT NULL;

CREATE INDEX idx_journal_created_at ON journal_transactions (created_at);
CREATE INDEX idx_journal_correlation ON journal_transactions (correlation_id);

CREATE TABLE journal_postings (
    posting_id UUID PRIMARY KEY,
    journal_id UUID NOT NULL,
    ledger_account_id UUID NOT NULL,
    posting_sequence INTEGER NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    memo VARCHAR(500),
    CONSTRAINT fk_posting_journal
        FOREIGN KEY (journal_id) REFERENCES journal_transactions (journal_id),
    CONSTRAINT fk_posting_ledger_account
        FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (ledger_account_id),
    CONSTRAINT chk_posting_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_posting_amount CHECK (amount > 0),
    CONSTRAINT chk_posting_currency CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3),
    CONSTRAINT uk_posting_sequence UNIQUE (journal_id, posting_sequence)
);

CREATE INDEX idx_postings_account_journal
    ON journal_postings (ledger_account_id, journal_id);

CREATE TABLE journal_state_events (
    event_id UUID PRIMARY KEY,
    journal_id UUID NOT NULL,
    event_sequence INTEGER NOT NULL,
    state VARCHAR(16) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_state_event_journal
        FOREIGN KEY (journal_id) REFERENCES journal_transactions (journal_id),
    CONSTRAINT chk_journal_state CHECK (state IN ('PENDING', 'POSTED', 'FAILED', 'REVERSED')),
    CONSTRAINT uk_journal_state_sequence UNIQUE (journal_id, event_sequence)
);

CREATE INDEX idx_journal_state_latest
    ON journal_state_events (journal_id, event_sequence DESC);

CREATE TABLE journal_links (
    journal_id UUID NOT NULL,
    link_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (journal_id, link_type, external_id),
    CONSTRAINT fk_journal_link_journal
        FOREIGN KEY (journal_id) REFERENCES journal_transactions (journal_id),
    CONSTRAINT chk_journal_link_type CHECK (link_type IN (
        'TRANSACTION', 'DISPUTE', 'RISK_CASE', 'AUDIT_EVENT', 'REVERSAL', 'RECONCILIATION_EXCEPTION'))
);

CREATE INDEX idx_journal_links_external
    ON journal_links (link_type, external_id);

CREATE TABLE ledger_idempotency_claims (
    idempotency_scope VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    journal_id UUID,
    outcome VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    PRIMARY KEY (idempotency_scope, idempotency_key),
    CONSTRAINT fk_idempotency_journal
        FOREIGN KEY (journal_id) REFERENCES journal_transactions (journal_id),
    CONSTRAINT chk_idempotency_outcome
        CHECK (outcome IN ('CLAIMED', 'PENDING', 'POSTED', 'FAILED', 'REVERSED'))
);

CREATE OR REPLACE FUNCTION prevent_immutable_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger history is immutable: % on % is prohibited', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_transactions_immutable
    BEFORE UPDATE OR DELETE ON journal_transactions
    FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ledger_mutation();

CREATE TRIGGER trg_journal_postings_immutable
    BEFORE UPDATE OR DELETE ON journal_postings
    FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ledger_mutation();

CREATE TRIGGER trg_journal_state_events_immutable
    BEFORE UPDATE OR DELETE ON journal_state_events
    FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ledger_mutation();

CREATE OR REPLACE FUNCTION validate_journal_state_transition()
RETURNS TRIGGER AS $$
DECLARE
    previous_state VARCHAR(16);
    previous_sequence INTEGER;
BEGIN
    SELECT state, event_sequence
      INTO previous_state, previous_sequence
      FROM journal_state_events
     WHERE journal_id = NEW.journal_id
     ORDER BY event_sequence DESC
     LIMIT 1;

    IF previous_sequence IS NULL THEN
        IF NEW.event_sequence <> 1 OR NEW.state <> 'PENDING' THEN
            RAISE EXCEPTION 'Journal lifecycle must start with PENDING sequence 1';
        END IF;
    ELSE
        IF NEW.event_sequence <> previous_sequence + 1 THEN
            RAISE EXCEPTION 'Journal state sequence must be contiguous';
        END IF;
        IF NOT ((previous_state = 'PENDING' AND NEW.state IN ('POSTED', 'FAILED'))
             OR (previous_state = 'POSTED' AND NEW.state = 'REVERSED')) THEN
            RAISE EXCEPTION 'Illegal journal state transition from % to %', previous_state, NEW.state;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_journal_state_transition
    BEFORE INSERT ON journal_state_events
    FOR EACH ROW EXECUTE FUNCTION validate_journal_state_transition();

CREATE OR REPLACE FUNCTION validate_journal_integrity()
RETURNS TRIGGER AS $$
DECLARE
    target_journal_id UUID;
    journal_currency CHAR(3);
    posting_count INTEGER;
    currency_mismatch_count INTEGER;
    debit_total NUMERIC(19,2);
    credit_total NUMERIC(19,2);
BEGIN
    target_journal_id := COALESCE(NEW.journal_id, OLD.journal_id);
    SELECT currency INTO journal_currency
      FROM journal_transactions
     WHERE journal_id = target_journal_id;

    IF journal_currency IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(*),
           COUNT(*) FILTER (WHERE currency <> journal_currency),
           COALESCE(SUM(amount) FILTER (WHERE direction = 'DEBIT'), 0),
           COALESCE(SUM(amount) FILTER (WHERE direction = 'CREDIT'), 0)
      INTO posting_count, currency_mismatch_count, debit_total, credit_total
      FROM journal_postings
     WHERE journal_id = target_journal_id;

    IF posting_count < 2 THEN
        RAISE EXCEPTION 'Journal % must contain at least two postings', target_journal_id;
    END IF;
    IF currency_mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Journal % has a posting currency mismatch', target_journal_id;
    END IF;
    IF debit_total <> credit_total THEN
        RAISE EXCEPTION 'Journal % is out of balance: debits %, credits %',
            target_journal_id, debit_total, credit_total;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_validate_journal_header_integrity
    AFTER INSERT ON journal_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_journal_integrity();

CREATE CONSTRAINT TRIGGER trg_validate_journal_posting_integrity
    AFTER INSERT OR UPDATE OR DELETE ON journal_postings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_journal_integrity();
