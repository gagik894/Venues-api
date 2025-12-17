-- Adds uniqueness constraint for venue custom domains (ignores NULLs)
-- Ensures white-label routing resolves to a single venue per domain.

-- Safe re-run: IF NOT EXISTS
CREATE UNIQUE INDEX IF NOT EXISTS idx_venues_custom_domain_unique
    ON venues (custom_domain)
    WHERE custom_domain IS NOT NULL;

/*
Verification:
SELECT custom_domain, COUNT(*)
FROM venues
WHERE custom_domain IS NOT NULL
GROUP BY custom_domain
HAVING COUNT(*) > 1;
*/
