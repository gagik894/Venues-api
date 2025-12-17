/*
  Flyway Migration V2
  Description: Seed initial reference data (Regions, Cities, Categories, Platform)
  Context: Armenia (AM)
*/

-- ==================================================================
-- 1. DEFAULT PLATFORM (User Provided)
-- ==================================================================
INSERT INTO platforms (id, name, status, api_url, shared_secret, webhook_enabled,
                       webhook_failure_count, webhook_success_count, created_at, last_modified_at)
VALUES ('11111111-1111-1111-1111-111111111111', -- Hardcoded UUID for internal client
        'Venues Web Client',
        'ACTIVE',
        'https://venues.app/api/hooks',
        'secret-key-123',
        false,
        0,
        0,
        NOW(),
        NOW())
ON CONFLICT (name) DO NOTHING;

-- ==================================================================
-- 2. VENUE CATEGORIES (ref_venue_categories)
-- ==================================================================
INSERT INTO ref_venue_categories (code, names, icon, color, display_order, is_active, created_at, last_modified_at)
VALUES ('THEATER', '{
  "en": "Theater",
  "hy": "Թատրոն",
  "ru": "Театр"
}', 'theater-masks', '#D81B60', 10, true, NOW(), NOW()),
       ('CONCERT_HALL', '{
         "en": "Concert Hall",
         "hy": "Համերգասրահ",
         "ru": "Концертный зал"
       }', 'music', '#8E24AA', 20, true, NOW(), NOW()),
       ('OPERA', '{
         "en": "Opera House",
         "hy": "Օպերա",
         "ru": "Опера"
       }', 'landmark', '#E53935', 30, true, NOW(), NOW()),
       ('STADIUM', '{
         "en": "Stadium",
         "hy": "Մարզադաշտ",
         "ru": "Стадион"
       }', 'futbol', '#43A047', 40, true, NOW(), NOW()),
       ('CLUB', '{
         "en": "Club",
         "hy": "Ակումբ",
         "ru": "Клуб"
       }', 'glass-cheers', '#1E88E5', 50, true, NOW(), NOW()),
       ('MUSEUM', '{
         "en": "Museum",
         "hy": "Թանգարան",
         "ru": "Музей"
       }', 'palette', '#FB8C00', 60, true, NOW(), NOW()),
       ('CINEMA', '{
         "en": "Cinema",
         "hy": "Կինոթատրոն",
         "ru": "Кинотеатр"
       }', 'film', '#546E7A', 70, true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- ==================================================================
-- 3. REGIONS (ref_regions) - Administrative Divisions
-- ==================================================================
INSERT INTO ref_regions (code, names, display_order, is_active, created_at, last_modified_at)
VALUES ('AM-ER', '{
  "en": "Yerevan",
  "hy": "Երևան",
  "ru": "Ереван"
}', 1, true, NOW(), NOW()),
       ('AM-SH', '{
         "en": "Shirak",
         "hy": "Շիրակ",
         "ru": "Ширак"
       }', 2, true, NOW(), NOW()),
       ('AM-LO', '{
         "en": "Lori",
         "hy": "Լոռի",
         "ru": "Лори"
       }', 3, true, NOW(), NOW()),
       ('AM-TV', '{
         "en": "Tavush",
         "hy": "Տավուշ",
         "ru": "Тавуш"
       }', 4, true, NOW(), NOW()),
       ('AM-KT', '{
         "en": "Kotayk",
         "hy": "Կոտայք",
         "ru": "Котайк"
       }', 5, true, NOW(), NOW()),
       ('AM-AR', '{
         "en": "Ararat",
         "hy": "Արարատ",
         "ru": "Арарат"
       }', 6, true, NOW(), NOW()),
       ('AM-AV', '{
         "en": "Armavir",
         "hy": "Արմավիր",
         "ru": "Армавир"
       }', 7, true, NOW(), NOW()),
       ('AM-GR', '{
         "en": "Gegharkunik",
         "hy": "Գեղարքունիք",
         "ru": "Гехаркуник"
       }', 8, true, NOW(), NOW()),
       ('AM-SU', '{
         "en": "Syunik",
         "hy": "Սյունիք",
         "ru": "Сюник"
       }', 9, true, NOW(), NOW()),
       ('AM-VD', '{
         "en": "Vayots Dzor",
         "hy": "Վայոց Ձոր",
         "ru": "Вайоц Дзор"
       }', 10, true, NOW(), NOW()),
       ('AM-AG', '{
         "en": "Aragatsotn",
         "hy": "Արագածոտն",
         "ru": "Арагацотн"
       }', 11, true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- ==================================================================
-- 4. CITIES (ref_cities)
-- ==================================================================
INSERT INTO ref_cities (region_id, slug, names, display_order, is_active, created_at, last_modified_at)
SELECT id, 'yerevan', '{"en": "Yerevan", "hy": "Երևան", "ru": "Ереван"}', 10, true, NOW(), NOW()
FROM ref_regions
WHERE code = 'AM-ER'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO ref_cities (region_id, slug, names, display_order, is_active, created_at, last_modified_at)
SELECT id, 'gyumri', '{"en": "Gyumri", "hy": "Գյումրի", "ru": "Гюмри"}', 20, true, NOW(), NOW()
FROM ref_regions
WHERE code = 'AM-SH'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO ref_cities (region_id, slug, names, display_order, is_active, created_at, last_modified_at)
SELECT id, 'vanadzor', '{"en": "Vanadzor", "hy": "Վանաձոր", "ru": "Ванадзор"}', 30, true, NOW(), NOW()
FROM ref_regions
WHERE code = 'AM-LO'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO ref_cities (region_id, slug, names, display_order, is_active, created_at, last_modified_at)
SELECT id, 'dilijan', '{"en": "Dilijan", "hy": "Դիլիջան", "ru": "Дилижан"}', 40, true, NOW(), NOW()
FROM ref_regions
WHERE code = 'AM-TV'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO ref_cities (region_id, slug, names, display_order, is_active, created_at, last_modified_at)
SELECT id, 'tsaghkadzor', '{"en": "Tsaghkadzor", "hy": "Ծաղկաձոր", "ru": "Цахкадзор"}', 50, true, NOW(), NOW()
FROM ref_regions
WHERE code = 'AM-KT'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO ref_cities (region_id, slug, names, display_order, is_active, created_at, last_modified_at)
SELECT id, 'goris', '{"en": "Goris", "hy": "Գորիս", "ru": "Горис"}', 60, true, NOW(), NOW()
FROM ref_regions
WHERE code = 'AM-SU'
ON CONFLICT (slug) DO NOTHING;

-- ==================================================================
-- 5. EVENT CATEGORIES (Modified to match Venue Category Style)
-- ==================================================================
INSERT INTO ref_event_categories (code, names, icon, color, display_order, is_active, created_at, last_modified_at)
VALUES ('CONCERT', '{
         "en": "Concerts",
         "hy": "Համերգներ",
         "ru": "Концерты"
       }', 'music', '#FF5733', 10, true, NOW(), NOW()),
       ('THEATER', '{
         "en": "Theater",
         "hy": "Թատրոն",
         "ru": "Театр"
       }', 'masks', '#3357FF', 20, true, NOW(), NOW()),
       ('SPORT', '{
         "en": "Sports",
         "hy": "Սպորտ",
         "ru": "Спорт"
       }', 'trophy', '#33FF57', 30, true, NOW(), NOW()),
       ('STANDUP', '{
         "en": "Stand Up",
         "hy": "Սթենդ-ափ",
         "ru": "Стендап"
       }', 'microphone', '#FFC300', 40, true, NOW(), NOW()),
       ('FESTIVAL', '{
         "en": "Festivals",
         "hy": "Փառատոններ",
         "ru": "Фестивали"
       }', 'flag', '#DAF7A6', 50, true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;