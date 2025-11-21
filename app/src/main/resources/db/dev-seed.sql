/*
  Flyway Migration V3
  Description: Seed Development/Test Transactional Data
  Dependencies: Requires V2 (Reference Data) to be applied first.
*/

-- ================================================================
-- 0. PRE-REQUISITES (Organizations)
-- Venues MUST belong to an Organization.
-- ================================================================
INSERT INTO organizations (id, name, slug, type, contact_email, is_active, created_at, last_modified_at)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Ministry of Culture', 'min-culture', 'GOVERNMENT', 'contact@min.am',
        true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 1. VENUES
-- Fixed: Added organization_id, slug, city_id lookup. Removed 'verified', 'official'.
-- ================================================================
INSERT INTO venues (id, organization_id, city_id, category_id,
                    name, slug, address,
                    is_always_open, status, created_at, last_modified_at)
VALUES ('11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        (SELECT id FROM ref_cities WHERE slug = 'yerevan' LIMIT 1), -- Lookup ID
        (SELECT id FROM ref_venue_categories WHERE code = 'OPERA' LIMIT 1), -- Lookup ID
        'Yerevan Opera House',
        'opera-house',
        'Tumanyan 54, Yerevan',
        false, 'ACTIVE', NOW(), NOW()),
       ('22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        (SELECT id FROM ref_cities WHERE slug = 'gyumri' LIMIT 1),
        (SELECT id FROM ref_venue_categories WHERE code = 'THEATER' LIMIT 1),
        'Gyumri Drama Theatre',
        'gyumri-drama',
        'Abovyan 12, Gyumri',
        false, 'ACTIVE', NOW(), NOW()),
       ('33333333-3333-3333-3333-333333333333',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        (SELECT id FROM ref_cities WHERE slug = 'yerevan' LIMIT 1),
        (SELECT id FROM ref_venue_categories WHERE code = 'CONCERT_HALL' LIMIT 1),
        'Aram Khachaturian Concert Hall',
        'aram-khachaturian',
        'Marshal Baghramyan Avenue 46, Yerevan',
        false, 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.1 VENUE TRANSLATIONS
INSERT INTO venue_translations (venue_id, language, name, description, created_at, last_modified_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'hy', 'Երևանի օպերայի և բալետի ազգային ակադեմիական թատրոն',
        'Հայաստանի հիմնական օպերայի և բալետի թատրոնը։', NOW(), NOW()),
       ('11111111-1111-1111-1111-111111111111', 'ru', 'Ереванский академический театр оперы и балета',
        'Главный оперный и балетный театр Армении.', NOW(), NOW()),
       ('22222222-2222-2222-2222-222222222222', 'hy', 'Գյումրիի Վարդան Աճեմյանի անվան դրամատիկական թատրոն',
        'Հայաստանի երկրորդ ամենախոշոր դրամատիկական թատրոնը։', NOW(), NOW()),
       ('33333333-3333-3333-3333-333333333333', 'hy', 'Արամ Խաչատրյանի անվան համերգասրահ',
        'Երևանի կենտրոնական համերգասրահը։', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- ================================================================
-- 2. USERS (Customers)
-- Fixed: Removed 'role' column (Schema doesn't have it)
-- ================================================================
INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, status, failed_login_attempts,
                   email_verified, created_at, last_modified_at)
VALUES ('a0000000-0000-0000-0000-000000000001', 'customer@example.com',
        '$2a$12$ibgO8gE2JgrJOpLmz75UXu9LLr9.6EoCyb8uV3VVELVgUFBFxgmEG', 'Regular', 'Customer', '+37411111111', 'ACTIVE',
        0, true, NOW(), NOW()),
       ('a0000000-0000-0000-0000-000000000002', 'anna.petrosyan@example.com',
        '$2a$12$ibgO8gE2JgrJOpLmz75UXu9LLr9.6EoCyb8uV3VVELVgUFBFxgmEG', 'Anna', 'Petrosyan', '+37422222222', 'ACTIVE',
        0, true, NOW(), NOW()),
       ('a0000000-0000-0000-0000-000000000003', 'karen.sargsyan@example.com',
        '$2a$12$ibgO8gE2JgrJOpLmz75UXu9LLr9.6EoCyb8uV3VVELVgUFBFxgmEG', 'Karen', 'Sargsyan', '+37433333333', 'ACTIVE',
        0, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 3. STAFF IDENTITIES & PERMISSIONS
-- Fixed: Used valid HEX UUIDs (f0.., f1.., f2..) instead of s/m/p
-- ================================================================

-- 3.1 Staff Identities (Prefix f0)
INSERT INTO staff_identities (id, email, password_hash, first_name, last_name, is_platform_super_admin, status,
                              failed_login_attempts, version, created_at, last_modified_at)
VALUES
-- Super Admin (Global Access)
('f0000000-0000-0000-0000-000000000000', 'super@venues.app',
 '$2a$12$ibgO8gE2JgrJOpLmz75UXu9LLr9.6EoCyb8uV3VVELVgUFBFxgmEG', 'System', 'Admin', true, 'ACTIVE', 0, 0, NOW(), NOW()),

-- Ministry Owner (Minister)
('f0000000-0000-0000-0000-000000000001', 'minister@culture.gov.am',
 '$2a$12$ibgO8gE2JgrJOpLmz75UXu9LLr9.6EoCyb8uV3VVELVgUFBFxgmEG', 'Aram', 'Khachatryan', false, 'ACTIVE', 0, 0, NOW(),
 NOW()),

-- Venue Manager (Opera House Manager)
('f0000000-0000-0000-0000-000000000002', 'manager@opera.am',
 '$2a$12$ibgO8gE2JgrJOpLmz75UXu9LLr9.6EoCyb8uV3VVELVgUFBFxgmEG', 'Opera', 'Manager', false, 'ACTIVE', 0, 0, NOW(),
 NOW())
ON CONFLICT (id) DO NOTHING;

-- 3.2 Staff Memberships (Link to Organization) (Prefix f1)
INSERT INTO staff_memberships (id, staff_identity_id, organization_id, org_role, is_active, created_at,
                               last_modified_at)
VALUES
-- Minister owns the Ministry Org
('f1000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
 'OWNER', true, NOW(), NOW()),

-- Opera Manager is a Member of Ministry Org
('f1000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
 'MEMBER', true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 3.3 Staff Venue Permissions (Prefix f2)
INSERT INTO staff_venue_permissions (id, membership_id, venue_id, role, created_at, last_modified_at)
VALUES
-- Opera Manager manages the Opera House Venue
('f2000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
 'MANAGER', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 4. PLATFORMS
-- ================================================================
INSERT INTO platforms (id, name, api_url, shared_secret, status, webhook_enabled, description, contact_email,
                       rate_limit, webhook_success_count, webhook_failure_count, created_at, last_modified_at)
VALUES ('bbbbbbbb-1111-1111-1111-111111111111', 'Ticketmaster Armenia', 'https://kind-mouse-84.webhook.cool', 'secret1',
        'ACTIVE', true, 'Partner', 'tech@tm.am', 1000, 0, 0, NOW(), NOW()),
       ('bbbbbbbb-2222-2222-2222-222222222222', 'TomsTickets', 'https://test.local/api', 'secret2', 'ACTIVE', false,
        'Booking platform', 'api@toms.com', 500, 0, 0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 5. SEATING CHARTS
-- ================================================================
INSERT INTO seating_charts (id, venue_id, name, width, height, is_active, background_url, created_at, last_modified_at)
VALUES ('99999999-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Opera Main Hall', 2000, 2000,
        true, 'http://img.url', NOW(), NOW()),
       ('99999999-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'Drama Theatre Hall', 1500,
        1500, true, NULL, NOW(), NOW()),
       ('99999999-3333-3333-3333-333333333333', '33333333-3333-3333-3333-333333333333', 'Concert Hall VIP Lounge', 1000,
        1000, true, 'http://img.url', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 6. EVENTS
-- Note: Looking up Category IDs by key (requires V2)
-- ================================================================
INSERT INTO events (id, venue_id, category_id, seating_chart_id, title, description, currency, status, created_at,
                    last_modified_at)
VALUES ('eeeeeeee-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111',
        (SELECT id FROM event_categories WHERE code = 'theater'), '99999999-1111-1111-1111-111111111111',
        'Swan Lake', 'Tchaikovsky masterpiece', 'AMD', 'UPCOMING', NOW(), NOW()),
       ('eeeeeeee-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222',
        (SELECT id FROM event_categories WHERE code = 'theater'), '99999999-2222-2222-2222-222222222222',
        'Hamlet', 'Shakespeare tragedy', 'AMD', 'UPCOMING', NOW(), NOW()),
       ('eeeeeeee-3333-3333-3333-333333333333', '33333333-3333-3333-3333-333333333333',
        (SELECT id FROM event_categories WHERE code = 'concert'), '99999999-3333-3333-3333-333333333333',
        'Philharmonic Orchestra', 'Classical evening', 'AMD', 'UPCOMING', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 6.1 EVENT TRANSLATIONS
INSERT INTO event_translations (event_id, language, title, description, created_at, last_modified_at)
VALUES ('eeeeeeee-1111-1111-1111-111111111111', 'hy', 'Կարապի լիճ', 'Չայկովսկու գլուխգործոցը', NOW(), NOW()),
       ('eeeeeeee-2222-2222-2222-222222222222', 'hy', 'Համլետ', 'Շեքսպիրի ողբերգություն', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- ================================================================
-- 7. EVENT SESSIONS
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
-- 8. CHART ZONES
-- ================================================================
INSERT INTO chart_zones (id, chart_id, parent_zone_id, name, code, x_position, y_position, rotation, created_at,
                         last_modified_at)
VALUES (1, '99999999-1111-1111-1111-111111111111', NULL, 'Orchestra', 'ORCH', 500.0, 1000.0, 0, NOW(), NOW()),
       (2, '99999999-1111-1111-1111-111111111111', NULL, 'Balcony', 'BALC', 500.0, 500.0, 0, NOW(), NOW()),
       (3, '99999999-1111-1111-1111-111111111111', NULL, 'Standing Area', 'STAND', 500.0, 1500.0, 0, NOW(), NOW()),
       (5, '99999999-3333-3333-3333-333333333333', NULL, 'VIP Lounge', 'VIP', 300.0, 800.0, 0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('chart_zones', 'id'), 50);

-- ================================================================
-- 9. CHART GA AREAS
-- ================================================================
INSERT INTO chart_ga_areas (id, zone_id, name, code, capacity, created_at, last_modified_at)
VALUES (100, 3, 'General Standing', 'STAND-GA-1', 100, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('chart_ga_areas', 'id'), 200);

-- ================================================================
-- 10. CHART TABLES
-- ================================================================
INSERT INTO chart_tables (id, zone_id, table_number, code, seat_capacity, shape, x_position, y_position, width, height,
                          rotation, created_at, last_modified_at)
VALUES (1, 5, 'T1', 'VIP-T1', 4, 'ROUND', 200.0, 700.0, 100.0, 100.0, 0, NOW(), NOW()),
       (2, 5, 'T2', 'VIP-T2', 4, 'ROUND', 400.0, 700.0, 100.0, 100.0, 0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('chart_tables', 'id'), 50);

-- ================================================================
-- 11. CHART SEATS
-- ================================================================
INSERT INTO chart_seats (id, zone_id, table_id, row_label, seat_number, code, category_key, x_position, y_position,
                         created_at, last_modified_at)
VALUES (1, 1, NULL, 'A', '1', 'ORCH_ROW-A_SEAT-1', 'STANDARD', 100.0, 100.0, NOW(), NOW()),
       (2, 1, NULL, 'A', '2', 'ORCH_ROW-A_SEAT-2', 'STANDARD', 200.0, 100.0, NOW(), NOW()),
       (10, 5, 1, 'T1', '1', 'VIP_ROW-T1_SEAT-1', 'VIP', 180.0, 680.0, NOW(), NOW()),
       (11, 5, 1, 'T1', '2', 'VIP_ROW-T1_SEAT-2', 'VIP', 220.0, 680.0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('chart_seats', 'id'), 50);

-- ================================================================
-- 12. PRICE TEMPLATES
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
-- 13. SESSION CONFIGS
-- ================================================================
INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
VALUES ('55555555-1111-1111-1111-111111111111', 1, 'dddddddd-1111-1111-1111-111111111111', 'AVAILABLE', NOW(), NOW()),
       ('55555555-1111-1111-1111-111111111111', 2, 'dddddddd-2222-2222-2222-222222222222', 'SOLD', NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO session_level_configs (session_id, ga_area_id, price_template_id, capacity, sold_count, status, created_at,
                                   last_modified_at)
VALUES ('55555555-1111-1111-1111-111111111111', 100, 'dddddddd-2222-2222-2222-222222222222', 100, 25, 'AVAILABLE',
        NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO session_table_configs (session_id, table_id, price_template_id, booking_mode, status, created_at,
                                   last_modified_at)
VALUES ('55555555-3333-3333-3333-333333333333', 1, 'dddddddd-3333-3333-3333-333333333333', 'TABLE_ONLY', 'AVAILABLE',
        NOW(), NOW())
ON CONFLICT DO NOTHING;

-- ================================================================
-- 14. GUESTS
-- ================================================================
INSERT INTO guests (id, name, email, phone, created_at, last_modified_at)
VALUES ('eeeeeeee-9999-1111-1111-111111111111', 'John Doe', 'guest@example.com', '+37499888777', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 15. BOOKINGS
-- Fixed: Removed 'reservation_token'
-- ================================================================
INSERT INTO bookings (id, user_id, guest_id, session_id, venue_id, platform_id, total_price, currency, status,
                      created_at, last_modified_at)
VALUES ('bbbbbbbb-1111-1111-1111-111111111111',
        'a0000000-0000-0000-0000-000000000002',
        NULL,
        '55555555-1111-1111-1111-111111111111',
        '11111111-1111-1111-1111-111111111111',
        'bbbbbbbb-1111-1111-1111-111111111111',
        6000.00, 'AMD', 'CONFIRMED', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ================================================================
-- 16. BOOKING ITEMS
-- ================================================================
INSERT INTO booking_items (booking_id, seat_id, ga_area_id, price_template_name, unit_price, quantity, created_at,
                           last_modified_at)
VALUES ('bbbbbbbb-1111-1111-1111-111111111111', 2, NULL, 'Standard', 6000.00, 1, NOW(), NOW());

-- ================================================================
-- Final Sequence Updates
-- ================================================================
SELECT setval(pg_get_serial_sequence('booking_items', 'id'), 50);
SELECT setval(pg_get_serial_sequence('session_seat_configs', 'id'), 50);
SELECT setval(pg_get_serial_sequence('session_level_configs', 'id'), 50);
SELECT setval(pg_get_serial_sequence('session_table_configs', 'id'), 50);