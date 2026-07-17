CREATE TABLE provider_activations (
    activation_id VARCHAR(36) PRIMARY KEY,
    boundary_type VARCHAR(32) NOT NULL,
    provider_alias VARCHAR(100) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    contract_reference VARCHAR(200),
    credential_reference VARCHAR(240),
    credential_version VARCHAR(100),
    key_id VARCHAR(160),
    key_version VARCHAR(100),
    sla_evidence_reference VARCHAR(200),
    reconciliation_reference VARCHAR(200),
    webhook_verification_reference VARCHAR(200),
    security_review_reference VARCHAR(200),
    legal_approval_reference VARCHAR(200),
    jurisdiction_review_reference VARCHAR(200),
    rollback_reference VARCHAR(200),
    disaster_recovery_reference VARCHAR(200),
    created_by VARCHAR(100) NOT NULL,
    certified_by VARCHAR(100),
    approved_by VARCHAR(100),
    activated_by VARCHAR(100),
    suspended_by VARCHAR(100),
    suspension_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_provider_activation_boundary CHECK
        (boundary_type IN ('NOTIFICATION','IDP','KMS','RISK','FX','CONSENT')),
    CONSTRAINT ck_provider_activation_lifecycle CHECK
        (lifecycle_status IN ('DRAFT','SANDBOX_CERTIFIED','APPROVED','ACTIVE','SUSPENDED')),
    CONSTRAINT uq_provider_activation_boundary_alias UNIQUE (boundary_type, provider_alias)
);

CREATE INDEX idx_provider_activation_lifecycle
    ON provider_activations(lifecycle_status, boundary_type);

CREATE TABLE provider_activation_events (
    event_id VARCHAR(36) PRIMARY KEY,
    activation_id VARCHAR(36) NOT NULL REFERENCES provider_activations(activation_id),
    event_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    mfa_evidence_reference VARCHAR(240) NOT NULL,
    mfa_verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    external_evidence_reference VARCHAR(240),
    detail VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_provider_activation_event_idempotency UNIQUE (actor, idempotency_key)
);

CREATE INDEX idx_provider_activation_events_activation
    ON provider_activation_events(activation_id, created_at DESC);

CREATE TABLE provider_certification_runs (
    run_id VARCHAR(36) PRIMARY KEY,
    activation_id VARCHAR(36) NOT NULL REFERENCES provider_activations(activation_id),
    harness_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    required_checks VARCHAR(1000) NOT NULL,
    passed_checks VARCHAR(1000) NOT NULL,
    failed_checks VARCHAR(1000) NOT NULL,
    evidence_reference VARCHAR(240) NOT NULL,
    health_sla_reference VARCHAR(240) NOT NULL,
    rollback_reference VARCHAR(240) NOT NULL,
    disaster_recovery_reference VARCHAR(240) NOT NULL,
    executed_by VARCHAR(100) NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_provider_certification_status CHECK (status IN ('PASSED','FAILED')),
    CONSTRAINT uq_provider_certification_evidence UNIQUE (activation_id, evidence_reference)
);

CREATE INDEX idx_provider_certification_activation
    ON provider_certification_runs(activation_id, executed_at DESC);

CREATE TABLE provider_webhook_replay_evidence (
    replay_id VARCHAR(36) PRIMARY KEY,
    activation_id VARCHAR(36) NOT NULL REFERENCES provider_activations(activation_id),
    delivery_id VARCHAR(160) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    verifier_reference VARCHAR(200) NOT NULL,
    provider_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_provider_webhook_delivery UNIQUE (activation_id, delivery_id),
    CONSTRAINT uq_provider_webhook_digest UNIQUE (activation_id, payload_digest)
);
