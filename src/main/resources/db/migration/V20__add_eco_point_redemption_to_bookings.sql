ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS eco_points_redeemed INTEGER NOT NULL DEFAULT 0;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS eco_points_discount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE bookings
    ADD CONSTRAINT chk_bookings_eco_points_redeemed_non_negative
        CHECK (eco_points_redeemed >= 0);

ALTER TABLE bookings
    ADD CONSTRAINT chk_bookings_eco_points_discount_non_negative
        CHECK (eco_points_discount >= 0);
