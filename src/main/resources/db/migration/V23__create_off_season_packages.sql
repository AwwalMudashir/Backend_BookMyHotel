CREATE TABLE off_season_packages (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(20) NOT NULL,
    hotel_id BIGINT NULL REFERENCES hotels(id),
    branch_id BIGINT NULL REFERENCES branches(id),
    created_by BIGINT NULL REFERENCES users(id),
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    summary VARCHAR(280) NOT NULL,
    description VARCHAR(2000),
    inclusions JSONB NOT NULL DEFAULT '[]'::jsonb,
    eligible_room_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    terms_and_conditions VARCHAR(2500),
    image_url VARCHAR(1000),
    discount_type VARCHAR(30) NOT NULL,
    discount_value NUMERIC(12,2) NOT NULL,
    discount_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    max_discount_amount NUMERIC(12,2),
    minimum_room_subtotal NUMERIC(12,2),
    booking_start_date DATE NOT NULL,
    booking_end_date DATE NOT NULL,
    stay_start_date DATE NOT NULL,
    stay_end_date DATE NOT NULL,
    minimum_nights INTEGER NOT NULL DEFAULT 1,
    maximum_nights INTEGER,
    minimum_advance_days INTEGER NOT NULL DEFAULT 0,
    max_bookings INTEGER,
    times_booked INTEGER NOT NULL DEFAULT 0,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_package_scope CHECK (
        (scope = 'GLOBAL' AND hotel_id IS NULL AND branch_id IS NULL) OR
        (scope = 'HOTEL' AND hotel_id IS NOT NULL AND branch_id IS NULL) OR
        (scope = 'BRANCH' AND hotel_id IS NOT NULL AND branch_id IS NOT NULL)
    ),
    CONSTRAINT chk_package_discount_positive CHECK (discount_value > 0),
    CONSTRAINT chk_package_booking_dates CHECK (booking_end_date >= booking_start_date),
    CONSTRAINT chk_package_stay_dates CHECK (stay_end_date > stay_start_date),
    CONSTRAINT chk_package_nights CHECK (
        minimum_nights >= 1 AND (maximum_nights IS NULL OR maximum_nights >= minimum_nights)
    ),
    CONSTRAINT chk_package_advance_days CHECK (minimum_advance_days >= 0),
    CONSTRAINT chk_package_usage CHECK (
        times_booked >= 0 AND (max_bookings IS NULL OR max_bookings > 0)
    )
);

CREATE INDEX idx_packages_hotel ON off_season_packages(hotel_id);
CREATE INDEX idx_packages_branch ON off_season_packages(branch_id);
CREATE INDEX idx_packages_public_window ON off_season_packages(active, booking_end_date, stay_start_date);

ALTER TABLE bookings
    ADD COLUMN off_season_package_id BIGINT NULL REFERENCES off_season_packages(id) ON DELETE SET NULL,
    ADD COLUMN package_code VARCHAR(40),
    ADD COLUMN package_name VARCHAR(120),
    ADD COLUMN package_discount NUMERIC(12,2) NOT NULL DEFAULT 0;

CREATE INDEX idx_bookings_off_season_package ON bookings(off_season_package_id);
