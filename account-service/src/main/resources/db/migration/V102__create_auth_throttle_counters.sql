-- Throttle online password guessing and registration abuse.
--
-- /api/auth/login previously accepted unlimited attempts, so the only bound on a
-- password-guessing run was bcrypt latency. Counters live in the database rather
-- than in memory so every account-service replica enforces the same limit.
--
-- throttle_key is a scope prefix plus a subject: a SHA-256 of the normalized
-- username (usernames are not stored here) or the client IP address.
CREATE TABLE auth_throttle_counters (
    throttle_key      VARCHAR(200) PRIMARY KEY,
    failure_count     INTEGER NOT NULL,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_until      TIMESTAMP WITH TIME ZONE,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_auth_throttle_failure_count CHECK (failure_count >= 0)
);

CREATE INDEX idx_auth_throttle_updated_at ON auth_throttle_counters (updated_at);

COMMENT ON COLUMN auth_throttle_counters.failure_count IS
    'Attempts counted in the current window; the window restarts once it or a lockout expires.';
COMMENT ON COLUMN auth_throttle_counters.locked_until IS
    'While in the future, attempts in this scope are refused before credentials are checked.';
