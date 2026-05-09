CREATE TABLE risk_cases (
    case_id VARCHAR(36) PRIMARY KEY,
    case_number VARCHAR(32) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    user_id VARCHAR(128),
    transaction_id VARCHAR(36),
    primary_alert_id VARCHAR(36),
    assigned_to VARCHAR(128),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP,
    closed_at TIMESTAMP,
    resolution_note VARCHAR(500)
);

CREATE TABLE risk_case_alerts (
    case_id VARCHAR(36) NOT NULL,
    alert_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (case_id, alert_id),
    CONSTRAINT fk_risk_case_alert_case FOREIGN KEY (case_id) REFERENCES risk_cases (case_id) ON DELETE CASCADE,
    CONSTRAINT fk_risk_case_alert_alert FOREIGN KEY (alert_id) REFERENCES risk_alerts (alert_id) ON DELETE CASCADE
);

CREATE TABLE risk_case_notes (
    note_id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    author VARCHAR(128) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_risk_case_note_case FOREIGN KEY (case_id) REFERENCES risk_cases (case_id) ON DELETE CASCADE
);

CREATE INDEX idx_risk_case_number ON risk_cases (case_number);
CREATE INDEX idx_risk_case_status ON risk_cases (status);
CREATE INDEX idx_risk_case_priority ON risk_cases (priority);
CREATE INDEX idx_risk_case_user_id ON risk_cases (user_id);
CREATE INDEX idx_risk_case_transaction_id ON risk_cases (transaction_id);
CREATE INDEX idx_risk_case_primary_alert_id ON risk_cases (primary_alert_id);
CREATE INDEX idx_risk_case_assigned_to ON risk_cases (assigned_to);
CREATE INDEX idx_risk_case_created_at ON risk_cases (created_at);
CREATE INDEX idx_risk_case_alert_alert_id ON risk_case_alerts (alert_id);
CREATE INDEX idx_risk_case_note_case_id ON risk_case_notes (case_id);
CREATE INDEX idx_risk_case_note_created_at ON risk_case_notes (created_at);
