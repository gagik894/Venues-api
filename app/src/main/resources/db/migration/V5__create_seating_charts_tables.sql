-- ================================================================
-- Venues API - Seating Charts Module Database Migration
-- Version: 5
-- Description: Create tables for seating charts, levels, and seats
-- ================================================================

-- ================================================================
-- SEATING CHARTS
-- ================================================================

CREATE TABLE seating_charts
(
    id                   BIGSERIAL PRIMARY KEY,
    venue_id             BIGINT       NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    name                 VARCHAR(255) NOT NULL,
    seat_indicator_size  INTEGER      NOT NULL DEFAULT 1,
    level_indicator_size INTEGER      NOT NULL DEFAULT 1,
    background_url       VARCHAR(500),
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_seating_chart_venue_id ON seating_charts (venue_id);
CREATE INDEX idx_seating_chart_name ON seating_charts (name);

-- ================================================================
-- LEVELS (Sections/Areas)
-- ================================================================

CREATE TABLE levels
(
    id               BIGSERIAL PRIMARY KEY,
    parent_level_id  BIGINT REFERENCES levels (id) ON DELETE CASCADE,
    level_name       VARCHAR(255) NOT NULL,
    level_identifier VARCHAR(50),
    level_number     INTEGER,
    position_x       DOUBLE PRECISION,
    position_y       DOUBLE PRECISION,
    capacity         INTEGER,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_level_parent_id ON levels (parent_level_id);
CREATE INDEX idx_level_identifier ON levels (level_identifier);

-- Level <-> SeatingChart join table (M:N)
CREATE TABLE level_seating_charts
(
    level_id         BIGINT NOT NULL REFERENCES levels (id) ON DELETE CASCADE,
    seating_chart_id BIGINT NOT NULL REFERENCES seating_charts (id) ON DELETE CASCADE,
    PRIMARY KEY (level_id, seating_chart_id)
);

CREATE INDEX idx_level_seating_charts_level ON level_seating_charts (level_id);
CREATE INDEX idx_level_seating_charts_chart ON level_seating_charts (seating_chart_id);

-- Level translations
CREATE TABLE level_translations
(
    id               BIGSERIAL PRIMARY KEY,
    level_id         BIGINT       NOT NULL REFERENCES levels (id) ON DELETE CASCADE,
    language         VARCHAR(10)  NOT NULL,
    level_label      VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_level_translation_level_language UNIQUE (level_id, language)
);

CREATE INDEX idx_level_translation_level_id ON level_translations (level_id);
CREATE INDEX idx_level_translation_language ON level_translations (language);

-- ================================================================
-- SEATS
-- ================================================================

CREATE TABLE seats
(
    id               BIGSERIAL PRIMARY KEY,
    level_id         BIGINT      NOT NULL REFERENCES levels (id) ON DELETE CASCADE,
    seat_identifier  VARCHAR(50) NOT NULL,
    seat_number      VARCHAR(50),
    row_label        VARCHAR(50),
    position_x       DOUBLE PRECISION,
    position_y       DOUBLE PRECISION,
    seat_type        VARCHAR(50),
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_seat_level_identifier UNIQUE (level_id, seat_identifier)
);

CREATE INDEX idx_seat_level_id ON seats (level_id);
CREATE INDEX idx_seat_identifier ON seats (seat_identifier);
CREATE INDEX idx_seat_type ON seats (seat_type);

-- Seat <-> SeatingChart join table (M:N)
CREATE TABLE seat_seating_charts
(
    seat_id          BIGINT NOT NULL REFERENCES seats (id) ON DELETE CASCADE,
    seating_chart_id BIGINT NOT NULL REFERENCES seating_charts (id) ON DELETE CASCADE,
    PRIMARY KEY (seat_id, seating_chart_id)
);

CREATE INDEX idx_seat_seating_charts_seat ON seat_seating_charts (seat_id);
CREATE INDEX idx_seat_seating_charts_chart ON seat_seating_charts (seating_chart_id);

-- Seat translations
CREATE TABLE seat_translations
(
    id               BIGSERIAL PRIMARY KEY,
    seat_id          BIGINT       NOT NULL REFERENCES seats (id) ON DELETE CASCADE,
    language         VARCHAR(10)  NOT NULL,
    label            VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_seat_translation_seat_language UNIQUE (seat_id, language)
);

CREATE INDEX idx_seat_translation_seat_id ON seat_translations (seat_id);
CREATE INDEX idx_seat_translation_language ON seat_translations (language);

-- ================================================================
-- UPDATE EVENTS TABLE
-- ================================================================

-- Add index for seating chart FK (column already created by Hibernate)
CREATE INDEX idx_event_seating_chart_id ON events (seating_chart_id);

-- ================================================================
-- COMMENTS
-- ================================================================

COMMENT ON TABLE seating_charts IS 'Seating chart templates for venue layouts';
COMMENT ON TABLE levels IS 'Sections/areas in seating charts (can be GA or seated)';
COMMENT ON TABLE seats IS 'Individual bookable seats';
COMMENT ON TABLE level_seating_charts IS 'Many-to-many relationship between levels and charts';
COMMENT ON TABLE seat_seating_charts IS 'Many-to-many relationship between seats and charts';
COMMENT ON TABLE level_translations IS 'Multi-language labels for levels/sections';
COMMENT ON TABLE seat_translations IS 'Multi-language labels for individual seats';

COMMENT ON COLUMN levels.capacity IS 'Capacity for GA sections (null for seated sections)';
COMMENT ON COLUMN levels.position_x IS 'X coordinate for rendering section on chart';
COMMENT ON COLUMN levels.position_y IS 'Y coordinate for rendering section on chart';
COMMENT ON COLUMN seats.seat_identifier IS 'Unique identifier for API communication (indexed for performance)';
COMMENT ON COLUMN seats.position_x IS 'X coordinate for rendering seat on chart';
COMMENT ON COLUMN seats.position_y IS 'Y coordinate for rendering seat on chart';
COMMENT ON COLUMN seats.seat_type IS 'Default seat type (can be overridden per event)';

