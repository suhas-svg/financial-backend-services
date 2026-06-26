CREATE TABLE customer_monthly_statements (
    statement_id UUID PRIMARY KEY,
    ledger_account_id UUID NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    external_account_id VARCHAR(255) NOT NULL,
    currency CHAR(3) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    statement_version INTEGER NOT NULL DEFAULT 1,
    opening_balance NUMERIC(19,2) NOT NULL,
    closing_balance NUMERIC(19,2) NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_statement_ledger_account
        FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (ledger_account_id),
    CONSTRAINT chk_statement_currency
        CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3),
    CONSTRAINT chk_statement_period
        CHECK (period_start < period_end),
    CONSTRAINT chk_statement_version
        CHECK (statement_version > 0),
    CONSTRAINT uk_statement_account_period_version
        UNIQUE (owner_id, external_account_id, period_start, period_end, statement_version)
);

CREATE INDEX idx_customer_monthly_statements_owner
    ON customer_monthly_statements (owner_id, period_start DESC, external_account_id, statement_version DESC);

CREATE TABLE customer_monthly_statement_lines (
    line_id UUID PRIMARY KEY,
    statement_id UUID NOT NULL,
    journal_id UUID NOT NULL,
    line_sequence INTEGER NOT NULL,
    effective_date DATE NOT NULL,
    description VARCHAR(500),
    amount NUMERIC(19,2) NOT NULL,
    running_balance NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    CONSTRAINT fk_statement_line_statement
        FOREIGN KEY (statement_id) REFERENCES customer_monthly_statements (statement_id),
    CONSTRAINT fk_statement_line_journal
        FOREIGN KEY (journal_id) REFERENCES journal_transactions (journal_id),
    CONSTRAINT chk_statement_line_sequence
        CHECK (line_sequence > 0),
    CONSTRAINT chk_statement_line_currency
        CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3),
    CONSTRAINT uk_statement_line_sequence
        UNIQUE (statement_id, line_sequence),
    CONSTRAINT uk_statement_line_journal
        UNIQUE (statement_id, journal_id)
);

CREATE INDEX idx_customer_monthly_statement_lines_statement
    ON customer_monthly_statement_lines (statement_id, line_sequence);

CREATE TRIGGER trg_customer_monthly_statements_immutable
    BEFORE UPDATE OR DELETE ON customer_monthly_statements
    FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ledger_mutation();

CREATE TRIGGER trg_customer_monthly_statement_lines_immutable
    BEFORE UPDATE OR DELETE ON customer_monthly_statement_lines
    FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ledger_mutation();
