-- =====================================================
-- V13: Refactor Seating Chart Relationships
-- =====================================================
-- Remove ManyToMany relationships and use proper FK columns
-- This improves performance and follows clean architecture

-- Add seating_chart_id column to levels table
ALTER TABLE levels
    ADD COLUMN seating_chart_id BIGINT;

-- Populate seating_chart_id from existing level_seating_charts join table
UPDATE levels l
SET seating_chart_id = (SELECT lsc.seating_chart_id
                        FROM level_seating_charts lsc
                        WHERE lsc.level_id = l.id
                        LIMIT 1);

-- Make seating_chart_id NOT NULL after data migration
ALTER TABLE levels
    ALTER COLUMN seating_chart_id SET NOT NULL;

-- Add foreign key constraint
ALTER TABLE levels
    ADD CONSTRAINT fk_level_seating_chart
        FOREIGN KEY (seating_chart_id)
            REFERENCES seating_charts (id)
            ON DELETE CASCADE;

-- Add index for performance
CREATE INDEX idx_level_seating_chart_id ON levels (seating_chart_id);

-- Drop the old ManyToMany join tables
DROP TABLE IF EXISTS level_seating_charts;
DROP TABLE IF EXISTS seat_seating_charts;

-- Add comment
COMMENT ON COLUMN levels.seating_chart_id IS 'FK to seating_charts - replaces ManyToMany relationship';

