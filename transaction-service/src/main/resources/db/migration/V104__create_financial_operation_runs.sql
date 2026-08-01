CREATE TABLE financial_operation_runs (
    operation_type VARCHAR(40) NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    claim_id UUID NOT NULL,
    claimed_by VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    zone_id VARCHAR(64) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claim_until TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    evidence TEXT,
    error_code VARCHAR(160),
    PRIMARY KEY (operation_type, business_date),
    CONSTRAINT ck_financial_operation_type CHECK (
        operation_type IN ('DAILY_RECONCILIATION', 'MONTHLY_STATEMENT_CLOSE')
    ),
    CONSTRAINT ck_financial_operation_status CHECK (
        status IN ('RUNNING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_financial_operation_attempts CHECK (attempt_count > 0)
);

CREATE INDEX idx_financial_operation_runs_status_claim
    ON financial_operation_runs (status, claim_until);
