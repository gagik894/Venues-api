-- ================================================================
-- Venues API - Implement "Snapshot at Add to Cart" Pattern
-- Version: 14
-- Description: Remove price columns from inventory tables, add unit_price to cart tables
-- ================================================================

-- ===========================
-- PHASE 1: Remove price from inventory tables
-- ===========================

-- Remove price column from session_seat_configs
-- (Price is now only in EventPriceTemplate, snapshotted to CartSeat)
ALTER TABLE session_seat_configs
    DROP COLUMN IF EXISTS price;

-- Remove price column from session_level_configs
-- (Price is now only in EventPriceTemplate, snapshotted to CartItem)
ALTER TABLE session_level_configs
    DROP COLUMN IF EXISTS price;

-- ===========================
-- PHASE 2: Add unit_price to cart tables
-- ===========================

-- Add unit_price to cart_seats (snapshotted price)
ALTER TABLE cart_seats
    ADD COLUMN IF NOT EXISTS unit_price DECIMAL(10, 2);

-- Backfill existing records with a default price (if any exist)
-- In production, this should be set based on the current price template
UPDATE cart_seats
SET unit_price = 0.00
WHERE unit_price IS NULL;

-- Make unit_price NOT NULL after backfilling
ALTER TABLE cart_seats
    ALTER COLUMN unit_price SET NOT NULL;

-- Add unit_price to cart_items (snapshotted price per ticket)
ALTER TABLE cart_items
    ADD COLUMN IF NOT EXISTS unit_price DECIMAL(10, 2);

-- Backfill existing records with a default price (if any exist)
UPDATE cart_items
SET unit_price = 0.00
WHERE unit_price IS NULL;

-- Make unit_price NOT NULL after backfilling
ALTER TABLE cart_items
    ALTER COLUMN unit_price SET NOT NULL;

-- ===========================
-- COMMENTS
-- ===========================

COMMENT ON COLUMN cart_seats.unit_price IS 'Snapshotted price from priceTemplate at add-to-cart time. Immutable.';
COMMENT ON COLUMN cart_items.unit_price IS 'Snapshotted price per ticket from priceTemplate at add-to-cart time. Immutable. Total = unitPrice × quantity';

COMMENT ON TABLE session_seat_configs IS 'Inventory entity. Tracks seat availability and price TEMPLATE assignment only. Price is snapshotted to cart_seats.';
COMMENT ON TABLE session_level_configs IS 'Inventory entity. Tracks GA level availability and price TEMPLATE assignment only. Price is snapshotted to cart_items.';

