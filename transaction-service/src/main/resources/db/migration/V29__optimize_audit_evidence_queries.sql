CREATE INDEX IF NOT EXISTS idx_audit_created_at_desc
    ON audit_log_entries (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_type_created_at
    ON audit_log_entries (event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action_created_at
    ON audit_log_entries (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_outcome_created_at
    ON audit_log_entries (outcome, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_user_created_at
    ON audit_log_entries (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_transaction_created_at
    ON audit_log_entries (transaction_id, created_at DESC);