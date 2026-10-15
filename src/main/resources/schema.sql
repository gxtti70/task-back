CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_data TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_timezone VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS week_starts_on INTEGER;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_job_title VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_bio VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_accent_color VARCHAR(7);

-- Store roles in uppercase and accept every role supported by the API.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
UPDATE users
SET role = upper(regexp_replace(btrim(role), '^(ROLE_)+', '', 'i'))
WHERE role IS DISTINCT FROM upper(regexp_replace(btrim(role), '^(ROLE_)+', '', 'i'));
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('ADMIN', 'MANAGER', 'SCRUM', 'DEVELOPER'));

-- Canonical emails prevent duplicate accounts that differ only by case or spaces.
UPDATE users SET email = lower(btrim(email)) WHERE email IS DISTINCT FROM lower(btrim(email));
CREATE UNIQUE INDEX IF NOT EXISTS users_email_normalized_unique_idx ON users (lower(btrim(email)));

CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    owner_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(32) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    story_points INTEGER NOT NULL DEFAULT 1,
    due_date DATE,
    assignee_id UUID NOT NULL REFERENCES users(id),
    project_id UUID REFERENCES projects(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS due_date DATE;

CREATE TABLE IF NOT EXISTS task_comments (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS task_comments_task_created_at_idx ON task_comments(task_id, created_at);

CREATE TABLE IF NOT EXISTS task_attachments (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    uploaded BOOLEAN NOT NULL DEFAULT FALSE,
    upload_expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS task_attachments_task_created_at_idx ON task_attachments(task_id, created_at DESC);

CREATE TABLE IF NOT EXISTS task_tags (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    tag VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS project_members (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT project_members_project_user_unique UNIQUE (project_id, user_id)
);

CREATE TABLE IF NOT EXISTS project_favorites (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT project_favorites_user_project_unique UNIQUE (user_id, project_id)
);
CREATE INDEX IF NOT EXISTS project_favorites_user_created_at_idx ON project_favorites(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS notifications_user_created_at_idx ON notifications(user_id, created_at DESC);


CREATE TABLE IF NOT EXISTS user_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    tier VARCHAR(24) NOT NULL DEFAULT 'FREE',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    current_period_start TIMESTAMP,
    current_period_end TIMESTAMP,
    provider VARCHAR(16) NOT NULL DEFAULT 'DEV',
    provider_subscription_id VARCHAR(255) UNIQUE,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tier VARCHAR(24) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    provider_payment_id VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS payments_user_created_at_idx ON payments(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS announcements (
    key VARCHAR(100) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS announcement_dismissals (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    announcement_key VARCHAR(100) NOT NULL REFERENCES announcements(key) ON DELETE CASCADE,
    dismissed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT announcement_dismissals_user_key_unique UNIQUE (user_id, announcement_key)
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS password_reset_tokens_user_idx ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS password_reset_tokens_token_idx ON password_reset_tokens(token);
