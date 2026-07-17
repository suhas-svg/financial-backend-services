CREATE TABLE outcome_consent_versions (
    version_id VARCHAR(64) PRIMARY KEY,
    terms_hash VARCHAR(64) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    jurisdiction_rules VARCHAR(500) NOT NULL,
    accessibility_standard VARCHAR(100) NOT NULL,
    retention_policy VARCHAR(160) NOT NULL,
    approval_reference VARCHAR(160),
    effective_from TIMESTAMP WITH TIME ZONE,
    retired_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_outcome_consent_lifecycle CHECK
        (lifecycle_status IN ('DRAFT','NON_PRODUCTION_APPROVED','APPROVED','RETIRED'))
);

CREATE TABLE outcome_consent_governance_events (
    event_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    policy_id VARCHAR(36),
    version_id VARCHAR(64),
    event_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    jurisdiction VARCHAR(16),
    accessibility_metadata VARCHAR(500),
    retention_metadata VARCHAR(500),
    complaint_reference VARCHAR(100),
    detail VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_outcome_consent_governance_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_outcome_consent_governance_user
    ON outcome_consent_governance_events(user_id, created_at DESC);

CREATE TABLE outcome_provider_evidence (
    evidence_id VARCHAR(36) PRIMARY KEY,
    boundary_type VARCHAR(32) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    provider_reference VARCHAR(160),
    policy_or_contract_version VARCHAR(100),
    classification VARCHAR(32) NOT NULL,
    reconciliation_status VARCHAR(32) NOT NULL,
    correlation_id VARCHAR(160),
    as_of TIMESTAMP WITH TIME ZONE,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    detail VARCHAR(1000)
);

INSERT INTO outcome_consent_versions (
    version_id, terms_hash, lifecycle_status, jurisdiction_rules,
    accessibility_standard, retention_policy, approval_reference, effective_from
) VALUES (
    '2026-07-16.1',
    repeat('0', 64),
    'NON_PRODUCTION_APPROVED',
    '*',
    'WCAG_REVIEW_REQUIRED',
    'RETENTION_POLICY_REQUIRED',
    'LOCAL-DEMO-NOT-LEGAL-APPROVAL',
    CURRENT_TIMESTAMP
);
