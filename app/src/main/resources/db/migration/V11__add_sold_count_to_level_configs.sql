-- ================================================================
-- Venues API - Booking Module Database Migration
-- Version: 11
-- Description: Add sold_count to session_level_configs for efficient GA tracking
-- ================================================================

-- Add sold_count column to track GA tickets sold
ALTER TABLE session_level_configs
    ADD COLUMN IF NOT EXISTS sold_count INTEGER NOT NULL DEFAULT 0;

-- Add check constraint to ensure sold_count doesn't exceed capacity
ALTER TABLE session_level_configs
    ADD CONSTRAINT chk_sold_count_within_capacity
        CHECK (sold_count >= 0);

-- Add comment
COMMENT ON COLUMN session_level_configs.sold_count IS 'Denormalized count of sold GA tickets - updated on booking confirmation/cancellation';

-- Create index for performance
CREATE INDEX IF NOT EXISTS idx_session_level_config_sold_count ON session_level_configs (sold_count);

