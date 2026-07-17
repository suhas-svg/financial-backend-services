CREATE TABLE outcome_guardrail_policies (
    policy_id VARCHAR(36) PRIMARY KEY,
    guardrail_id VARCHAR(36) NOT NULL UNIQUE,
    scenario_id VARCHAR(36) NOT NULL,
    result_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    funding_account_id VARCHAR(64) NOT NULL,
    protected_account_id VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    trigger_threshold NUMERIC(19,2) NOT NULL,
    max_action_amount NUMERIC(19,2) NOT NULL,
    total_limit NUMERIC(19,2) NOT NULL,
    total_executed NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_reserved NUMERIC(19,2) NOT NULL DEFAULT 0,
    max_executions INTEGER NOT NULL,
    execution_count INTEGER NOT NULL DEFAULT 0,
    terms_version VARCHAR(64) NOT NULL,
    terms_hash VARCHAR(64) NOT NULL,
    consent_evidence_json TEXT NOT NULL,
    consent_idempotency_key VARCHAR(128) NOT NULL,
    consent_request_fingerprint VARCHAR(64) NOT NULL,
    activation_challenge_id VARCHAR(36) NOT NULL,
    activation_challenge_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    activation_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consented_at TIMESTAMP WITH TIME ZONE NOT NULL,
    activated_at TIMESTAMP WITH TIME ZONE,
    suspended_at TIMESTAMP WITH TIME ZONE,
    suspension_reason VARCHAR(500),
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_guardrail_policy_draft FOREIGN KEY (guardrail_id) REFERENCES outcome_guardrail_drafts(guardrail_id),
    CONSTRAINT fk_guardrail_policy_scenario FOREIGN KEY (scenario_id) REFERENCES outcome_scenarios(scenario_id),
    CONSTRAINT fk_guardrail_policy_result FOREIGN KEY (result_id) REFERENCES outcome_simulation_results(result_id),
    CONSTRAINT uq_guardrail_policy_consent_key UNIQUE (user_id, consent_idempotency_key),
    CONSTRAINT ck_guardrail_policy_accounts CHECK (funding_account_id <> protected_account_id),
    CONSTRAINT ck_guardrail_policy_currency CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3),
    CONSTRAINT ck_guardrail_policy_amounts CHECK (
        trigger_threshold >= 0 AND max_action_amount > 0 AND total_limit > 0
        AND max_action_amount <= total_limit AND total_executed >= 0 AND total_reserved >= 0
        AND total_executed + total_reserved <= total_limit),
    CONSTRAINT ck_guardrail_policy_execution_counts CHECK (
        max_executions BETWEEN 1 AND 100 AND execution_count BETWEEN 0 AND max_executions),
    CONSTRAINT ck_guardrail_policy_status CHECK (
        status IN ('CONSENT_PENDING','ACTIVE','SUSPENDED','REVOKED','EXPIRED'))
);

CREATE TABLE outcome_guardrail_executions (
    execution_id VARCHAR(36) PRIMARY KEY,
    policy_id VARCHAR(36) NOT NULL,
    guardrail_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    transfer_idempotency_key VARCHAR(128) NOT NULL,
    transfer_authorization_id VARCHAR(36),
    transaction_id VARCHAR(36),
    authorization_challenge_id VARCHAR(36),
    authorization_expires_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_guardrail_execution_policy FOREIGN KEY (policy_id) REFERENCES outcome_guardrail_policies(policy_id),
    CONSTRAINT fk_guardrail_execution_draft FOREIGN KEY (guardrail_id) REFERENCES outcome_guardrail_drafts(guardrail_id),
    CONSTRAINT uq_guardrail_execution_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_guardrail_transfer_key UNIQUE (transfer_idempotency_key),
    CONSTRAINT ck_guardrail_execution_amount CHECK (amount > 0),
    CONSTRAINT ck_guardrail_execution_status CHECK (
        status IN ('REQUESTED','AWAITING_AUTHORIZATION','COMPLETED','CANCELLED','FAILED'))
);

CREATE TABLE outcome_guardrail_runtime_controls (
    control_id VARCHAR(32) PRIMARY KEY,
    execution_enabled BOOLEAN NOT NULL,
    reason VARCHAR(500) NOT NULL,
    changed_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO outcome_guardrail_runtime_controls (
    control_id, execution_enabled, reason, changed_by, updated_at, version
) VALUES (
    'GLOBAL', FALSE, 'Executable guardrails require explicit operator enablement', 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 0
);

CREATE TABLE outcome_guardrail_control_events (
    event_id VARCHAR(36) PRIMARY KEY,
    execution_enabled BOOLEAN NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_guardrail_control_event_key UNIQUE (actor, idempotency_key)
);

CREATE INDEX idx_guardrail_policies_user_status ON outcome_guardrail_policies(user_id, status, updated_at DESC);
CREATE INDEX idx_guardrail_policies_operator ON outcome_guardrail_policies(status, updated_at DESC);
CREATE INDEX idx_guardrail_executions_policy ON outcome_guardrail_executions(policy_id, created_at DESC);
CREATE INDEX idx_guardrail_executions_pending ON outcome_guardrail_executions(status, authorization_expires_at);
CREATE INDEX idx_guardrail_control_events_created ON outcome_guardrail_control_events(created_at DESC);

CREATE TRIGGER trg_guardrail_control_events_immutable
BEFORE UPDATE OR DELETE ON outcome_guardrail_control_events
FOR EACH ROW EXECUTE FUNCTION reject_outcome_immutable_mutation();
