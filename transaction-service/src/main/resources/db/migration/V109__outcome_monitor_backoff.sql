-- Monitor health for Outcome Protection scenarios.
--
-- A scenario whose re-evaluation keeps failing (for example one that references
-- an account the account service no longer resolves) used to keep its old
-- last_checked_at, so it stayed at the front of every monitor run. With enough
-- such scenarios, healthy customers' scenarios were never re-checked.
--
-- The monitor now records each failure, waits with an exponential backoff
-- before retrying (monitor_next_attempt_at), and marks the scenario degraded
-- after repeated failures. Any successful evaluation, including a customer's
-- manual "Check current state", clears these columns.
ALTER TABLE outcome_scenarios
    ADD COLUMN monitor_failure_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN monitor_next_attempt_at TIMESTAMP,
    ADD COLUMN monitor_last_error VARCHAR(500),
    ADD COLUMN monitor_degraded_at TIMESTAMP;

ALTER TABLE outcome_scenarios
    ADD CONSTRAINT ck_outcome_scenario_monitor_failures CHECK (monitor_failure_count >= 0);

CREATE INDEX IF NOT EXISTS idx_outcome_scenarios_monitor_due
    ON outcome_scenarios (status, monitor_next_attempt_at);
