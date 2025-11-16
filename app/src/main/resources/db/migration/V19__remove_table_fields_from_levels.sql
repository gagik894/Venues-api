-- Migration: V19 Fix table booking architecture
-- Description:
--   1. Removes table_booking_mode and table_capacity from levels table
--   2. Adds booking_mode to session_table_configs where it belongs
--
-- Architectural Note:
-- The levels table is a STATIC structure (seating chart skeleton).
-- Session-specific booking rules (pricing, availability, booking modes)
-- belong in session_table_configs.

-- ========================================
-- PART 1: Add booking_mode to session_table_configs
-- ========================================

ALTER TABLE session_table_configs
    ADD COLUMN booking_mode VARCHAR(20) NOT NULL DEFAULT 'FLEXIBLE';

ALTER TABLE session_table_configs
    ADD CONSTRAINT chk_session_table_booking_mode
        CHECK (booking_mode IN ('SEATS_ONLY', 'TABLE_ONLY', 'FLEXIBLE'));

CREATE INDEX idx_session_table_config_booking_mode ON session_table_configs (booking_mode);

COMMENT ON COLUMN session_table_configs.booking_mode IS 'How the table can be booked: SEATS_ONLY (individual seats only), TABLE_ONLY (entire table only), or FLEXIBLE (either way)';

-- ========================================
-- PART 2: Remove session-specific fields from levels
-- ========================================

-- Drop constraints first
ALTER TABLE levels
    DROP CONSTRAINT IF EXISTS chk_table_booking_mode;

ALTER TABLE levels
    DROP CONSTRAINT IF EXISTS chk_table_capacity;

-- Drop columns
ALTER TABLE levels
    DROP COLUMN IF EXISTS table_booking_mode,
    DROP COLUMN IF EXISTS table_capacity;

-- ========================================
-- Verification
-- ========================================

-- Verify the levels table no longer has session-specific fields
SELECT 'levels table cleanup' as check_name,
       COUNT(*)               as unwanted_columns_found
FROM information_schema.columns
WHERE table_name = 'levels'
  AND column_name IN ('table_booking_mode', 'table_capacity');
-- Should return 0

-- Verify session_table_configs has booking_mode
SELECT 'session_table_configs has booking_mode' as check_name,
       COUNT(*)                                 as column_exists
FROM information_schema.columns
WHERE table_name = 'session_table_configs'
  AND column_name = 'booking_mode';
-- Should return 1

