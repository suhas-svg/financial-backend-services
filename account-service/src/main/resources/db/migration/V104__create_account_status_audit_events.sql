-- Account status changes are a privileged operator action (freeze, unfreeze). They were
-- recorded only on the accounts row itself, so the transaction-service audit log the
-- operations console presents as its governance record never saw them. This table gives
-- every status transition its own immutable, attributable row.
CREATE TABLE account_status_audit_events (
    event_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_account_status_audit_created ON account_status_audit_events(created_at DESC);
CREATE INDEX idx_account_status_audit_account ON account_status_audit_events(account_id, created_at DESC);
