ALTER TABLE scheduled_transfers
    ADD COLUMN source_time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN source_local_date_time TIMESTAMP,
    ADD COLUMN dst_overlap_policy VARCHAR(16) NOT NULL DEFAULT 'EARLIER',
    ADD COLUMN dst_gap_policy VARCHAR(24) NOT NULL DEFAULT 'SHIFT_FORWARD',
    ADD COLUMN recurrence_anchor_day INTEGER,
    ADD COLUMN recurrence_anchor_end_of_month BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE scheduled_transfers
SET source_local_date_time = next_run_at,
    recurrence_anchor_day = EXTRACT(DAY FROM next_run_at)
WHERE source_local_date_time IS NULL;

ALTER TABLE scheduled_transfers
    ALTER COLUMN source_local_date_time SET NOT NULL,
    ALTER COLUMN recurrence_anchor_day SET NOT NULL,
    ADD CONSTRAINT ck_scheduled_transfer_overlap_policy
        CHECK (dst_overlap_policy IN ('EARLIER', 'LATER')),
    ADD CONSTRAINT ck_scheduled_transfer_gap_policy
        CHECK (dst_gap_policy IN ('SHIFT_FORWARD', 'REJECT')),
    ADD CONSTRAINT ck_scheduled_transfer_anchor_day
        CHECK (recurrence_anchor_day BETWEEN 1 AND 31);

CREATE TABLE outcome_notification_deliveries (
    delivery_id VARCHAR(36) PRIMARY KEY,
    warning_event_id VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(128) NOT NULL,
    scenario_id VARCHAR(36) NOT NULL,
    dedupe_key VARCHAR(180) NOT NULL UNIQUE,
    payload_json TEXT NOT NULL,
    state VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    first_attempt_at TIMESTAMP,
    last_attempt_at TIMESTAMP,
    delivered_at TIMESTAMP,
    terminal_at TIMESTAMP,
    sla_escalated_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_outcome_notification_warning FOREIGN KEY (warning_event_id)
        REFERENCES outcome_domain_events(event_id),
    CONSTRAINT fk_outcome_notification_scenario FOREIGN KEY (scenario_id)
        REFERENCES outcome_scenarios(scenario_id),
    CONSTRAINT ck_outcome_notification_state CHECK
        (state IN ('PENDING', 'RETRY_SCHEDULED', 'DELIVERED', 'TERMINAL_FAILED')),
    CONSTRAINT ck_outcome_notification_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outcome_notification_due
    ON outcome_notification_deliveries(state, next_attempt_at);
CREATE INDEX idx_outcome_notification_scenario
    ON outcome_notification_deliveries(user_id, scenario_id, created_at DESC);

ALTER TABLE ledger_bootstrap_runs
    ADD COLUMN requested_role VARCHAR(64) NOT NULL DEFAULT 'SYSTEM_STARTUP',
    ADD COLUMN request_id VARCHAR(128) NOT NULL DEFAULT 'legacy-bootstrap';
