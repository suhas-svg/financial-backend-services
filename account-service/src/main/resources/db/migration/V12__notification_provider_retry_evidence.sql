ALTER TABLE notification_provider_receipts
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN next_attempt_at TIMESTAMP,
    ADD COLUMN terminal_at TIMESTAMP,
    ADD COLUMN reconciled_at TIMESTAMP;

ALTER TABLE notification_provider_receipts
    ADD CONSTRAINT ck_notification_provider_attempt_count CHECK (attempt_count >= 1);

CREATE INDEX idx_notification_provider_retry_due
    ON notification_provider_receipts(next_attempt_at)
    WHERE next_attempt_at IS NOT NULL AND terminal_at IS NULL;
