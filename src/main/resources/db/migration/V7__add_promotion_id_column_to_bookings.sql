ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS promotion_id BIGINT;

ALTER TABLE bookings
    ADD CONSTRAINT fk_bookings_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id);
