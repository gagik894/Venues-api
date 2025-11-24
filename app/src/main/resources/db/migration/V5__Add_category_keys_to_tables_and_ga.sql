-- Add category keys to tables and GA areas so pricing defaults can be derived
ALTER TABLE chart_tables
    ADD COLUMN category_key VARCHAR(100);

ALTER TABLE chart_ga_areas
    ADD COLUMN category_key VARCHAR(100);

-- Backfill legacy records with their existing business codes
UPDATE chart_tables
SET category_key = COALESCE(category_key, code);

UPDATE chart_ga_areas
SET category_key = COALESCE(category_key, code);

-- Enforce presence going forward
ALTER TABLE chart_tables
    ALTER COLUMN category_key SET NOT NULL;

ALTER TABLE chart_ga_areas
    ALTER COLUMN category_key SET NOT NULL;

/*
Verification:
SELECT COUNT(*) FROM chart_tables WHERE category_key IS NULL;
SELECT COUNT(*) FROM chart_ga_areas WHERE category_key IS NULL;
*/
