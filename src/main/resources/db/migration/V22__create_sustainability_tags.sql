CREATE TABLE IF NOT EXISTS sustainability_tags (
    id BIGSERIAL PRIMARY KEY,
    hotel_id BIGINT NOT NULL REFERENCES hotels(id),
    branch_id BIGINT NULL REFERENCES branches(id),
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_sustainability_tags_hotel
    ON sustainability_tags(hotel_id);

CREATE INDEX IF NOT EXISTS idx_sustainability_tags_branch
    ON sustainability_tags(branch_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_sustainability_tag_scope_name
    ON sustainability_tags(hotel_id, COALESCE(branch_id, 0), LOWER(name))
    WHERE active = TRUE;
