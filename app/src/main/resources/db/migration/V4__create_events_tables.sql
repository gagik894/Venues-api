-- ================================================================
-- Venues API - Events Module Database Migration
-- Version: 4
-- Description: Create tables for events, sessions, categories, and translations
-- ================================================================

-- ================================================================
-- EVENT CATEGORIES
-- ================================================================

CREATE TABLE event_categories
(
    id            BIGSERIAL PRIMARY KEY,
    category_key  VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    color         VARCHAR(7),
    icon          VARCHAR(100),
    display_order INTEGER      NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_category_display_order ON event_categories (display_order);
CREATE INDEX idx_event_category_active ON event_categories (is_active);

-- Category translations
CREATE TABLE event_category_translations
(
    id               BIGSERIAL PRIMARY KEY,
    category_id      BIGINT       NOT NULL REFERENCES event_categories (id) ON DELETE CASCADE,
    language         VARCHAR(10)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_category_translation_category_language UNIQUE (category_id, language)
);

CREATE INDEX idx_category_translation_category_id ON event_category_translations (category_id);
CREATE INDEX idx_category_translation_language ON event_category_translations (language);

-- ================================================================
-- EVENTS
-- ================================================================

CREATE TABLE events
(
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    img_url          VARCHAR(500),
    description      TEXT,
    venue_id         BIGINT       NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    location         VARCHAR(500),
    latitude         DOUBLE PRECISION,
    longitude        DOUBLE PRECISION,
    category_id      BIGINT       REFERENCES event_categories (id) ON DELETE SET NULL,
    price_range      VARCHAR(100),
    currency         VARCHAR(3)   NOT NULL DEFAULT 'AMD',
    seating_chart_id VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_venue_id ON events (venue_id);
CREATE INDEX idx_event_status ON events (status);
CREATE INDEX idx_event_category_id ON events (category_id);
CREATE INDEX idx_event_created_at ON events (created_at);

-- Event secondary images
CREATE TABLE event_secondary_images
(
    event_id  BIGINT       NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL
);

CREATE INDEX idx_event_secondary_images_event_id ON event_secondary_images (event_id);

-- Event tags
CREATE TABLE event_tags
(
    event_id BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    tag      VARCHAR(50) NOT NULL
);

CREATE INDEX idx_event_tags_event_id ON event_tags (event_id);
CREATE INDEX idx_event_tags_tag ON event_tags (tag);

-- Event translations
CREATE TABLE event_translations
(
    id               BIGSERIAL PRIMARY KEY,
    event_id         BIGINT       NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    language         VARCHAR(10)  NOT NULL,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_event_translation_event_language UNIQUE (event_id, language)
);

CREATE INDEX idx_event_translation_event_id ON event_translations (event_id);
CREATE INDEX idx_event_translation_language ON event_translations (language);

-- ================================================================
-- EVENT SESSIONS
-- ================================================================

CREATE TABLE event_sessions
(
    id                   BIGSERIAL PRIMARY KEY,
    event_id             BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    start_time           TIMESTAMP   NOT NULL,
    end_time             TIMESTAMP   NOT NULL,
    tickets_count        INTEGER,
    tickets_sold         INTEGER     NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL DEFAULT 'UPCOMING',
    price_override       DECIMAL(10, 2),
    price_range_override VARCHAR(100),
    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_session_event_id ON event_sessions (event_id);
CREATE INDEX idx_event_session_start_time ON event_sessions (start_time);
CREATE INDEX idx_event_session_status ON event_sessions (status);

-- Session price overrides
CREATE TABLE event_session_price_overrides
(
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT         NOT NULL REFERENCES event_sessions (id) ON DELETE CASCADE,
    template_name VARCHAR(100)   NOT NULL,
    price         DECIMAL(10, 2) NOT NULL,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_session_price_override_session_id ON event_session_price_overrides (session_id);

-- ================================================================
-- EVENT PRICE TEMPLATES
-- ================================================================

CREATE TABLE event_price_templates
(
    id            BIGSERIAL PRIMARY KEY,
    event_id      BIGINT         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    template_name VARCHAR(100)   NOT NULL,
    color         VARCHAR(7),
    price         DECIMAL(10, 2) NOT NULL,
    display_order INTEGER        NOT NULL DEFAULT 0,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_price_template_event_id ON event_price_templates (event_id);

-- ================================================================
-- SEED DATA - Sample Event Categories
-- ================================================================

INSERT INTO event_categories (category_key, name, color, icon, display_order)
VALUES ('THEATER', 'Theater', '#E91E63', 'theater-masks', 1),
       ('CONCERT', 'Concert', '#9C27B0', 'music', 2),
       ('OPERA', 'Opera', '#673AB7', 'music-note', 3),
       ('BALLET', 'Ballet', '#3F51B5', 'ballet', 4),
       ('EXHIBITION', 'Exhibition', '#2196F3', 'palette', 5),
       ('COMEDY', 'Comedy', '#FFC107', 'comedy', 6),
       ('FESTIVAL', 'Festival', '#FF5722', 'celebration', 7),
       ('WORKSHOP', 'Workshop', '#795548', 'school', 8),
       ('OTHER', 'Other', '#9E9E9E', 'event', 9);

-- Category translations (Armenian)
INSERT INTO event_category_translations (category_id, language, name)
VALUES ((SELECT id FROM event_categories WHERE category_key = 'THEATER'), 'hy', 'Թատրոն'),
       ((SELECT id FROM event_categories WHERE category_key = 'CONCERT'), 'hy', 'Համերգ'),
       ((SELECT id FROM event_categories WHERE category_key = 'OPERA'), 'hy', 'Օպերա'),
       ((SELECT id FROM event_categories WHERE category_key = 'BALLET'), 'hy', 'Բալետ'),
       ((SELECT id FROM event_categories WHERE category_key = 'EXHIBITION'), 'hy', 'Ցուցադրություն'),
       ((SELECT id FROM event_categories WHERE category_key = 'COMEDY'), 'hy', 'Կատակերգություն'),
       ((SELECT id FROM event_categories WHERE category_key = 'FESTIVAL'), 'hy', 'Փառատոն'),
       ((SELECT id FROM event_categories WHERE category_key = 'WORKSHOP'), 'hy', 'Վարպետաց դաս'),
       ((SELECT id FROM event_categories WHERE category_key = 'OTHER'), 'hy', 'Այլ');

-- Category translations (Russian)
INSERT INTO event_category_translations (category_id, language, name)
VALUES ((SELECT id FROM event_categories WHERE category_key = 'THEATER'), 'ru', 'Театр'),
       ((SELECT id FROM event_categories WHERE category_key = 'CONCERT'), 'ru', 'Концерт'),
       ((SELECT id FROM event_categories WHERE category_key = 'OPERA'), 'ru', 'Опера'),
       ((SELECT id FROM event_categories WHERE category_key = 'BALLET'), 'ru', 'Балет'),
       ((SELECT id FROM event_categories WHERE category_key = 'EXHIBITION'), 'ru', 'Выставка'),
       ((SELECT id FROM event_categories WHERE category_key = 'COMEDY'), 'ru', 'Комедия'),
       ((SELECT id FROM event_categories WHERE category_key = 'FESTIVAL'), 'ru', 'Фестиваль'),
       ((SELECT id FROM event_categories WHERE category_key = 'WORKSHOP'), 'ru', 'Мастер-класс'),
       ((SELECT id FROM event_categories WHERE category_key = 'OTHER'), 'ru', 'Другое');

-- ================================================================
-- COMMENTS
-- ================================================================

COMMENT ON TABLE events IS 'Main events table - cultural events hosted by venues';
COMMENT ON TABLE event_sessions IS 'Event sessions (time slots) - multiple showings per event';
COMMENT ON TABLE event_categories IS 'Event categories with multi-language support';
COMMENT ON TABLE event_translations IS 'Multi-language support for event titles and descriptions';
COMMENT ON TABLE event_price_templates IS 'Price templates for different ticket tiers (VIP, Standard, etc.)';

