CREATE TABLE beneficiaries (
    beneficiary_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    destination_account_id VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    nickname VARCHAR(120),
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    disabled_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_beneficiaries_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_beneficiaries_currency CHECK (currency IN ('USD', 'EUR', 'GBP'))
);

CREATE INDEX idx_beneficiaries_user_status ON beneficiaries(user_id, status);
CREATE INDEX idx_beneficiaries_destination_account ON beneficiaries(destination_account_id);
CREATE UNIQUE INDEX uq_beneficiaries_active_destination
    ON beneficiaries(user_id, destination_account_id, currency)
    WHERE status = 'ACTIVE';
