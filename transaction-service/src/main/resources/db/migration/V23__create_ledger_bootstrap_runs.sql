CREATE TABLE ledger_bootstrap_runs (
    run_id VARCHAR(36) PRIMARY KEY,
    requested_by VARCHAR(128) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    business_date DATE NOT NULL,
    maintenance_mode BOOLEAN NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    imported_accounts INTEGER,
    reused_accounts INTEGER,
    seeded_system_accounts INTEGER,
    opening_journals INTEGER,
    currencies_json TEXT,
    failure_reason VARCHAR(1000),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT ck_ledger_bootstrap_run_outcome CHECK (outcome IN ('STARTED', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_ledger_bootstrap_runs_started_at ON ledger_bootstrap_runs(started_at DESC);
CREATE INDEX idx_ledger_bootstrap_runs_outcome ON ledger_bootstrap_runs(outcome, started_at DESC);
