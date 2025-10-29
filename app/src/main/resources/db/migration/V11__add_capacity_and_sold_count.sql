-- ================================================================
-- Venues API - Event Module Database Migration
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

-- Add check constraints (drop first if exists to avoid errors)
DO
$$
    BEGIN
        -- Drop existing constraints if they exist
        ALTER TABLE session_level_configs
            DROP CONSTRAINT IF EXISTS chk_sold_count_within_capacity;
        ALTER TABLE session_level_configs
            DROP CONSTRAINT IF EXISTS chk_sold_count_non_negative;
        ALTER TABLE session_level_configs
            DROP CONSTRAINT IF EXISTS chk_capacity_positive;

        -- Add constraints
        ALTER TABLE session_level_configs
            ADD CONSTRAINT chk_sold_count_within_capacity
                CHECK (capacity IS NULL OR sold_count <= capacity);

        ALTER TABLE session_level_configs
            ADD CONSTRAINT chk_sold_count_non_negative
                CHECK (sold_count >= 0);

        ALTER TABLE session_level_configs
            ADD CONSTRAINT chk_capacity_positive
                CHECK (capacity IS NULL OR capacity > 0);
    END
$$;

-- Add comments
COMMENT ON COLUMN session_level_configs.capacity IS 'Session-specific GA capacity - can differ per session for same level';
COMMENT ON COLUMN session_level_configs.sold_count IS 'Denormalized count of sold GA tickets - updated on booking confirmation/cancellation';

-- Create index for performance
CREATE INDEX IF NOT EXISTS idx_session_level_config_sold_count ON session_level_configs (sold_count);

