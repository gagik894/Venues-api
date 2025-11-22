/*
 * Add is_anchor column to event_price_templates.
 * This flag indicates if the template corresponds to a physical seating chart category (e.g. "VIP", "Standard").
 * Anchor templates should not be deleted by the user as they are tied to the physical layout.
 */

-- 1. Add column as nullable first
ALTER TABLE event_price_templates
    ADD COLUMN is_anchor BOOLEAN;

-- 2. Backfill existing records
-- We default to FALSE for safety.
UPDATE event_price_templates
SET is_anchor = FALSE
WHERE is_anchor IS NULL;

-- 3. Add NOT NULL constraint
ALTER TABLE event_price_templates
    ALTER COLUMN is_anchor SET NOT NULL;
ALTER TABLE event_price_templates
    ALTER COLUMN is_anchor SET DEFAULT FALSE;
