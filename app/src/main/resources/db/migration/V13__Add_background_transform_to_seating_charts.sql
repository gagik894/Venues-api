-- Adds optional background transform metadata for seating charts
ALTER TABLE seating_charts
    ADD COLUMN IF NOT EXISTS background_transform_json TEXT;

