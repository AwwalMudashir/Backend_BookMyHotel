ALTER TABLE services
    ADD COLUMN IF NOT EXISTS hotel_id BIGINT,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE services service
SET hotel_id = branch.hotel_id
FROM branches branch
WHERE service.branch_id = branch.id
  AND service.hotel_id IS NULL;

ALTER TABLE services
    ALTER COLUMN hotel_id SET NOT NULL,
    ALTER COLUMN branch_id DROP NOT NULL;

ALTER TABLE services
    ADD CONSTRAINT fk_services_hotel
        FOREIGN KEY (hotel_id) REFERENCES hotels(id);

CREATE INDEX IF NOT EXISTS idx_services_hotel_active ON services(hotel_id, active);
CREATE INDEX IF NOT EXISTS idx_services_branch_active ON services(branch_id, active);

ALTER TABLE booking_services
    ADD COLUMN IF NOT EXISTS service_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS unit_price NUMERIC(12, 2);

UPDATE booking_services booking_service
SET service_name = service.name,
    unit_price = service.price
FROM services service
WHERE booking_service.service_id = service.id
  AND (booking_service.service_name IS NULL OR booking_service.unit_price IS NULL);

ALTER TABLE booking_services
    ALTER COLUMN service_name SET NOT NULL,
    ALTER COLUMN unit_price SET NOT NULL;
