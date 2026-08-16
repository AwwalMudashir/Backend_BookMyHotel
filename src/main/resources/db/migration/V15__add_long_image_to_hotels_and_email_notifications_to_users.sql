-- Add long_image to hotels and email_notifications to users

ALTER TABLE hotels
    ADD COLUMN long_image TEXT;

ALTER TABLE users
    ADD COLUMN email_notifications BOOLEAN DEFAULT FALSE;
