-- Rate-limit online guessing against the six-digit TOTP code space.
--
-- Enrollment confirmation and MFA disablement previously accepted unlimited code attempts for a
-- logged-in session, leaving only network latency as a bound on guesses. These columns let the
-- service refuse verification outright once a consecutive-failure threshold is reached, without
-- consulting the secret at all.
ALTER TABLE user_mfa_methods
    ADD COLUMN failed_verification_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN user_mfa_methods.failed_verification_attempts IS
    'Consecutive failed TOTP verifications; reset to zero on success and when a lockout expires.';
COMMENT ON COLUMN user_mfa_methods.locked_until IS
    'While in the future, TOTP verification is refused before the secret is consulted.';
