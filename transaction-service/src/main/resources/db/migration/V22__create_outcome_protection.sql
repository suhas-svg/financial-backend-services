CREATE TABLE outcome_scenarios (
    scenario_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_version INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    create_idempotency_key VARCHAR(128) NOT NULL,
    create_request_fingerprint VARCHAR(64) NOT NULL,
    last_source_fingerprint VARCHAR(64),
    last_protection_state VARCHAR(24),
    last_checked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_outcome_scenario_create_key UNIQUE (user_id, create_idempotency_key),
    CONSTRAINT ck_outcome_scenario_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_outcome_scenario_currency CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3),
    CONSTRAINT ck_outcome_scenario_protection_state CHECK (last_protection_state IS NULL OR last_protection_state IN ('SAFE', 'AT_RISK'))
);

CREATE TABLE outcome_scenario_versions (
    version_id VARCHAR(36) PRIMARY KEY,
    scenario_id VARCHAR(36) NOT NULL,
    scenario_version INTEGER NOT NULL,
    horizon_start DATE NOT NULL,
    horizon_days INTEGER NOT NULL,
    protected_minimum NUMERIC(19, 2) NOT NULL,
    account_ids_json TEXT NOT NULL,
    assumptions_json TEXT NOT NULL,
    shocks_json TEXT NOT NULL,
    ledger_snapshot_json TEXT NOT NULL,
    schedule_snapshot_json TEXT NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    mutation_idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_outcome_version_scenario FOREIGN KEY (scenario_id) REFERENCES outcome_scenarios(scenario_id),
    CONSTRAINT uq_outcome_scenario_version UNIQUE (scenario_id, scenario_version),
    CONSTRAINT uq_outcome_version_mutation_key UNIQUE (scenario_id, mutation_idempotency_key),
    CONSTRAINT ck_outcome_horizon_days CHECK (horizon_days BETWEEN 1 AND 90),
    CONSTRAINT ck_outcome_protected_minimum CHECK (protected_minimum >= 0)
);

CREATE TABLE outcome_simulation_results (
    result_id VARCHAR(36) PRIMARY KEY,
    scenario_id VARCHAR(36) NOT NULL,
    scenario_version INTEGER NOT NULL,
    baseline_safe BOOLEAN NOT NULL,
    baseline_lowest_balance NUMERIC(19, 2) NOT NULL,
    baseline_failure_date DATE,
    proof_json TEXT NOT NULL,
    failure_json TEXT,
    repair_json TEXT NOT NULL,
    evaluated_combinations INTEGER NOT NULL,
    search_capped BOOLEAN NOT NULL,
    result_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_outcome_result_version FOREIGN KEY (scenario_id, scenario_version)
        REFERENCES outcome_scenario_versions(scenario_id, scenario_version),
    CONSTRAINT uq_outcome_result_version UNIQUE (scenario_id, scenario_version)
);

CREATE TABLE outcome_guardrail_drafts (
    guardrail_id VARCHAR(36) PRIMARY KEY,
    scenario_id VARCHAR(36) NOT NULL,
    result_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    guardrail_type VARCHAR(48) NOT NULL,
    threshold_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    scope_json TEXT NOT NULL,
    preview_text VARCHAR(1000) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(24) NOT NULL,
    accepted_at TIMESTAMP,
    acceptance_idempotency_key VARCHAR(128),
    acceptance_fingerprint VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_outcome_guardrail_scenario FOREIGN KEY (scenario_id) REFERENCES outcome_scenarios(scenario_id),
    CONSTRAINT fk_outcome_guardrail_result FOREIGN KEY (result_id) REFERENCES outcome_simulation_results(result_id),
    CONSTRAINT uq_outcome_guardrail_acceptance_key UNIQUE (user_id, acceptance_idempotency_key),
    CONSTRAINT ck_outcome_guardrail_status CHECK (status IN ('DRAFT', 'ACCEPTED', 'EXPIRED')),
    CONSTRAINT ck_outcome_guardrail_threshold CHECK (threshold_amount >= 0)
);

CREATE TABLE outcome_domain_events (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    scenario_id VARCHAR(36) NOT NULL,
    scenario_version INTEGER NOT NULL,
    result_id VARCHAR(36),
    guardrail_id VARCHAR(36),
    dedupe_key VARCHAR(180) NOT NULL,
    fields_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_outcome_event_scenario FOREIGN KEY (scenario_id) REFERENCES outcome_scenarios(scenario_id),
    CONSTRAINT uq_outcome_event_dedupe UNIQUE (user_id, dedupe_key)
);

CREATE INDEX idx_outcome_scenarios_user ON outcome_scenarios(user_id, updated_at DESC);
CREATE INDEX idx_outcome_scenarios_monitor ON outcome_scenarios(status, last_checked_at);
CREATE INDEX idx_outcome_guardrails_scenario ON outcome_guardrail_drafts(scenario_id, created_at);
CREATE INDEX idx_outcome_events_scenario ON outcome_domain_events(scenario_id, created_at);

CREATE OR REPLACE FUNCTION reject_outcome_immutable_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Outcome Protection snapshots and results are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_outcome_versions_immutable
BEFORE UPDATE OR DELETE ON outcome_scenario_versions
FOR EACH ROW EXECUTE FUNCTION reject_outcome_immutable_mutation();

CREATE TRIGGER trg_outcome_results_immutable
BEFORE UPDATE OR DELETE ON outcome_simulation_results
FOR EACH ROW EXECUTE FUNCTION reject_outcome_immutable_mutation();

CREATE TRIGGER trg_outcome_events_immutable
BEFORE UPDATE OR DELETE ON outcome_domain_events
FOR EACH ROW EXECUTE FUNCTION reject_outcome_immutable_mutation();
