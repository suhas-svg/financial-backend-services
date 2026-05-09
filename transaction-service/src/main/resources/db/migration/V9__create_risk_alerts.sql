CREATE TABLE risk_alerts (
    alert_id VARCHAR(36) PRIMARY KEY,
    alert_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    user_id VARCHAR(128),
    transaction_id VARCHAR(36),
    from_account_id VARCHAR(255),
    to_account_id VARCHAR(255),
    amount NUMERIC(19, 2),
    currency VARCHAR(3),
    reason VARCHAR(500) NOT NULL,
    recommendation VARCHAR(500),
    dedupe_key VARCHAR(255) NOT NULL UNIQUE,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    reviewed_by VARCHAR(128),
    reviewed_at TIMESTAMP,
    resolution_note VARCHAR(500)
);

CREATE INDEX idx_risk_alert_created_at ON risk_alerts (created_at);
CREATE INDEX idx_risk_alert_status ON risk_alerts (status);
CREATE INDEX idx_risk_alert_severity ON risk_alerts (severity);
CREATE INDEX idx_risk_alert_type ON risk_alerts (alert_type);
CREATE INDEX idx_risk_alert_user_id ON risk_alerts (user_id);
CREATE INDEX idx_risk_alert_transaction_id ON risk_alerts (transaction_id);
CREATE INDEX idx_risk_alert_dedupe_key ON risk_alerts (dedupe_key);
