-- ================================================================
-- Venues API - Development Data Seed Script
-- ================================================================
-- This script populates the database with realistic mock data for testing
-- All relationships are valid and logically consistent
-- Run this script AFTER all Flyway migrations have been applied
-- ================================================================

-- Clear existing data (in correct order to respect FK constraints)
DELETE
FROM booking_items;
DELETE
FROM bookings;
DELETE
FROM guests;
DELETE
FROM cart_tables;
DELETE
FROM cart_seats;
DELETE
FROM cart_items;
DELETE
FROM session_table_configs;
DELETE
FROM session_seat_configs;
DELETE
FROM session_level_configs;
DELETE
FROM seats;
DELETE
FROM level_translations;
DELETE
FROM levels;
DELETE
FROM seating_charts;
DELETE
FROM event_categories;
DELETE
FROM event_category_translations;
DELETE
FROM event_sessions;
DELETE
FROM events;
DELETE
FROM webhook_events;
DELETE
FROM platforms;
DELETE
FROM users;
DELETE
FROM venues;

-- ================================================================
-- 1. VENUES
-- ================================================================
INSERT INTO venues (id, name, email, password_hash, address, city, category, verified, status, created_at,
                    last_modified_at)
VALUES (1, 'Yerevan Opera House', 'opera@yerevan.am', '$2a$10$dummyHashForDevelopment1234567890',
        'Tumanyan 54, Yerevan', 'Yerevan', 'OPERA_HOUSE', true, 'ACTIVE', NOW(), NOW()),
       (2, 'Gyumri Drama Theatre', 'drama@gyumri.am', '$2a$10$dummyHashForDevelopment1234567890', 'Abovyan 12, Gyumri',
        'Gyumri', 'THEATRE', true, 'ACTIVE', NOW(), NOW()),
       (3, 'Aram Khachaturian Concert Hall', 'concert@yerevan.am', '$2a$10$dummyHashForDevelopment1234567890',
        'Marshal Baghramyan Avenue 46, Yerevan', 'Yerevan', 'CONCERT_HALL', true, 'ACTIVE', NOW(), NOW());

SELECT setval('venues_id_seq', 3, true);

-- ================================================================
-- 1.1 VENUE TRANSLATIONS
-- ================================================================
INSERT INTO venue_translations (venue_id, language, name, description, created_at, last_modified_at)
VALUES
-- Yerevan Opera House - Armenian
(1, 'hy', 'Երևանի օպերայի և բալետի ազգային ակադեմիական թատրոն',
 'Հայաստանի հիմնական օպերայի և բալետի թատրոնը, որը կառուցվել է 1933 թվականին։ Հայտնի է իր բացառիկ ակուստիկայով և հարուստ մշակութային ծրագրով։',
 NOW(), NOW()),
-- Yerevan Opera House - Russian
(1, 'ru', 'Ереванский академический театр оперы и балета',
 'Главный оперный и балетный театр Армении, построенный в 1933 году. Известен своей исключительной акустикой и богатой культурной программой.',
 NOW(), NOW()),
-- Yerevan Opera House - French
(1, 'fr', 'Théâtre national académique d''opéra et de ballet d''Erevan',
 'Principal théâtre d''opéra et de ballet d''Arménie, construit en 1933. Réputé pour son acoustique exceptionnelle et sa riche programmation culturelle.',
 NOW(), NOW()),

-- Gyumri Drama Theatre - Armenian
(2, 'hy', 'Գյումրիի Վարդան Աճեմյանի անվան դրամատիկական թատրոն',
 'Հայաստանի երկրորդ ամենախոշոր դրամատիկական թատրոնը, որը հիմնադրվել է 1912 թվականին։ Ներկայացնում է ինչպես դասական, այնպես էլ ժամանակակից ներկայացումներ։',
 NOW(), NOW()),
-- Gyumri Drama Theatre - Russian
(2, 'ru', 'Гюмрийский драматический театр имени Вардана Аджемяна',
 'Второй по величине драматический театр Армении, основанный в 1912 году. Представляет как классические, так и современные постановки.',
 NOW(), NOW()),
-- Gyumri Drama Theatre - French
(2, 'fr', 'Théâtre dramatique Vardan Adjemian de Gyumri',
 'Deuxième plus grand théâtre dramatique d''Arménie, fondé en 1912. Présente des productions classiques et contemporaines.',
 NOW(), NOW()),

-- Aram Khachaturian Concert Hall - Armenian
(3, 'hy', 'Արամ Խաչատրյանի անվան համերգասրահ',
 'Երևանի կենտրոնական համերգասրահը, նվիրված հայտնի կոմպոզիտոր Արամ Խաչատրյանին։ Հյուրընկալում է սիմֆոնիկ նվագախմբի համերգներ և այլ դասական երաժշտության միջոցառումներ։',
 NOW(), NOW()),
-- Aram Khachaturian Concert Hall - Russian
(3, 'ru', 'Концертный зал имени Арама Хачатуряна',
 'Центральный концертный зал Еревана, посвященный знаменитому композитору Араму Хачатуряну. Принимает концерты симфонического оркестра и другие мероприятия классической музыки.',
 NOW(), NOW()),
-- Aram Khachaturian Concert Hall - French
(3, 'fr', 'Salle de concert Aram Khatchatourian',
 'Salle de concert centrale d''Erevan, dédiée au célèbre compositeur Aram Khatchatourian. Accueille des concerts d''orchestre symphonique et autres événements de musique classique.',
 NOW(), NOW());

-- ================================================================
-- 2. USERS
-- ================================================================
-- Password: admin123 (BCrypt hash)
INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, role, status, failed_login_attempts,
                   created_at, last_modified_at)
VALUES (1, 'admin@gov.am', '$2a$12$qUufnGELImjm6LnAbFLhyuvcenhdtDUXmbDulbp6cUXS/nP.I1tde', 'Admin', 'User',
        '+37411111111', 'ADMIN', 'ACTIVE', 0, NOW(), NOW()),
       (2, 'anna.petrosyan@example.com', '$2a$12$qUufnGELImjm6LnAbFLhyuvcenhdtDUXmbDulbp6cUXS/nP.I1tde', 'Anna',
        'Petrosyan', '+37422222222', 'USER', 'ACTIVE', 0, NOW(), NOW()),
       (3, 'karen.sargsyan@example.com', '$2a$12$qUufnGELImjm6LnAbFLhyuvcenhdtDUXmbDulbp6cUXS/nP.I1tde', 'Karen',
        'Sargsyan', '+37433333333', 'USER', 'ACTIVE', 0, NOW(), NOW()),
       (4, 'maria.khachatryan@example.com', '$2a$12$qUufnGELImjm6LnAbFLhyuvcenhdtDUXmbDulbp6cUXS/nP.I1tde', 'Maria',
        'Khachatryan', '+37444444444', 'USER', 'ACTIVE', 0, NOW(), NOW());

SELECT setval('users_id_seq', 4, true);

-- ================================================================
-- 3. PLATFORMS
-- ================================================================
INSERT INTO platforms (id, name, api_url, shared_secret, status, webhook_enabled, description, contact_email,
                       rate_limit, webhook_success_count, webhook_failure_count, created_at, last_modified_at)
VALUES (1, 'Ticketmaster Armenia', 'https://kind-mouse-84.webhook.cool',
        'a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6', 'ACTIVE', true,
        'Official Ticketmaster partner platform', 'tech@ticketmaster.am', 1000, 0, 0, NOW(), NOW()),
       (2, 'TomsTickets', 'https://test.local/api', 'z6y5x4w3v2u1t0s9r8q7p6o5n4m3l2k1j0i9h8g7f6e5d4c3b2a1',
        'ACTIVE', false, 'Tom''s Tickets booking platform', 'api@tomstickets.com', 500, 0, 0, NOW(), NOW()),
       (3, 'TestPlatform', 'https://test.local/api', 'test-secret-for-development-only', 'ACTIVE', false,
        'Test platform for development', 'test@example.com', 100, 0, 0, NOW(), NOW());

SELECT setval('platforms_id_seq', 3, true);

-- ================================================================
-- 3.1 EVENT CATEGORIES
-- ================================================================
INSERT INTO event_categories (id, category_key, name, color, icon, display_order, is_active, created_at)
VALUES (1, 'BALLET', 'Ballet', '#FF6B9D', 'ballet-icon', 1, true, NOW()),
       (2, 'OPERA', 'Opera', '#9D50BB', 'opera-icon', 2, true, NOW()),
       (3, 'THEATRE', 'Theatre', '#F39C12', 'theatre-icon', 3, true, NOW()),
       (4, 'CONCERT', 'Concert', '#3498DB', 'concert-icon', 4, true, NOW()),
       (5, 'EXHIBITION', 'Exhibition', '#2ECC71', 'exhibition-icon', 5, true, NOW()),
       (6, 'FESTIVAL', 'Festival', '#E74C3C', 'festival-icon', 6, true, NOW());

SELECT setval('event_categories_id_seq', 6, true);

-- ================================================================
-- 3.2 EVENT CATEGORY TRANSLATIONS
-- ================================================================
INSERT INTO event_category_translations (category_id, language, name, created_at, last_modified_at)
VALUES
-- Ballet translations
(1, 'hy', 'Բալետ', NOW(), NOW()),
(1, 'ru', 'Балет', NOW(), NOW()),
(1, 'fr', 'Ballet', NOW(), NOW()),

-- Opera translations
(2, 'hy', 'Օպերա', NOW(), NOW()),
(2, 'ru', 'Опера', NOW(), NOW()),
(2, 'fr', 'Opéra', NOW(), NOW()),

-- Theatre translations
(3, 'hy', 'Թատրոն', NOW(), NOW()),
(3, 'ru', 'Театр', NOW(), NOW()),
(3, 'fr', 'Théâtre', NOW(), NOW()),

-- Concert translations
(4, 'hy', 'Համերգ', NOW(), NOW()),
(4, 'ru', 'Концерт', NOW(), NOW()),
(4, 'fr', 'Concert', NOW(), NOW()),

-- Exhibition translations
(5, 'hy', 'Ցուցահանդես', NOW(), NOW()),
(5, 'ru', 'Выставка', NOW(), NOW()),
(5, 'fr', 'Exposition', NOW(), NOW()),

-- Festival translations
(6, 'hy', 'Փառատոն', NOW(), NOW()),
(6, 'ru', 'Фестиваль', NOW(), NOW()),
(6, 'fr', 'Festival', NOW(), NOW());

-- ================================================================
-- 4. EVENTS
-- ================================================================
INSERT INTO events (id, venue_id, title, description, currency, status, category_id, seating_chart_id, created_at,
                    last_modified_at)
VALUES (1, 1, 'Swan Lake', 'Pyotr Ilyich Tchaikovsky''s timeless ballet masterpiece', 'AMD', 'UPCOMING', 1, 1, NOW(),
        NOW()),
       (2, 2, 'Hamlet', 'William Shakespeare''s classic tragedy performed in Armenian', 'AMD', 'UPCOMING', 3, 2, NOW(),
        NOW()),
       (3, 3, 'Armenian National Philharmonic Orchestra',
        'An evening of classical masterworks featuring Khachaturian and Komitas', 'AMD', 'UPCOMING', 4, 3, NOW(),
        NOW()),
       (4, 3, 'Rock Night - Past Event', 'Past rock concert for testing purposes', 'AMD', 'PAST', 4, 3,
        NOW() - INTERVAL '10 days', NOW() - INTERVAL '5 days');

SELECT setval('events_id_seq', 4, true);

-- ================================================================
-- 4.1 EVENT TRANSLATIONS
-- ================================================================
INSERT INTO event_translations (event_id, language, title, description, created_at, last_modified_at)
VALUES
-- Swan Lake - Armenian
(1, 'hy', 'Կարապետ լիճ',
 'Պյոտր Իլիչ Չայկովսկու անմահ բալետային գլուխգործոցը։ Սիրո, նվիրվածության և զոհաբերության պատմություն՝ արտահայտված երաժշտության և պարի միջոցով։ Հայաստանի լավագույն բալետի վարպետների կատարմամբ։',
 NOW(), NOW()),
-- Swan Lake - Russian
(1, 'ru', 'Лебединое озеро',
 'Бессмертный балетный шедевр Петра Ильича Чайковского. История любви, преданности и жертвенности, выраженная через музыку и танец. В исполнении лучших балетных мастеров Армении.',
 NOW(), NOW()),
-- Swan Lake - French
(1, 'fr', 'Le Lac des cygnes',
 'Le chef-d''œuvre intemporel de ballet de Piotr Ilitch Tchaïkovski. Une histoire d''amour, de dévouement et de sacrifice exprimée à travers la musique et la danse. Interprété par les meilleurs maîtres du ballet arménien.',
 NOW(), NOW()),

-- Hamlet - Armenian
(2, 'hy', 'Համլետ',
 'Ուիլյամ Շեքսպիրի անմահ ողբերգությունը՝ ներկայացված հայերենով։ Իշխանության, վրեժի և մարդկային էության խորը հետազոտություն։ Ժամանակակից մեկնաբանությամբ դասական գործի նկատմամբ։',
 NOW(), NOW()),
-- Hamlet - Russian
(2, 'ru', 'Гамлет',
 'Бессмертная трагедия Уильяма Шекспира в исполнении на армянском языке. Глубокое исследование власти, мести и человеческой сущности. Современная интерпретация классического произведения.',
 NOW(), NOW()),
-- Hamlet - French
(2, 'fr', 'Hamlet',
 'La tragédie intemporelle de William Shakespeare jouée en arménien. Une exploration profonde du pouvoir, de la vengeance et de l''essence humaine. Une interprétation contemporaine d''une œuvre classique.',
 NOW(), NOW()),

-- Armenian National Philharmonic Orchestra - Armenian
(3, 'hy', 'Հայաստանի ազգային ֆիլհարմոնիկ նվագախումբ',
 'Դասական գլուխգործոցների երեկո՝ ներկայացնելով Արամ Խաչատրյանի և Կոմիտասի ստեղծագործությունները։ Հայաստանի երաժշտական ժառանգության հոգևոր ճանապարհորդություն։ Աշխարհահռչակ դիրիժորի ղեկավարությամբ։',
 NOW(), NOW()),
-- Armenian National Philharmonic Orchestra - Russian
(3, 'ru', 'Армянский национальный филармонический оркестр',
 'Вечер классических шедевров с произведениями Арама Хачатуряна и Комитаса. Духовное путешествие через музыкальное наследие Армении. Под управлением всемирно известного дирижера.',
 NOW(), NOW()),
-- Armenian National Philharmonic Orchestra - French
(3, 'fr', 'Orchestre philharmonique national arménien',
 'Une soirée de chefs-d''œuvre classiques mettant en vedette Khatchatourian et Komitas. Un voyage spirituel à travers l''héritage musical arménien. Dirigé par un chef d''orchestre de renommée mondiale.',
 NOW(), NOW()),

-- Rock Night - Armenian
(4, 'hy', 'Ռոք գիշեր - Անցյալ միջոցառում',
 'Անցյալ ռոք համերգ՝ փորձարկման նպատակով։ Պատմական ներկայացում՝ տեղական և միջազգային ռոք խմբերի մասնակցությամբ։',
 NOW(), NOW()),
-- Rock Night - Russian
(4, 'ru', 'Рок-ночь - Прошедшее мероприятие',
 'Прошедший рок-концерт для целей тестирования. Историческое выступление с участием местных и международных рок-групп.',
 NOW(), NOW()),
-- Rock Night - French
(4, 'fr', 'Nuit Rock - Événement passé',
 'Concert rock passé à des fins de test. Performance historique avec des groupes de rock locaux et internationaux.',
 NOW(), NOW());

-- ================================================================
-- 5. EVENT SESSIONS
-- ================================================================
INSERT INTO event_sessions (id, event_id, start_time, end_time, tickets_count, tickets_sold, status, created_at,
                            last_modified_at)
VALUES (1, 1, NOW() + INTERVAL '7 days' + INTERVAL '19 hours',
        NOW() + INTERVAL '7 days' + INTERVAL '21 hours 30 minutes', 300, 25, 'UPCOMING', NOW(), NOW()),
       (2, 1, NOW() + INTERVAL '8 days' + INTERVAL '19 hours',
        NOW() + INTERVAL '8 days' + INTERVAL '21 hours 30 minutes', 300, 0, 'UPCOMING', NOW(), NOW()),
       (3, 2, NOW() + INTERVAL '10 days' + INTERVAL '18 hours', NOW() + INTERVAL '10 days' + INTERVAL '21 hours', 250,
        15, 'UPCOMING', NOW(), NOW()),
       (4, 3, NOW() + INTERVAL '14 days' + INTERVAL '20 hours', NOW() + INTERVAL '14 days' + INTERVAL '22 hours', 400,
        0, 'UPCOMING', NOW(), NOW()),
       (5, 4, NOW() - INTERVAL '5 days' + INTERVAL '20 hours', NOW() - INTERVAL '5 days' + INTERVAL '23 hours', 200,
        180, 'PAST', NOW() - INTERVAL '10 days', NOW() - INTERVAL '5 days');

SELECT setval('event_sessions_id_seq', 5, true);

-- ================================================================
-- 6. SEATING CHARTS
-- ================================================================
INSERT INTO seating_charts (id, venue_id, name, seat_indicator_size, level_indicator_size, background_url, created_at,
                            last_modified_at)
VALUES (1, 1, 'Opera Main Hall', 1, 1, 'https://example.com/opera-bg.jpg', NOW(), NOW()),
       (2, 2, 'Drama Theatre Hall', 1, 1, NULL, NOW(), NOW()),
       (3, 3, 'Concert Hall VIP Lounge', 1, 1, 'https://example.com/concert-vip-bg.jpg', NOW(), NOW());

SELECT setval('seating_charts_id_seq', 3, true);

-- ================================================================
-- 7. LEVELS (Sections)
-- ================================================================
INSERT INTO levels (id, seating_chart_id, parent_level_id, level_name, level_identifier, position_x, position_y,
                    capacity, created_at, last_modified_at)
VALUES
-- Opera House levels (seating_chart_id = 1)
(1, 1, NULL, 'Orchestra', 'ORCH', 50.0, 100.0, NULL, NOW(), NOW()),
(2, 1, NULL, 'Balcony', 'BALC', 50.0, 50.0, NULL, NOW(), NOW()),
(3, 1, NULL, 'Standing Area', 'STAND', 50.0, 150.0, 100, NOW(), NOW()), -- GA area
-- Drama Theatre levels (seating_chart_id = 2)
(4, 2, NULL, 'Parterre', 'PART', 50.0, 100.0, NULL, NOW(), NOW()),
-- Concert Hall levels with tables (seating_chart_id = 3)
(5, 3, NULL, 'VIP Lounge', 'VIP-LOUNGE', 30.0, 80.0, NULL, NOW(), NOW());
-- Parent section for VIP tables

-- VIP Tables (FLEXIBLE mode - can book individual seats or whole table)
INSERT INTO levels (id, seating_chart_id, parent_level_id, level_name, level_identifier, position_x, position_y,
                    capacity, is_table, table_booking_mode, table_capacity, created_at, last_modified_at)
VALUES
-- VIP Table 1 (4 seats, flexible booking)
(6, 3, 5, 'VIP Table 1', 'VIP-T1', 20.0, 70.0, NULL, true, 'FLEXIBLE', 4, NOW(), NOW()),
-- VIP Table 2 (4 seats, flexible booking)
(7, 3, 5, 'VIP Table 2', 'VIP-T2', 40.0, 70.0, NULL, true, 'FLEXIBLE', 4, NOW(), NOW()),
-- VIP Table 3 (6 seats, table only - must book complete table)
(8, 3, 5, 'VIP Table 3', 'VIP-T3', 60.0, 70.0, NULL, true, 'TABLE_ONLY', 6, NOW(), NOW()),
-- Premium Table (8 seats, seats only - individual booking)
(9, 3, 5, 'Premium Table', 'PREM-T1', 80.0, 70.0, NULL, true, 'SEATS_ONLY', 8, NOW(), NOW());

SELECT setval('levels_id_seq', 9, true);


-- ================================================================
-- 9. SEATS
-- ================================================================
-- Opera House - Orchestra (rows A-E, seats 1-10)
INSERT INTO seats (level_id, seat_identifier, seat_number, row_label, position_x, position_y, seat_type, created_at,
                   last_modified_at)
SELECT 1, -- Orchestra level
       'ORCH-' || chr(64 + row_num) || seat_num,
       seat_num::text,
       'Row ' || chr(64 + row_num),
       seat_num * 10.0,
       (row_num - 1) * 15.0,
       CASE WHEN chr(64 + row_num) = 'A' AND seat_num IN (5, 6) THEN 'vip' ELSE 'standard' END,
       NOW(),
       NOW()
FROM generate_series(1, 5) AS row_num,
     generate_series(1, 10) AS seat_num;

-- Opera House - Balcony (rows F-H, seats 1-8)
INSERT INTO seats (level_id, seat_identifier, seat_number, row_label, position_x, position_y, seat_type, created_at,
                   last_modified_at)
SELECT 2, -- Balcony level
       'BALC-' || chr(69 + row_num) || seat_num,
       seat_num::text,
       'Row ' || chr(69 + row_num),
       seat_num * 10.0,
       (row_num - 1) * 15.0,
       'standard',
       NOW(),
       NOW()
FROM generate_series(1, 3) AS row_num,
     generate_series(1, 8) AS seat_num;

-- Drama Theatre - Parterre (rows A-D, seats 1-12)
INSERT INTO seats (level_id, seat_identifier, seat_number, row_label, position_x, position_y, seat_type, created_at,
                   last_modified_at)
SELECT 4, -- Parterre level
       'PART-' || chr(64 + row_num) || seat_num,
       seat_num::text,
       'Row ' || chr(64 + row_num),
       seat_num * 8.0,
       (row_num - 1) * 12.0,
       'standard',
       NOW(),
       NOW()
FROM generate_series(1, 4) AS row_num,
     generate_series(1, 12) AS seat_num;

-- Concert Hall - VIP Table 1 (4 seats in 2x2 arrangement)
INSERT INTO seats (level_id, seat_identifier, seat_number, row_label, position_x, position_y, seat_type, created_at,
                   last_modified_at)
VALUES (6, 'VIP-T1-S1', '1', NULL, 18.0, 68.0, 'vip', NOW(), NOW()),
       (6, 'VIP-T1-S2', '2', NULL, 22.0, 68.0, 'vip', NOW(), NOW()),
       (6, 'VIP-T1-S3', '3', NULL, 18.0, 72.0, 'vip', NOW(), NOW()),
       (6, 'VIP-T1-S4', '4', NULL, 22.0, 72.0, 'vip', NOW(), NOW());

-- Concert Hall - VIP Table 2 (4 seats in 2x2 arrangement)
INSERT INTO seats (level_id, seat_identifier, seat_number, row_label, position_x, position_y, seat_type, created_at,
                   last_modified_at)
VALUES (7, 'VIP-T2-S1', '1', NULL, 38.0, 68.0, 'vip', NOW(), NOW()),
       (7, 'VIP-T2-S2', '2', NULL, 42.0, 68.0, 'vip', NOW(), NOW()),
       (7, 'VIP-T2-S3', '3', NULL, 38.0, 72.0, 'vip', NOW(), NOW()),
       (7, 'VIP-T2-S4', '4', NULL, 42.0, 72.0, 'vip', NOW(), NOW());

-- Concert Hall - VIP Table 3 (6 seats in 2x3 arrangement, TABLE_ONLY)
INSERT INTO seats (level_id, seat_identifier, seat_number, row_label, position_x, position_y, seat_type, created_at,
                   last_modified_at)
VALUES (8, 'VIP-T3-S1', '1', NULL, 58.0, 66.0, 'vip', NOW(), NOW()),
       (8, 'VIP-T3-S2', '2', NULL, 62.0, 66.0, 'vip', NOW(), NOW()),
       (8, 'VIP-T3-S3', '3', NULL, 58.0, 70.0, 'vip', NOW(), NOW()),
       (8, 'VIP-T3-S4', '4', NULL, 62.0, 70.0, 'vip', NOW(), NOW()),
       (8, 'VIP-T3-S5', '5', NULL, 58.0, 74.0, 'vip', NOW(), NOW()),
       (8, 'VIP-T3-S6', '6', NULL, 62.0, 74.0, 'vip', NOW(), NOW());

-- Concert Hall - Premium Table (8 seats in 2x4 arrangement, SEATS_ONLY)
INSERT INTO seats (level_id, seat_identifier, seat_number, row_label, position_x, position_y, seat_type, created_at,
                   last_modified_at)
VALUES (9, 'PREM-T1-S1', '1', NULL, 76.0, 64.0, 'premium', NOW(), NOW()),
       (9, 'PREM-T1-S2', '2', NULL, 80.0, 64.0, 'premium', NOW(), NOW()),
       (9, 'PREM-T1-S3', '3', NULL, 84.0, 64.0, 'premium', NOW(), NOW()),
       (9, 'PREM-T1-S4', '4', NULL, 88.0, 64.0, 'premium', NOW(), NOW()),
       (9, 'PREM-T1-S5', '5', NULL, 76.0, 76.0, 'premium', NOW(), NOW()),
       (9, 'PREM-T1-S6', '6', NULL, 80.0, 76.0, 'premium', NOW(), NOW()),
       (9, 'PREM-T1-S7', '7', NULL, 84.0, 76.0, 'premium', NOW(), NOW()),
       (9, 'PREM-T1-S8', '8', NULL, 88.0, 76.0, 'premium', NOW(), NOW());


-- ================================================================
-- 8. EVENT PRICE TEMPLATES
-- ================================================================
-- Price templates for Event 1 (Swan Lake)
INSERT INTO event_price_templates (id, event_id, template_name, price, color, display_order, created_at)
VALUES (1, 1, 'VIP', 10000.00, '#FFD700', 1, NOW()),
       (2, 1, 'Standard', 6000.00, '#4169E1', 2, NOW()),
       (3, 1, 'Standing', 2500.00, '#90EE90', 3, NOW()),
       (4, 1, 'Matinee VIP', 8000.00, '#FFA500', 4, NOW()),
       (5, 1, 'Matinee Standard', 5000.00, '#87CEEB', 5, NOW()),
       (6, 1, 'Matinee Standing', 2000.00, '#98FB98', 6, NOW());

-- Price templates for Event 2 (Hamlet)
INSERT INTO event_price_templates (id, event_id, template_name, price, color, display_order, created_at)
VALUES (7, 2, 'Standard', 3000.00, '#4169E1', 1, NOW());

-- Price templates for Event 3 (Concert)
INSERT INTO event_price_templates (id, event_id, template_name, price, color, display_order, created_at)
VALUES (8, 3, 'Premium', 8000.00, '#FFD700', 1, NOW()),
       (9, 3, 'General', 4000.00, '#4169E1', 2, NOW()),
       (10, 3, 'VIP Table (4 seats)', 30000.00, '#FF1493', 3, NOW()), -- Discounted vs 4×8000
       (11, 3, 'VIP Table (6 seats)', 42000.00, '#FF1493', 4, NOW()), -- Discounted vs 6×8000
       (12, 3, 'Premium Table (8 seats)', 56000.00, '#DA70D6', 5, NOW()); -- Discounted vs 8×8000

SELECT setval('event_price_templates_id_seq', 12, true);

-- ================================================================
-- 9. SESSION SEAT CONFIGS (Template Assignment & Availability per Session)
-- ================================================================
-- Session 1: Swan Lake matinee (using matinee templates)
INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
SELECT 1,       -- Session 1
       id,      -- Seat ID
       CASE
           WHEN seat_type = 'vip' THEN 4 -- Matinee VIP template
           ELSE 5 -- Matinee Standard template
           END,
       CASE
           WHEN id <= (SELECT MIN(id) + 25 FROM seats WHERE level_id = 1) THEN 'SOLD'
           ELSE 'AVAILABLE'
           END, -- First 25 seats sold
       NOW(),
       NOW()
FROM seats
WHERE level_id IN (1, 2);
-- Orchestra and Balcony only

-- Session 2: Swan Lake evening (using evening templates - higher prices)
INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
SELECT 2,                          -- Session 2
       id,
       CASE
           WHEN seat_type = 'vip' THEN 1 -- VIP template
           ELSE 2 -- Standard template
           END,
       'AVAILABLE',                -- All available
       NOW(),
       NOW()
FROM seats
WHERE level_id IN (1, 2);

-- Session 3: Hamlet (flat pricing with standard template)
INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
SELECT 3,       -- Session 3
       id,
       7,       -- Standard template for Hamlet
       CASE
           WHEN id <= (SELECT MIN(id) + 15 FROM seats WHERE level_id = 4) THEN 'SOLD'
           ELSE 'AVAILABLE'
           END,
       NOW(),
       NOW()
FROM seats
WHERE level_id = 4;
-- Parterre

-- ================================================================
-- 10. SESSION LEVEL CONFIGS (GA Template Assignment & Capacity per Session)
-- ================================================================
-- Session 1: Standing Area for Swan Lake matinee
INSERT INTO session_level_configs (session_id, level_id, price_template_id, capacity, sold_count, status, created_at,
                                   last_modified_at)
VALUES (1, 3, 6, 100, 25, 'AVAILABLE', NOW(), NOW()),
       -- Matinee Standing template
       -- Session 2: Standing Area for Swan Lake evening
       (2, 3, 3, 120, 0, 'AVAILABLE', NOW(), NOW());
-- Evening Standing template

-- ================================================================
-- 11. SESSION SEAT CONFIGS FOR TABLE SEATS (Event 3 - Concert, Session 4)
-- ================================================================
-- VIP Table 1 seats (template 8 - Premium, FLEXIBLE table - seats can be booked individually)
INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
SELECT 4, id, 8, 'AVAILABLE', NOW(), NOW()
FROM seats
WHERE level_id = 6;

-- VIP Table 2 seats (template 8 - Premium, FLEXIBLE table)
INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
SELECT 4, id, 8, 'AVAILABLE', NOW(), NOW()
FROM seats
WHERE level_id = 7;

-- VIP Table 3 seats (template 8 - Premium, TABLE_ONLY - seats automatically BLOCKED)
INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
SELECT 4, id, 8, 'BLOCKED', NOW(), NOW() -- BLOCKED because TABLE_ONLY mode
FROM seats
WHERE level_id = 8;

-- Premium Table seats (template 8 - Premium, SEATS_ONLY - individual booking only)
INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
SELECT 4, id, 8, 'AVAILABLE', NOW(), NOW()
FROM seats
WHERE level_id = 9;

-- ================================================================
-- 12. SESSION TABLE CONFIGS (Table Pricing & Availability)
-- ================================================================
-- Concert Hall tables for Session 4 (Event 3)
INSERT INTO session_table_configs (session_id, table_id, price_template_id, status, created_at, last_modified_at)
VALUES
-- VIP Table 1 (4 seats, FLEXIBLE mode, discounted table price)
(4, 6, 10, 'AVAILABLE', NOW(), NOW()),
-- VIP Table 2 (4 seats, FLEXIBLE mode, discounted table price)
(4, 7, 10, 'AVAILABLE', NOW(), NOW()),
-- VIP Table 3 (6 seats, TABLE_ONLY mode, only table booking allowed)
(4, 8, 11, 'AVAILABLE', NOW(), NOW()),
-- Premium Table (8 seats, SEATS_ONLY mode, table cannot be booked as unit)
(4, 9, 12, 'BLOCKED', NOW(), NOW());
-- BLOCKED because SEATS_ONLY mode
-- Evening Standing template

-- ================================================================
-- 13. BOOKINGS
-- ================================================================
INSERT INTO bookings (id, user_id, guest_id, session_id, reservation_token, platform_id, venue_id, total_price,
                      currency, status, confirmed_at, cancelled_at, cancellation_reason, payment_id, created_at,
                      last_modified_at)
VALUES
-- Booking 1: User 2 (Anna) via Ticketmaster for Swan Lake
('11111111-1111-1111-1111-111111111111', 2, NULL, 1, '22222222-2222-2222-2222-222222222222', 1, 1, 15000.00, 'AMD',
 'CONFIRMED', NOW(), NULL, NULL, 'TM-ABC12345', NOW(), NOW()),
-- Booking 2: User 3 (Karen) direct booking for Hamlet
('33333333-3333-3333-3333-333333333333', 3, NULL, 3, '44444444-4444-4444-4444-444444444444', NULL, 2, 6000.00, 'AMD',
 'CONFIRMED', NOW(), NULL, NULL, 'DIRECT-XYZ789', NOW(), NOW()),
-- Booking 3: User 4 (Maria) pending booking for Concert
('55555555-5555-5555-5555-555555555555', 4, NULL, 4, '66666666-6666-6666-6666-666666666666', NULL, 3, 8000.00, 'AMD',
 'PENDING', NULL, NULL, NULL, NULL, NOW(), NOW()),
-- Booking 4: User 2 (Anna) cancelled booking
('77777777-7777-7777-7777-777777777777', 2, NULL, 3, '88888888-8888-8888-8888-888888888888', NULL, 2, 3000.00, 'AMD',
 'CANCELLED', NULL, NOW() - INTERVAL '1 hour', 'User requested cancellation', NULL, NOW() - INTERVAL '2 days', NOW());

-- ================================================================
-- SUMMARY
-- ================================================================
SELECT 'Venues' AS entity,
       COUNT(*) AS count
FROM venues
UNION ALL
SELECT 'Venue Translations', COUNT(*)
FROM venue_translations
UNION ALL
SELECT 'Users', COUNT(*)
FROM users
UNION ALL
SELECT 'Platforms', COUNT(*)
FROM platforms
UNION ALL
SELECT 'Event Categories', COUNT(*)
FROM event_categories
UNION ALL
SELECT 'Event Category Translations', COUNT(*)
FROM event_category_translations
UNION ALL
SELECT 'Events', COUNT(*)
FROM events
UNION ALL
SELECT 'Event Translations', COUNT(*)
FROM event_translations
UNION ALL
SELECT 'Event Sessions', COUNT(*)
FROM event_sessions
UNION ALL
SELECT 'Seating Charts', COUNT(*)
FROM seating_charts
UNION ALL
SELECT 'Levels', COUNT(*)
FROM levels
UNION ALL
SELECT 'Levels (Tables)', COUNT(*)
FROM levels
WHERE is_table = true
UNION ALL
SELECT 'Seats', COUNT(*)
FROM seats
UNION ALL
SELECT 'Session Seat Configs', COUNT(*)
FROM session_seat_configs
UNION ALL
SELECT 'Session Level Configs', COUNT(*)
FROM session_level_configs
UNION ALL
SELECT 'Session Table Configs', COUNT(*)
FROM session_table_configs
UNION ALL
SELECT 'Bookings', COUNT(*)
FROM bookings;

-- ================================================================
-- SUCCESS MESSAGE
-- ================================================================
DO
$$
    BEGIN
        RAISE NOTICE '✅ Development data seeded successfully!';
        RAISE NOTICE '   - 3 Venues (with Armenian, Russian, French translations)';
        RAISE NOTICE '   - 4 Users (1 admin: admin@gov.am / admin123)';
        RAISE NOTICE '   - 3 Platforms';
        RAISE NOTICE '   - 6 Event Categories (with 3 language translations each)';
        RAISE NOTICE '   - 4 Events with 5 Sessions (with Armenian, Russian, French translations)';
        RAISE NOTICE '   - 3 Seating Charts, 9 Levels (4 Table Levels), 96 Seats';
        RAISE NOTICE '   - Session Configs: Seat pricing, GA capacity, & Table pricing';
        RAISE NOTICE '   - 4 VIP/Premium Tables (FLEXIBLE, TABLE_ONLY, SEATS_ONLY modes)';
        RAISE NOTICE '   - 4 Bookings (2 confirmed, 1 pending, 1 cancelled)';
        RAISE NOTICE '';
        RAISE NOTICE '🌍 Translations: Armenian (hy), Russian (ru), French (fr)';
        RAISE NOTICE '🎭 Ready to test all API endpoints with multilingual support!';
        RAISE NOTICE '🍽️  Ready to test table booking with different modes!';
        RAISE NOTICE '';
        RAISE NOTICE '📝 Test session seating: GET /api/v1/sessions/1/seating';
        RAISE NOTICE '📝 Test table seating: GET /api/v1/sessions/4/seating';
        RAISE NOTICE '📝 Test translations: GET /api/v1/events/1?lang=hy';
        RAISE NOTICE '📝 Test translations: GET /api/v1/venues/1?lang=ru';
    END
$$;

