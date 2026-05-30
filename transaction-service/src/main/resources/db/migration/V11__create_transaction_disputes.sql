CREATE TABLE transaction_disputes (
    dispute_id VARCHAR(36) PRIMARY KEY,
    dispute_number VARCHAR(32) NOT NULL UNIQUE,
    transaction_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    assigned_to VARCHAR(128),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP,
    closed_at TIMESTAMP,
    resolution_note VARCHAR(1000)
);

CREATE TABLE transaction_dispute_notes (
    note_id VARCHAR(36) PRIMARY KEY,
    dispute_id VARCHAR(36) NOT NULL,
    author VARCHAR(128) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_dispute_notes_dispute FOREIGN KEY (dispute_id) REFERENCES transaction_disputes(dispute_id)
);

CREATE INDEX idx_dispute_number ON transaction_disputes(dispute_number);
CREATE INDEX idx_dispute_status ON transaction_disputes(status);
CREATE INDEX idx_dispute_user_id ON transaction_disputes(user_id);
CREATE INDEX idx_dispute_transaction_id ON transaction_disputes(transaction_id);
CREATE INDEX idx_dispute_assigned_to ON transaction_disputes(assigned_to);
CREATE INDEX idx_dispute_created_at ON transaction_disputes(created_at);
CREATE INDEX idx_dispute_notes_dispute_id ON transaction_dispute_notes(dispute_id);
CREATE INDEX idx_dispute_notes_created_at ON transaction_dispute_notes(created_at);
CREATE UNIQUE INDEX uq_active_dispute_transaction
    ON transaction_disputes(transaction_id)
    WHERE status IN ('OPEN', 'IN_REVIEW');
