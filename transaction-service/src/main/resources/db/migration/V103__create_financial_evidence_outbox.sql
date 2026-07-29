CREATE TABLE financial_evidence_outbox (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 8,
    next_attempt_at TIMESTAMP NOT NULL,
    delivered_at TIMESTAMP,
    last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_financial_evidence_source_event
        FOREIGN KEY (event_id) REFERENCES journal_state_events(event_id),
    CONSTRAINT chk_financial_evidence_status
        CHECK (status IN ('PENDING', 'RETRY_SCHEDULED', 'DELIVERED', 'TERMINAL_FAILED')),
    CONSTRAINT chk_financial_evidence_attempts
        CHECK (attempt_count >= 0 AND max_attempts BETWEEN 1 AND 32)
);

CREATE INDEX idx_financial_evidence_outbox_due
    ON financial_evidence_outbox (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED');

CREATE TABLE financial_evidence_deliveries (
    delivery_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    destination VARCHAR(48) NOT NULL,
    dedupe_key VARCHAR(260) NOT NULL UNIQUE,
    payload JSONB NOT NULL,
    delivered_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_financial_evidence_delivery_event
        FOREIGN KEY (event_id) REFERENCES financial_evidence_outbox(event_id),
    CONSTRAINT chk_financial_evidence_destination
        CHECK (destination IN ('AUDIT_ENRICHMENT', 'RISK_NOTIFICATION', 'ANALYTICS', 'NONCRITICAL_METRICS')),
    CONSTRAINT uk_financial_evidence_event_destination UNIQUE (event_id, destination)
);

CREATE INDEX idx_financial_evidence_delivery_event
    ON financial_evidence_deliveries (event_id, delivered_at);

CREATE TRIGGER trg_financial_evidence_deliveries_immutable
    BEFORE UPDATE OR DELETE ON financial_evidence_deliveries
    FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ledger_mutation();
