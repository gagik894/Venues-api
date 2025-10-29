-- ================================================================
-- Venues API - Booking Module Database Migration
-- Version: 11
-- Description: Add capacity and sold_count to session_level_configs
-- ================================================================

-- Add capacity column for session-specific GA capacity
-- Different sessions can have different capacities for same area
ALTER TABLE session_level_configs
    ADD COLUMN IF NOT EXISTS capacity INTEGER;

-- Add sold_count column to track GA tickets sold
ALTER TABLE session_level_configs
    ADD COLUMN IF NOT EXISTS sold_count INTEGER NOT NULL DEFAULT 0;

-- Add check constraint to ensure sold_count doesn't exceed capacity
ALTER TABLE session_level_configs
    ADD CONSTRAINT chk_sold_count_within_capacity
        CHECK (capacity IS NULL OR sold_count <= capacity);

-- Add check constraint for non-negative values
ALTER TABLE session_level_configs
    ADD CONSTRAINT chk_sold_count_non_negative
        CHECK (sold_count >= 0);

ALTER TABLE session_level_configs
    ADD CONSTRAINT chk_capacity_positive
        CHECK (capacity IS NULL OR capacity > 0);

-- Add comments
COMMENT ON COLUMN session_level_configs.capacity IS 'Session-specific GA capacity - can differ per session for same level';
COMMENT ON COLUMN session_level_configs.sold_count IS 'Denormalized count of sold GA tickets - updated on booking confirmation/cancellation';

-- Create index for performance
CREATE INDEX IF NOT EXISTS idx_session_level_config_sold_count ON session_level_configs (sold_count);

