ALTER TABLE user_mfa_methods
    ADD COLUMN secret_key_id VARCHAR(64) NOT NULL DEFAULT 'legacy';

CREATE TABLE notification_provider_receipts (
    receipt_id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES notifications(notification_id),
    delivery_id VARCHAR(36),
    provider VARCHAR(80) NOT NULL,
    provider_receipt_id VARCHAR(160),
    classification VARCHAR(30) NOT NULL,
    reconciliation_status VARCHAR(30) NOT NULL,
    attempted_at TIMESTAMP NOT NULL,
    detail VARCHAR(500),
    CONSTRAINT uq_notification_provider_receipt UNIQUE (notification_id, provider)
);

CREATE INDEX idx_notification_provider_receipt_delivery
    ON notification_provider_receipts(delivery_id);

CREATE TABLE mfa_key_rotation_runs (
    run_id VARCHAR(36) PRIMARY KEY,
    requested_by VARCHAR(100) NOT NULL,
    from_key_id VARCHAR(64) NOT NULL,
    to_key_id VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    examined_count INTEGER NOT NULL,
    rotated_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(500)
);
