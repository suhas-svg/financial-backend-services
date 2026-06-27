CREATE TABLE reconciliation_runs (
    run_id UUID PRIMARY KEY,
    business_date DATE NOT NULL,
    reconciliation_type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    total_exceptions INTEGER NOT NULL DEFAULT 0,
    critical_exceptions INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_reconciliation_run_type
        CHECK (reconciliation_type IN ('DAILY_LEDGER')),
    CONSTRAINT chk_reconciliation_run_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'COMPLETED_WITH_EXCEPTIONS', 'FAILED')),
    CONSTRAINT chk_reconciliation_exception_counts
        CHECK (total_exceptions >= 0 AND critical_exceptions >= 0 AND critical_exceptions <= total_exceptions)
);

CREATE INDEX idx_reconciliation_runs_business_date
    ON reconciliation_runs (business_date DESC, reconciliation_type, started_at DESC);

CREATE TABLE reconciliation_check_results (
    check_result_id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    check_code VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    checked_count INTEGER NOT NULL DEFAULT 0,
    exception_count INTEGER NOT NULL DEFAULT 0,
    summary VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reconciliation_check_run
        FOREIGN KEY (run_id) REFERENCES reconciliation_runs (run_id),
    CONSTRAINT chk_reconciliation_check_code
        CHECK (check_code IN (
            'JOURNAL_BALANCE_BY_CURRENCY',
            'JOURNAL_CURRENCY_CONSISTENCY',
            'LIFECYCLE_TRANSITION',
            'PROJECTION_RECOMPUTATION',
            'MIRROR_DRIFT',
            'MISSING_AUDIT_LINKAGE',
            'STALE_PENDING_JOURNAL',
            'FAILED_COMPENSATION',
            'NON_ZERO_SUSPENSE',
            'MISSING_SYSTEM_ACCOUNT',
            'STATEMENT_CLOSING_BALANCE')),
    CONSTRAINT chk_reconciliation_check_severity
        CHECK (severity IN ('INFO', 'WARNING', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_reconciliation_check_status
        CHECK (status IN ('PASSED', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_reconciliation_check_counts
        CHECK (checked_count >= 0 AND exception_count >= 0)
);

CREATE INDEX idx_reconciliation_check_results_run
    ON reconciliation_check_results (run_id, check_code);

CREATE TABLE reconciliation_exceptions (
    exception_id UUID PRIMARY KEY,
    check_code VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    fingerprint VARCHAR(240) NOT NULL,
    journal_id UUID,
    ledger_account_id UUID,
    summary VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(120),
    resolution_note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reconciliation_exception_journal
        FOREIGN KEY (journal_id) REFERENCES journal_transactions (journal_id),
    CONSTRAINT fk_reconciliation_exception_ledger_account
        FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (ledger_account_id),
    CONSTRAINT chk_reconciliation_exception_code
        CHECK (check_code IN (
            'JOURNAL_BALANCE_BY_CURRENCY',
            'JOURNAL_CURRENCY_CONSISTENCY',
            'LIFECYCLE_TRANSITION',
            'PROJECTION_RECOMPUTATION',
            'MIRROR_DRIFT',
            'MISSING_AUDIT_LINKAGE',
            'STALE_PENDING_JOURNAL',
            'FAILED_COMPENSATION',
            'NON_ZERO_SUSPENSE',
            'MISSING_SYSTEM_ACCOUNT',
            'STATEMENT_CLOSING_BALANCE')),
    CONSTRAINT chk_reconciliation_exception_severity
        CHECK (severity IN ('INFO', 'WARNING', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_reconciliation_exception_status
        CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'WAIVED')),
    CONSTRAINT chk_reconciliation_resolution_note
        CHECK (status NOT IN ('RESOLVED', 'WAIVED') OR resolution_note IS NOT NULL)
);

CREATE UNIQUE INDEX uk_open_reconciliation_exception_fingerprint
    ON reconciliation_exceptions (fingerprint)
    WHERE status NOT IN ('RESOLVED', 'WAIVED');

CREATE INDEX idx_reconciliation_exception_status
    ON reconciliation_exceptions (status, severity, updated_at DESC);

CREATE INDEX idx_reconciliation_exception_journal
    ON reconciliation_exceptions (journal_id)
    WHERE journal_id IS NOT NULL;

CREATE TABLE reconciliation_run_exceptions (
    run_id UUID NOT NULL,
    exception_id UUID NOT NULL,
    first_seen_in_run BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, exception_id),
    CONSTRAINT fk_reconciliation_run_exception_run
        FOREIGN KEY (run_id) REFERENCES reconciliation_runs (run_id),
    CONSTRAINT fk_reconciliation_run_exception_exception
        FOREIGN KEY (exception_id) REFERENCES reconciliation_exceptions (exception_id)
);

CREATE TABLE reconciliation_exception_notes (
    note_id UUID PRIMARY KEY,
    exception_id UUID NOT NULL,
    author VARCHAR(120) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reconciliation_note_exception
        FOREIGN KEY (exception_id) REFERENCES reconciliation_exceptions (exception_id),
    CONSTRAINT chk_reconciliation_note_not_blank
        CHECK (LENGTH(TRIM(note)) > 0)
);

CREATE INDEX idx_reconciliation_exception_notes_exception
    ON reconciliation_exception_notes (exception_id, created_at DESC);
