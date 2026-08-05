-- Add room_id (random public identifier) and public_ids (Cloudinary public IDs array) to rooms

ALTER TABLE rooms ADD COLUMN IF NOT EXISTS room_id VARCHAR(8) UNIQUE;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS public_ids JSONB DEFAULT '[]'::jsonb;

-- Create helper to generate an 8-char public id for existing rows
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

-- Populate existing rooms where room_id is null or empty
UPDATE rooms SET room_id = generate_public_id() WHERE room_id IS NULL OR room_id = '';

-- Ensure NOT NULL on room_id for safety (only if all rows have been populated)
ALTER TABLE rooms ALTER COLUMN room_id SET NOT NULL;

-- Drop helper
DROP FUNCTION IF EXISTS generate_public_id();
