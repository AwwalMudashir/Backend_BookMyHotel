-- Migration to add public IDs to sensitive entities for URL security
-- Prevents exposing database IDs in URLs

-- Create function to generate random public IDs (8 chars, alphanumeric)
CREATE OR REPLACE FUNCTION generate_public_id() RETURNS VARCHAR AS $$
DECLARE
  chars VARCHAR := 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  result VARCHAR := '';
  i INT;
BEGIN
  FOR i IN 1..8 LOOP
    result := result || SUBSTRING(chars, (FLOOR(RANDOM() * LENGTH(chars)))::INT + 1, 1);
  END LOOP;
  RETURN result;
END;
$$ LANGUAGE plpgsql;

-- Add columns without UNIQUE constraint or DEFAULT values first
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_id VARCHAR(8);
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS public_id VARCHAR(8);
ALTER TABLE hotels ADD COLUMN IF NOT EXISTS public_id VARCHAR(8);
ALTER TABLE branches ADD COLUMN IF NOT EXISTS public_id VARCHAR(8);

-- Populate all rows (including those that may have NULL or empty values) with unique IDs
UPDATE payments SET payment_id = substring(md5(random()::text || clock_timestamp()::text || coalesce(id::text, '')), 1, 8) WHERE payment_id IS NULL OR payment_id = '';
UPDATE rooms SET public_id = substring(md5(random()::text || clock_timestamp()::text || coalesce(id::text, '')), 1, 8) WHERE public_id IS NULL OR public_id = '';
UPDATE hotels SET public_id = substring(md5(random()::text || clock_timestamp()::text || coalesce(id::text, '')), 1, 8) WHERE public_id IS NULL OR public_id = '';
UPDATE branches SET public_id = substring(md5(random()::text || clock_timestamp()::text || coalesce(id::text, '')), 1, 8) WHERE public_id IS NULL OR public_id = '';

-- Add NOT NULL and UNIQUE constraints after all rows have unique values
ALTER TABLE payments ALTER COLUMN payment_id SET NOT NULL;
ALTER TABLE payments ADD CONSTRAINT payments_payment_id_key UNIQUE (payment_id);

ALTER TABLE rooms ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE rooms ADD CONSTRAINT rooms_public_id_key UNIQUE (public_id);

ALTER TABLE hotels ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE hotels ADD CONSTRAINT hotels_public_id_key UNIQUE (public_id);

ALTER TABLE branches ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE branches ADD CONSTRAINT branches_public_id_key UNIQUE (public_id);

-- Drop the function as it's no longer needed
DROP FUNCTION IF EXISTS generate_public_id();
