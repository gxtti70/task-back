ALTER TABLE users
    ADD COLUMN IF NOT EXISTS presence_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN IF NOT EXISTS presence_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS users_presence_status_idx ON users(presence_status);
CREATE INDEX IF NOT EXISTS users_last_seen_at_idx ON users(last_seen_at);
