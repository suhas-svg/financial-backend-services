CREATE TABLE user_mfa_methods (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    method_type VARCHAR(20) NOT NULL,
    secret_ciphertext VARCHAR(1024) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_mfa_method UNIQUE (user_id, method_type)
);
CREATE TABLE mfa_recovery_codes (
    id BIGSERIAL PRIMARY KEY,
    mfa_method_id BIGINT NOT NULL REFERENCES user_mfa_methods(id) ON DELETE CASCADE,
    code_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_recovery_method_unused ON mfa_recovery_codes(mfa_method_id, used_at);
CREATE TABLE step_up_challenges (
    challenge_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    proof_hash VARCHAR(64),
    consumer_key VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    proof_expires_at TIMESTAMP WITH TIME ZONE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_step_up_user_status ON step_up_challenges(user_id, status);
CREATE INDEX idx_step_up_expiry ON step_up_challenges(expires_at);
