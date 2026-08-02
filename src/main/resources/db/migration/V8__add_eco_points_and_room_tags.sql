ALTER TABLE users
    ADD COLUMN IF NOT EXISTS eco_points INTEGER NOT NULL DEFAULT 0;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS eco_points_earned INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS room_tags (
    room_id BIGINT NOT NULL,
    tag VARCHAR(50) NOT NULL,
    CONSTRAINT fk_room_tags_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT uq_room_tags_room_tag UNIQUE (room_id, tag)
);
