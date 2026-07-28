ALTER TABLE branches
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

UPDATE branches b
SET currency = COALESCE(
        (
            SELECT MIN(r.currency)
            FROM rooms r
            WHERE r.branch_id = b.id
              AND r.currency IS NOT NULL
        ),
        'USD'
    )
WHERE b.currency IS NULL;

ALTER TABLE branches
    ALTER COLUMN currency SET NOT NULL;

ALTER TABLE rooms
    DROP COLUMN IF EXISTS currency;
