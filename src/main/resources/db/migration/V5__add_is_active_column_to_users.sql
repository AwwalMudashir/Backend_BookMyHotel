ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_active CHAR(1);

UPDATE users
SET is_active = 'Y'
WHERE is_active IS NULL;
