ALTER TABLE outcome_scenario_versions
    ADD COLUMN source_fingerprint_schema VARCHAR(32) NOT NULL DEFAULT 'outcome-source-v1',
    ADD COLUMN source_components_json TEXT NOT NULL DEFAULT '{}';

COMMENT ON COLUMN outcome_scenario_versions.source_fingerprint_schema IS
    'Version of deterministic authoritative-source canonicalization; v1 rows fail closed for executable guardrails.';
COMMENT ON COLUMN outcome_scenario_versions.source_components_json IS
    'Immutable canonical account, ledger, schedule, and protected-obligation components used by freshness checks.';
