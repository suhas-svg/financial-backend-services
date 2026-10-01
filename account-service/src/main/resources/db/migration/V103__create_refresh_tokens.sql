-- Server-side sessions for the web console.
--
-- Access tokens are short-lived JWTs. A session is kept alive by an opaque refresh
-- token held in an httpOnly cookie. Every refresh rotates the token: the presented
-- row is marked rotated and a successor is issued in the same family. Presenting an
-- already-rotated token outside a short grace window means it was copied, so the
-- whole family is revoked. Logout revokes the family.
--
-- Only a SHA-256 of each token is stored.
CREATE TABLE refresh_tokens (
    token_id          UUID PRIMARY KEY,
    family_id         UUID NOT NULL,
    username          VARCHAR(100) NOT NULL,
    token_hash        CHAR(64) NOT NULL,
    issued_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    family_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rotated_at        TIMESTAMP WITH TIME ZONE,
    revoked_at        TIMESTAMP WITH TIME ZONE,
    revoke_reason     VARCHAR(32),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at <= family_expires_at)
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_username ON refresh_tokens (username);
CREATE INDEX idx_refresh_tokens_family_expires ON refresh_tokens (family_expires_at);
