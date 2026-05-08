CREATE TABLE audit_log_entries (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    action VARCHAR(100) NOT NULL,
    outcome VARCHAR(64) NOT NULL,
    user_id VARCHAR(128),
    transaction_id VARCHAR(36),
    from_account_id VARCHAR(255),
    to_account_id VARCHAR(255),
    amount NUMERIC(19, 2),
    currency VARCHAR(3),
    ip_address VARCHAR(45),
    details VARCHAR(500),
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    metadata TEXT
);

CREATE INDEX idx_audit_created_at ON audit_log_entries (created_at);
CREATE INDEX idx_audit_action ON audit_log_entries (action);
CREATE INDEX idx_audit_outcome ON audit_log_entries (outcome);
CREATE INDEX idx_audit_user_id ON audit_log_entries (user_id);
CREATE INDEX idx_audit_transaction_id ON audit_log_entries (transaction_id);
