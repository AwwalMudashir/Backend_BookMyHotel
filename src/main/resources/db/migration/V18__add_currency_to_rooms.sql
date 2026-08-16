-- Add a per-room currency column so rooms can store their own ISO-4217 currency code.
-- Backfill existing rows with their branch's currency to preserve existing behaviour.

ALTER TABLE rooms
  ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

-- Backfill currency from branch table for existing rooms where possible
UPDATE rooms
SET currency = COALESCE((SELECT currency FROM branches WHERE branches.id = rooms.branch_id), 'USD')
WHERE currency IS NULL;
