CREATE TABLE IF NOT EXISTS channels (
    id UUID PRIMARY KEY,
    type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    name VARCHAR(100),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    dm_user_low UUID,
    dm_user_high UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT channels_type_check CHECK (type IN ('TEXT', 'VOICE')),
    CONSTRAINT channels_project_name_check CHECK (project_id IS NULL OR name IS NOT NULL),
    CONSTRAINT channels_dm_fields_check CHECK ((project_id IS NULL) = (dm_user_low IS NOT NULL AND dm_user_high IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS channels_project_idx ON channels(project_id);
CREATE UNIQUE INDEX IF NOT EXISTS channels_project_name_unique_idx ON channels(project_id, name) WHERE project_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS channels_dm_pair_unique_idx ON channels(dm_user_low, dm_user_high) WHERE project_id IS NULL;

CREATE TABLE IF NOT EXISTS channel_members (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT channel_members_channel_user_unique UNIQUE (channel_id, user_id)
);

CREATE INDEX IF NOT EXISTS channel_members_user_idx ON channel_members(user_id);

CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS messages_channel_created_id_idx ON messages(channel_id, created_at DESC, id DESC);
