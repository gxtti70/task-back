ALTER TABLE users ADD COLUMN google_subject VARCHAR(255);
CREATE UNIQUE INDEX users_google_subject_unique_idx
    ON users (google_subject)
    WHERE google_subject IS NOT NULL;
