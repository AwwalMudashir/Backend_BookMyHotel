ALTER TABLE users
    ADD COLUMN IF NOT EXISTS managed_hotel_id BIGINT;

ALTER TABLE users
    ADD CONSTRAINT fk_users_managed_hotel FOREIGN KEY (managed_hotel_id) REFERENCES hotels(id);
