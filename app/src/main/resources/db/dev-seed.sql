-- ================================================================
-- Venues API - Development Data Seed Script (FIXED UUID SYNTAX)
-- All UUIDs now use valid Hex characters (0-9, a-f)
-- ================================================================

-- ================================================================
-- 1. VENUES (Prefix: 1111..., 2222..., 3333...)
-- ================================================================
INSERT INTO venues (id, name, smtp_email, smtp_password, address, city, category, verified, official, is_always_open,
                    status, created_at, last_modified_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Yerevan Opera House', 'opera@yerevan.am', 'mock_smtp_pass',
        'Tumanyan 54, Yerevan', 'Yerevan', 'OPERA_HOUSE', true, true, false, 'ACTIVE', NOW(), NOW()),
       ('22222222-2222-2222-2222-222222222222', 'Gyumri Drama Theatre', 'drama@gyumri.am', 'mock_smtp_pass',
        'Abovyan 12, Gyumri', 'Gyumri', 'THEATRE', true, true, false, 'ACTIVE', NOW(), NOW()),
       ('33333333-3333-3333-3333-333333333333', 'Aram Khachaturian Concert Hall', 'concert@yerevan.am',
        'mock_smtp_pass', 'Marshal Baghramyan Avenue 46, Yerevan', 'Yerevan', 'CONCERT_HALL', true, true, false,
        'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.1 VENUE TRANSLATIONS
INSERT INTO venue_translations (venue_id, language, name, description, created_at, last_modified_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'hy', 'Երևանի օպերայի և բալետի ազգային ակադեմիական թատրոն',
        'Հայաստանի հիմնական օպերայի և բալետի թատրոնը։', NOW(), NOW()),
       ('11111111-1111-1111-1111-111111111111', 'ru', 'Ереванский академический театр оперы и балета',
        'Главный оперный и балетный театр Армении.', NOW(), NOW()),
       ('11111111-1111-1111-1111-111111111111', 'fr', 'Théâtre national académique d''opéra et de ballet d''Erevan',
        'Principal théâtre d''opéra et de ballet.', NOW(), NOW()),
       ('22222222-2222-2222-2222-222222222222', 'hy', 'Գյումրիի Վարդան Աճեմյանի անվան դրամատիկական թատրոն',
        'Հայաստանի երկրորդ ամենախոշոր դրամատիկական թատրոնը։', NOW(), NOW()),
       ('33333333-3333-3333-3333-333333333333', 'hy', 'Արամ Խաչատրյանի անվան համերգասրահ',
        'Երևանի կենտրոնական համերգասրահը։', NOW(), NOW());

-- ================================================================
-- 2. USERS (Prefix: a000...)
-- ================================================================
INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, role, status, failed_login_attempts,
                   email_verified, created_at, last_modified_at)
VALUES ('a0000000-0000-0000-0000-000000000001', 'admin@gov.am', '$2a$10$e.g.mockhashforpassword123.......', 'Admin',
        'User', '+37411111111', 'ADMIN', 'ACTIVE', 0, true, NOW(), NOW()),
       ('a0000000-0000-0000-0000-000000000002', 'anna.petrosyan@example.com',
        '$2a$10$e.g.mockhashforpassword123.......', 'Anna', 'Petrosyan', '+37422222222', 'USER', 'ACTIVE', 0, true,
        NOW(), NOW()),
       ('a0000000-0000-0000-0000-000000000003', 'karen.sargsyan@example.com',
        '$2a$10$e.g.mockhashforpassword123.......', 'Karen', 'Sargsyan', '+37433333333', 'USER', 'ACTIVE', 0, true,
        NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 3. PLATFORMS (Prefix: bbbb... - replaced invalid 'p')
-- ================================================================
INSERT INTO platforms (id, name, api_url, shared_secret, status, webhook_enabled, description, contact_email,
                       rate_limit, webhook_success_count, webhook_failure_count, created_at, last_modified_at)
VALUES ('bbbbbbbb-1111-1111-1111-111111111111', 'Ticketmaster Armenia', 'https://kind-mouse-84.webhook.cool', 'secret1',
        'ACTIVE', true, 'Partner', 'tech@tm.am', 1000, 0, 0, NOW(), NOW()),
       ('bbbbbbbb-2222-2222-2222-222222222222', 'TomsTickets', 'https://test.local/api', 'secret2', 'ACTIVE', false,
        'Booking platform', 'api@toms.com', 500, 0, 0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 3.1 EVENT CATEGORIES
-- ================================================================
INSERT INTO event_categories (id, category_key, name, color, icon, display_order, is_active, created_at,
                              last_modified_at)
VALUES (10, 'BALLET', 'Ballet', '#FF6B9D', 'ballet-icon', 10, true, NOW(), NOW()),
       (20, 'OPERA', 'Opera', '#9D50BB', 'opera-icon', 20, true, NOW(), NOW()),
       (30, 'THEATRE', 'Theatre', '#F39C12', 'theatre-icon', 30, true, NOW(), NOW()),
       (40, 'CONCERT', 'Concert', '#3498DB', 'concert-icon', 40, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('event_categories', 'id'), 70);

-- 3.2 EVENT CATEGORY TRANSLATIONS
INSERT INTO event_category_translations (category_id, language, name, created_at, last_modified_at)
VALUES (10, 'hy', 'Բալետ', NOW(), NOW()),
       (10, 'ru', 'Балет', NOW(), NOW()),
       (20, 'hy', 'Օպերա', NOW(), NOW()),
       (20, 'ru', 'Опера', NOW(), NOW()),
       (30, 'hy', 'Թատրոն', NOW(), NOW()),
       (30, 'ru', 'Театр', NOW(), NOW()),
       (40, 'hy', 'Համերգ', NOW(), NOW()),
       (40, 'ru', 'Концерт', NOW(), NOW());

-- ================================================================
-- 4. SEATING CHARTS (Prefix: 9999... - replaced invalid 'sc')
-- ================================================================
INSERT INTO seating_charts (id, venue_id, name, seat_indicator_size, level_indicator_size, background_url, created_at,
                            last_modified_at)
VALUES ('99999999-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Opera Main Hall', 1, 1,
        'http://img.url', NOW(), NOW()),
       ('99999999-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'Drama Theatre Hall', 1, 1,
        NULL, NOW(), NOW()),
       ('99999999-3333-3333-3333-333333333333', '33333333-3333-3333-3333-333333333333', 'Concert Hall VIP Lounge', 1, 1,
        'http://img.url', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 5. EVENTS (Prefix: eeee...)
-- ================================================================
INSERT INTO events (id, venue_id, category_id, seating_chart_id, title, description, currency, status, created_at,
                    last_modified_at)
VALUES ('eeeeeeee-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 10,
        '99999999-1111-1111-1111-111111111111', 'Swan Lake', 'Tchaikovsky masterpiece', 'AMD', 'UPCOMING', NOW(),
        NOW()),
       ('eeeeeeee-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 30,
        '99999999-2222-2222-2222-222222222222', 'Hamlet', 'Shakespeare tragedy', 'AMD', 'UPCOMING', NOW(), NOW()),
       ('eeeeeeee-3333-3333-3333-333333333333', '33333333-3333-3333-3333-333333333333', 40,
        '99999999-3333-3333-3333-333333333333', 'Philharmonic Orchestra', 'Classical evening', 'AMD', 'UPCOMING', NOW(),
        NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 5.1 EVENT TRANSLATIONS
-- ================================================================
INSERT INTO event_translations (event_id, language, title, description, created_at, last_modified_at)
VALUES ('eeeeeeee-1111-1111-1111-111111111111', 'hy', 'Կարապի լիճ', 'Չայկովսկու գլուխգործոցը', NOW(), NOW()),
       ('eeeeeeee-2222-2222-2222-222222222222', 'hy', 'Համլետ', 'Շեքսպիրի ողբերգություն', NOW(), NOW());

-- ================================================================
-- 6. EVENT SESSIONS (Prefix: 5555... - replaced invalid 's')
-- ================================================================
INSERT INTO event_sessions (id, event_id, start_time, end_time, tickets_count, tickets_sold, status, created_at,
                            last_modified_at)
VALUES ('55555555-1111-1111-1111-111111111111', 'eeeeeeee-1111-1111-1111-111111111111', NOW() + INTERVAL '7 days',
        NOW() + INTERVAL '7 days 2 hours', 300, 25, 'UPCOMING', NOW(), NOW()),
       ('55555555-2222-2222-2222-222222222222', 'eeeeeeee-1111-1111-1111-111111111111', NOW() + INTERVAL '10 days',
        NOW() + INTERVAL '10 days 2 hours', 250, 15, 'UPCOMING', NOW(), NOW()),
       ('55555555-3333-3333-3333-333333333333', 'eeeeeeee-3333-3333-3333-333333333333', NOW() + INTERVAL '14 days',
        NOW() + INTERVAL '14 days 2 hours', 400, 0, 'UPCOMING', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 7. LEVELS
-- ================================================================
INSERT INTO levels (id, seating_chart_id, parent_level_id, level_name, level_identifier, position_x, position_y,
                    capacity, is_table, created_at, last_modified_at)
VALUES (1, '99999999-1111-1111-1111-111111111111', NULL, 'Orchestra', 'ORCH', 50.0, 100.0, NULL, false, NOW(), NOW()),
       (2, '99999999-1111-1111-1111-111111111111', NULL, 'Balcony', 'BALC', 50.0, 50.0, NULL, false, NOW(), NOW()),
       (3, '99999999-1111-1111-1111-111111111111', NULL, 'Standing', 'STAND', 50.0, 150.0, 100, false, NOW(), NOW()),
       (5, '99999999-3333-3333-3333-333333333333', NULL, 'VIP Lounge', 'VIP', 30.0, 80.0, NULL, false, NOW(), NOW()),
       (6, '99999999-3333-3333-3333-333333333333', 5, 'VIP Table 1', 'VIP-T1', 20.0, 70.0, 4, true, NOW(), NOW()),
       (7, '99999999-3333-3333-3333-333333333333', 5, 'VIP Table 2', 'VIP-T2', 40.0, 70.0, 4, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('levels', 'id'), 50);

-- ================================================================
-- 8. SEATS
-- ================================================================
INSERT INTO seats (id, level_id, seat_identifier, seat_number, row_label, position_x, position_y, created_at,
                   last_modified_at)
VALUES (1, 1, 'ORCH-A1', '1', 'Row A', 10.0, 10.0, NOW(), NOW()),
       (2, 1, 'ORCH-A2', '2', 'Row A', 20.0, 10.0, NOW(), NOW()),
       (10, 6, 'VIP-T1-S1', '1', NULL, 18.0, 68.0, NOW(), NOW()),
       (11, 6, 'VIP-T1-S2', '2', NULL, 22.0, 68.0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('seats', 'id'), 50);

-- ================================================================
-- 9. PRICE TEMPLATES (Prefix: dddd... - replaced invalid 'pt')
-- ================================================================
INSERT INTO event_price_templates (id, event_id, template_name, price, color, created_at, last_modified_at)
VALUES ('dddddddd-1111-1111-1111-111111111111', 'eeeeeeee-1111-1111-1111-111111111111', 'VIP', 10000.00, '#FFD700',
        NOW(), NOW()),
       ('dddddddd-2222-2222-2222-222222222222', 'eeeeeeee-1111-1111-1111-111111111111', 'Standard', 6000.00, '#4169E1',
        NOW(), NOW()),
       ('dddddddd-3333-3333-3333-333333333333', 'eeeeeeee-3333-3333-3333-333333333333', 'Table Booking', 30000.00,
        '#FF1493', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 10. SESSION CONFIGS
-- ================================================================
INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
VALUES ('55555555-1111-1111-1111-111111111111', 1, 'dddddddd-1111-1111-1111-111111111111', 'AVAILABLE', NOW(), NOW()),
       ('55555555-1111-1111-1111-111111111111', 2, 'dddddddd-2222-2222-2222-222222222222', 'SOLD', NOW(), NOW());

INSERT INTO session_level_configs (session_id, level_id, price_template_id, capacity, sold_count, status, created_at,
                                   last_modified_at)
VALUES ('55555555-1111-1111-1111-111111111111', 3, 'dddddddd-2222-2222-2222-222222222222', 100, 25, 'AVAILABLE', NOW(),
        NOW());

INSERT INTO session_table_configs (session_id, table_id, price_template_id, booking_mode, status, created_at,
                                   last_modified_at)
VALUES ('55555555-3333-3333-3333-333333333333', 6, 'dddddddd-3333-3333-3333-333333333333', 'TABLE_ONLY', 'AVAILABLE',
        NOW(), NOW());

-- ================================================================
-- 11. GUESTS (Prefix: eeee... - replaced invalid 'g' and 'g... is taken' so using eeee9999)
-- ================================================================
INSERT INTO guests (id, name, email, phone, created_at, last_modified_at)
VALUES ('eeeeeeee-9999-1111-1111-111111111111', 'John Doe', 'guest@example.com', '+37499888777', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 12. BOOKINGS (Prefix: bbbb... - replaced invalid 'b')
-- ================================================================
INSERT INTO bookings (id, reservation_token, user_id, guest_id, session_id, venue_id, platform_id,
                      total_price, currency, status, created_at, last_modified_at)
VALUES ('bbbbbbbb-1111-1111-1111-111111111111', 'aaaaaaaa-1111-1111-1111-111111111111',
        'a0000000-0000-0000-0000-000000000002', NULL,
        '55555555-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111',
        'bbbbbbbb-1111-1111-1111-111111111111',
        6000.00, 'AMD', 'CONFIRMED', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 13. BOOKING ITEMS
-- ================================================================
INSERT INTO booking_items (booking_id, seat_id, level_id, price_template_name, unit_price, quantity, created_at,
                           last_modified_at)
VALUES ('bbbbbbbb-1111-1111-1111-111111111111', 2, 1, 'Standard', 6000.00, 1, NOW(), NOW());

-- ================================================================
-- Final Sequence Updates
-- ================================================================
SELECT setval(pg_get_serial_sequence('booking_items', 'id'), 50);
SELECT setval(pg_get_serial_sequence('session_seat_configs', 'id'), 50);
SELECT setval(pg_get_serial_sequence('session_level_configs', 'id'), 50);
SELECT setval(pg_get_serial_sequence('session_table_configs', 'id'), 50);