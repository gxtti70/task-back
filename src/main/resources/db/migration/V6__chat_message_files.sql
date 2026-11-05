ALTER TABLE messages ADD COLUMN file_name VARCHAR(255);
ALTER TABLE messages ADD COLUMN file_content_type VARCHAR(150);
ALTER TABLE messages ADD COLUMN file_size_bytes BIGINT;
ALTER TABLE messages ADD COLUMN file_storage_key VARCHAR(36);
ALTER TABLE messages ADD COLUMN message_type VARCHAR(10) DEFAULT 'TEXT';
