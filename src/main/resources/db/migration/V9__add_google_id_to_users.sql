ALTER TABLE users
    ADD COLUMN IF NOT EXISTS google_id VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT uq_users_google_id UNIQUE (google_id);
