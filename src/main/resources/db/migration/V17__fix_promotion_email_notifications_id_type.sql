-- Fix id type for promotion_email_notifications to match JPA Long identity mapping

ALTER TABLE promotion_email_notifications
    ALTER COLUMN id TYPE BIGINT;
