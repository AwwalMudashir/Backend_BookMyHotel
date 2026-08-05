-- Migration to add public IDs to sensitive entities for URL security
-- Prevents exposing database IDs in URLs

-- Add paymentId to payments table
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_id VARCHAR(8) UNIQUE NOT NULL DEFAULT '';

-- Add publicId to rooms table
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS public_id VARCHAR(8) UNIQUE NOT NULL DEFAULT '';

-- Add publicId to hotels table
ALTER TABLE hotels ADD COLUMN IF NOT EXISTS public_id VARCHAR(8) UNIQUE NOT NULL DEFAULT '';

-- Add publicId to branches table
ALTER TABLE branches ADD COLUMN IF NOT EXISTS public_id VARCHAR(8) UNIQUE NOT NULL DEFAULT '';

-- Create function to generate random public IDs (8 chars, alphanumeric)
CREATE OR REPLACE FUNCTION generate_public_id() RETURNS VARCHAR AS $$
DECLARE
  chars VARCHAR := 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  result VARCHAR := '';
  i INT;
BEGIN
  FOR i IN 1..8 LOOP
    result := result || substr(chars, floor(random() * length(chars)) + 1, 1);
  END LOOP;
  RETURN result;
END;
$$ LANGUAGE plpgsql;

-- Populate existing records with unique public IDs
UPDATE payments SET payment_id = generate_public_id() WHERE payment_id = '';
UPDATE rooms SET public_id = generate_public_id() WHERE public_id = '';
UPDATE hotels SET public_id = generate_public_id() WHERE public_id = '';
UPDATE branches SET public_id = generate_public_id() WHERE public_id = '';

-- Add NOT NULL constraint after populating
ALTER TABLE payments ALTER COLUMN payment_id SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE hotels ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE branches ALTER COLUMN public_id SET NOT NULL;

-- Drop the function as it's no longer needed
DROP FUNCTION IF EXISTS generate_public_id();
