CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens(family_id);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens(user_id);
