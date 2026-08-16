-- Create queue table for promotion email notifications

CREATE TABLE promotion_email_notifications (
    id SERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL,
    recipient_email TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    processed_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_promotion_email_notifications_status_attempts ON promotion_email_notifications (status, attempts);