ALTER TABLE accounts
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN status_reason VARCHAR(500),
    ADD COLUMN status_updated_at TIMESTAMP,
    ADD COLUMN status_updated_by VARCHAR(100);
