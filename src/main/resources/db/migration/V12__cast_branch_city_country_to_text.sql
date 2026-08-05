-- Ensure branch city/country columns are stored as text so case-insensitive search predicates
-- using LOWER(...) work reliably in PostgreSQL.
ALTER TABLE branches
    ALTER COLUMN city TYPE text USING city::text,
    ALTER COLUMN country TYPE text USING country::text;
