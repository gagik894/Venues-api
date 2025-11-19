/*
 * Migration: Create reference location tables (regions and cities)
 *
 * Purpose:
 * Creates foundational location reference data tables for the venue system.
 * These tables store administrative regions and cities with multilingual support.
 *
 * Design Decisions:
 * 1. Table Prefix 'ref_': Denotes reference/lookup data (rarely changes)
 * 2. JSONB for names: Flexible multilingual support without schema changes
 * 3. Indexes: Optimized for common queries (code, slug, region lookups)
 * 4. Soft Delete: is_active flag preserves historical data integrity
 * 5. Display Order: Allows custom UI ordering without alphabetical constraints
 *
 * Dependencies: None (foundation tables)
 * Impacts: All modules that require location data (venue, event, booking)
 */

-- ============================================
-- REGIONS TABLE
-- ============================================

-- Administrative regions (provinces/states)
-- Examples: Yerevan, Shirak, Lori, etc.
CREATE TABLE ref_regions
(
    id               BIGSERIAL PRIMARY KEY,

    -- ISO or government-standard code (e.g., "AM-ER" for Yerevan)
    -- Critical for interoperability with other government systems
    code             VARCHAR(10) NOT NULL UNIQUE,

    -- Multilingual names stored as JSONB
    -- Format: {"hy": "Երևան", "en": "Yerevan", "ru": "Ереван"}
    -- At least "hy" and "en" should be provided
    names            JSONB       NOT NULL,

    -- Optional display order for UI sorting
    -- Lower numbers appear first; NULL values sorted last
    display_order    INTEGER,

    -- Soft-delete flag (preserve historical data)
    is_active        BOOLEAN     NOT NULL DEFAULT true,

    -- Audit timestamps (managed by Spring Data JPA auditing)
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast code lookups (used by other government systems)
CREATE INDEX idx_region_code ON ref_regions (code);

-- Index for active region filtering
CREATE INDEX idx_region_active ON ref_regions (is_active);

-- Comment for documentation
COMMENT ON TABLE ref_regions IS 'Administrative regions (provinces/states) with multilingual support';
COMMENT ON COLUMN ref_regions.code IS 'ISO/government code for interoperability (e.g., AM-ER)';
COMMENT ON COLUMN ref_regions.names IS 'Multilingual region names as JSONB {"hy": "...", "en": "..."}';
COMMENT ON COLUMN ref_regions.display_order IS 'Optional UI sort order (lower first)';
COMMENT ON COLUMN ref_regions.is_active IS 'Soft-delete flag for historical data integrity';

-- ============================================
-- CITIES TABLE
-- ============================================

-- Cities/communities linked to regions
-- Examples: Gyumri (→ Shirak), Yerevan (→ Yerevan), Vanadzor (→ Lori)
CREATE TABLE ref_cities
(
    id               BIGSERIAL PRIMARY KEY,

    -- Parent administrative region (mandatory relationship)
    region_id        BIGINT       NOT NULL REFERENCES ref_regions (id) ON DELETE RESTRICT,

    -- URL-friendly slug for API endpoints
    -- Must be lowercase, alphanumeric with hyphens only
    -- Used in URLs: /api/v1/cities/{slug}
    slug             VARCHAR(100) NOT NULL UNIQUE,

    -- Multilingual names stored as JSONB
    -- Format: {"hy": "Գյումրի", "en": "Gyumri", "ru": "Гюмри"}
    names            JSONB        NOT NULL,

    -- Optional official cadastre/government ID for integration
    -- Links to National Statistics Service, Cadastre Committee, etc.
    official_id      VARCHAR(50),

    -- Optional display order for UI sorting
    display_order    INTEGER,

    -- Soft-delete flag (preserve historical data)
    is_active        BOOLEAN      NOT NULL DEFAULT true,

    -- Audit timestamps (managed by Spring Data JPA auditing)
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast slug lookups (primary API access pattern)
CREATE UNIQUE INDEX idx_city_slug ON ref_cities (slug);

-- Index for region-filtered city queries
CREATE INDEX idx_city_region ON ref_cities (region_id);

-- Index for active city filtering
CREATE INDEX idx_city_active ON ref_cities (is_active);

-- Composite index for common query: active cities in a region
CREATE INDEX idx_city_region_active ON ref_cities (region_id, is_active);

-- GIN index for JSONB name searches (multilingual)
-- Enables efficient querying across all language names
CREATE INDEX idx_city_names_gin ON ref_cities USING GIN (names);

-- Comment for documentation
COMMENT ON TABLE ref_cities IS 'Cities/communities linked to administrative regions';
COMMENT ON COLUMN ref_cities.region_id IS 'Parent administrative region (mandatory)';
COMMENT ON COLUMN ref_cities.slug IS 'URL-friendly identifier for API endpoints';
COMMENT ON COLUMN ref_cities.names IS 'Multilingual city names as JSONB {"hy": "...", "en": "..."}';
COMMENT ON COLUMN ref_cities.official_id IS 'Optional cadastre/government ID for integration';
COMMENT ON COLUMN ref_cities.display_order IS 'Optional UI sort order (lower first)';
COMMENT ON COLUMN ref_cities.is_active IS 'Soft-delete flag for historical data integrity';

-- ============================================
-- SEED DATA (Armenian Regions and Major Cities)
-- ============================================

-- Insert Armenian regions (provinces + Yerevan)
-- Based on ISO 3166-2:AM standard
INSERT INTO ref_regions (code, names, display_order, is_active)
VALUES
    -- Capital city (special administrative status)
    ('AM-ER', '{
      "hy": "Երևան",
      "en": "Yerevan",
      "ru": "Ереван"
    }', 1, true),

    -- Provinces (մարզեր) - alphabetical by English name
    ('AM-AR', '{
      "hy": "Արագածոտն",
      "en": "Aragatsotn",
      "ru": "Арагацотн"
    }', 2, true),
    ('AM-AV', '{
      "hy": "Արարատ",
      "en": "Ararat",
      "ru": "Арарат"
    }', 3, true),
    ('AM-AR', '{
      "hy": "Արմավիր",
      "en": "Armavir",
      "ru": "Армавир"
    }', 4, true),
    ('AM-GR', '{
      "hy": "Գեղարքունիք",
      "en": "Gegharkunik",
      "ru": "Гехаркуник"
    }', 5, true),
    ('AM-KT', '{
      "hy": "Կոտայք",
      "en": "Kotayk",
      "ru": "Котайк"
    }', 6, true),
    ('AM-LO', '{
      "hy": "Լոռի",
      "en": "Lori",
      "ru": "Лори"
    }', 7, true),
    ('AM-SH', '{
      "hy": "Շիրակ",
      "en": "Shirak",
      "ru": "Ширак"
    }', 8, true),
    ('AM-SU', '{
      "hy": "Սյունիք",
      "en": "Syunik",
      "ru": "Сюник"
    }', 9, true),
    ('AM-TV', '{
      "hy": "Տավուշ",
      "en": "Tavush",
      "ru": "Тавуш"
    }', 10, true),
    ('AM-VD', '{
      "hy": "Վայոց Ձոր",
      "en": "Vayots Dzor",
      "ru": "Вайоц-Дзор"
    }', 11, true);

-- Insert major Armenian cities
-- Priority: Capital, regional centers, major tourist destinations
INSERT INTO ref_cities (region_id, slug, names, display_order, is_active)
VALUES
    -- Capital
    ((SELECT id FROM ref_regions WHERE code = 'AM-ER'),
     'yerevan',
     '{
       "hy": "Երևան",
       "en": "Yerevan",
       "ru": "Ереван"
     }',
     1, true),

    -- Regional centers (մարզկենտրոններ)
    ((SELECT id FROM ref_regions WHERE code = 'AM-SH'),
     'gyumri',
     '{
       "hy": "Գյումրի",
       "en": "Gyumri",
       "ru": "Гюмри"
     }',
     2, true),

    ((SELECT id FROM ref_regions WHERE code = 'AM-LO'),
     'vanadzor',
     '{
       "hy": "Վանաձոր",
       "en": "Vanadzor",
       "ru": "Ванадзор"
     }',
     3, true),

    ((SELECT id FROM ref_regions WHERE code = 'AM-GR'),
     'gavar',
     '{
       "hy": "Գավառ",
       "en": "Gavar",
       "ru": "Гавар"
     }',
     4, true),

    ((SELECT id FROM ref_regions WHERE code = 'AM-KT'),
     'hrazdan',
     '{
       "hy": "Հրազդան",
       "en": "Hrazdan",
       "ru": "Раздан"
     }',
     5, true),

    -- Major tourist destinations
    ((SELECT id FROM ref_regions WHERE code = 'AM-TV'),
     'dilijan',
     '{
       "hy": "Դիլիջան",
       "en": "Dilijan",
       "ru": "Дилижан"
     }',
     10, true),

    ((SELECT id FROM ref_regions WHERE code = 'AM-GR'),
     'sevan',
     '{
       "hy": "Սևան",
       "en": "Sevan",
       "ru": "Севан"
     }',
     11, true),

    ((SELECT id FROM ref_regions WHERE code = 'AM-AV'),
     'echmiadzin',
     '{
       "hy": "Էջմիածին",
       "en": "Echmiadzin",
       "ru": "Эчмиадзин"
     }',
     12, true);

-- ============================================
-- VERIFICATION QUERIES (Run after migration)
-- ============================================

-- Verify region count (should be 11)
-- SELECT COUNT(*) as region_count FROM ref_regions WHERE is_active = true;

-- Verify city count (should be 8)
-- SELECT COUNT(*) as city_count FROM ref_cities WHERE is_active = true;

-- Verify all cities have valid regions
-- SELECT c.slug, r.code
-- FROM ref_cities c
-- JOIN ref_regions r ON c.region_id = r.id
-- ORDER BY c.display_order;

-- Test JSONB search (should return Gyumri)
-- SELECT names->>'hy' as name_armenian
-- FROM ref_cities
-- WHERE slug = 'gyumri';

