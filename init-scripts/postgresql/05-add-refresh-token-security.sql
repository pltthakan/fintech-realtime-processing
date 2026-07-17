CREATE TABLE IF NOT EXISTS user_service.refresh_tokens (
    id                      UUID PRIMARY KEY,
    user_id                 BIGINT       NOT NULL REFERENCES user_service.users(id) ON DELETE CASCADE,
    token_hash              VARCHAR(64)  NOT NULL UNIQUE,
    family_id               UUID         NOT NULL,
    expires_at              TIMESTAMPTZ  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at            TIMESTAMPTZ,
    revoked_at              TIMESTAMPTZ,
    replaced_by_token_id    UUID,
    revoked_reason          VARCHAR(50),
    version                 BIGINT       NOT NULL DEFAULT 0
);

-- Older development databases may have created the SHA-256 value as CHAR(64).
-- Keep the migration repeatable and aligned with the JPA mapping.
ALTER TABLE user_service.refresh_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user
    ON user_service.refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_active
    ON user_service.refresh_tokens (family_id, revoked_at);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry
    ON user_service.refresh_tokens (expires_at);
