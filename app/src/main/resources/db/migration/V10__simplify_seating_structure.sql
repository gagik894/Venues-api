-- ================================================================
-- Venues API - Seating Charts Module Database Migration
-- Version: 10
-- Description: Simplify seating structure - remove seat translations and level_number
-- ================================================================

-- Remove seat translations table (seat numbers are universal)
DROP TABLE IF EXISTS seat_translations CASCADE;

-- Remove level_number column (not used, redundant)
ALTER TABLE levels
    DROP COLUMN IF EXISTS level_number;

-- Update comments
COMMENT ON TABLE level_translations IS 'Multi-language names for levels/sections (e.g., "Orchestra" → "Օրկեստր")';
COMMENT ON COLUMN levels.level_name IS 'Level name in default language (e.g., "Orchestra", "Balcony")';
COMMENT ON COLUMN levels.level_identifier IS 'API-friendly identifier (e.g., "ORCH", "BALC")';
COMMENT ON COLUMN level_translations.level_label IS 'Translated level name (e.g., "Օրկեստր", "Балкон")';
COMMENT ON COLUMN seats.row_label IS 'Row label as string (e.g., "Row A", "Ряд 1") - allows flexibility';
COMMENT ON COLUMN seats.seat_number IS 'Seat number within row (e.g., "1", "12", "A5")';

