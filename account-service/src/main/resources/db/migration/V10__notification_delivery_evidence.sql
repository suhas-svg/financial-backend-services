ALTER TABLE notifications
    ADD COLUMN delivery_id VARCHAR(36),
    ADD COLUMN first_received_at TIMESTAMP,
    ADD COLUMN last_received_at TIMESTAMP,
    ADD COLUMN delivery_count INTEGER NOT NULL DEFAULT 1;

UPDATE notifications
SET first_received_at = created_at,
    last_received_at = created_at
WHERE first_received_at IS NULL OR last_received_at IS NULL;

ALTER TABLE notifications
    ALTER COLUMN first_received_at SET NOT NULL,
    ALTER COLUMN last_received_at SET NOT NULL,
    ADD CONSTRAINT ck_notification_delivery_count CHECK (delivery_count >= 1);

CREATE UNIQUE INDEX uq_notifications_delivery_id
    ON notifications(delivery_id) WHERE delivery_id IS NOT NULL;
