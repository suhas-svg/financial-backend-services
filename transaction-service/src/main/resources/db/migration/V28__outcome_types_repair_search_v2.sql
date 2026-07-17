ALTER TABLE outcome_scenario_versions
    ADD COLUMN outcome_type VARCHAR(32) NOT NULL DEFAULT 'BALANCE_FLOOR',
    ADD COLUMN protected_obligation_json TEXT,
    ADD COLUMN canonical_inputs_json TEXT NOT NULL DEFAULT '{}',
    ADD CONSTRAINT ck_outcome_version_type
        CHECK (outcome_type IN ('BALANCE_FLOOR', 'SCHEDULED_OBLIGATION')),
    ADD CONSTRAINT ck_outcome_version_obligation
        CHECK (
            (outcome_type = 'BALANCE_FLOOR' AND protected_obligation_json IS NULL)
            OR (outcome_type = 'SCHEDULED_OBLIGATION' AND protected_obligation_json IS NOT NULL)
        );

ALTER TABLE outcome_simulation_results
    ADD COLUMN engine_version VARCHAR(64) NOT NULL DEFAULT 'outcome-v1',
    ADD COLUMN canonical_inputs_json TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN source_versions_json TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN candidate_actions_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN replay_output_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN certificate_hash VARCHAR(64),
    ADD COLUMN ranking_factors_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN rejection_reasons_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN repair_evaluated_combinations INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN repair_search_capped BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_outcome_result_repair_evaluated
        CHECK (repair_evaluated_combinations >= 0),
    ADD CONSTRAINT ck_outcome_result_certificate
        CHECK (certificate_hash IS NULL OR certificate_hash ~ '^[a-f0-9]{64}$');

ALTER TABLE outcome_guardrail_drafts
    ADD COLUMN alternative_rank INTEGER,
    ADD COLUMN candidate_actions_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN replay_proof_json TEXT,
    ADD COLUMN replay_certificate_hash VARCHAR(64),
    ADD COLUMN ranking_factors_json TEXT,
    ADD COLUMN rejection_reasons_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN preview_selected_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN preview_selection_idempotency_key VARCHAR(128),
    ADD COLUMN preview_selection_fingerprint VARCHAR(64),
    ADD CONSTRAINT ck_outcome_guardrail_alternative_rank
        CHECK (alternative_rank IS NULL OR alternative_rank > 0),
    ADD CONSTRAINT ck_outcome_guardrail_replay_certificate
        CHECK (replay_certificate_hash IS NULL OR replay_certificate_hash ~ '^[a-f0-9]{64}$'),
    ADD CONSTRAINT uq_outcome_guardrail_preview_selection
        UNIQUE (user_id, preview_selection_idempotency_key);

CREATE INDEX idx_outcome_guardrail_alternative
    ON outcome_guardrail_drafts(result_id, alternative_rank, created_at);
