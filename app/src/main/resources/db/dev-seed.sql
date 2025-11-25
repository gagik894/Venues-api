/* SEED DATA V4.1 - "The Empire Expansion" (Refactored with Constants)
   Changes:
     - Extracted ALL hardcoded Enums/Strings into the DECLARE block.
     - No magic strings in the INSERT statements.
*/

DO
$$
    DECLARE
        -- ==========================================
        -- 0. SYSTEM CONSTANTS (Enum Definitions)
        -- ==========================================

        -- Organization & Venue Types
        c_org_type_private CONSTANT varchar := 'PRIVATE';
        c_venue_cat_hall   CONSTANT varchar := 'CONCERT_HALL';
        c_venue_cat_club   CONSTANT varchar := 'CLUB';

        -- Event Categories
        c_ev_cat_concert   CONSTANT varchar := 'CONCERT';
        c_ev_cat_festival  CONSTANT varchar := 'FESTIVAL';

        -- Statuses
        c_status_active    CONSTANT varchar := 'ACTIVE';
        c_status_upcoming  CONSTANT varchar := 'UPCOMING';
        c_status_available CONSTANT varchar := 'AVAILABLE';
        c_status_blocked   CONSTANT varchar := 'BLOCKED';

        -- Roles
        c_role_owner       CONSTANT varchar := 'OWNER';
        c_role_admin       CONSTANT varchar := 'ADMIN';
        c_role_member      CONSTANT varchar := 'MEMBER';

        -- Venue Permissions
        c_perm_manager     CONSTANT varchar := 'MANAGER';
        c_perm_viewer      CONSTANT varchar := 'VIEWER';
        c_perm_scanner     CONSTANT varchar := 'SCANNER';

        -- Seating / Inventory Keys
        c_shape_rect       CONSTANT varchar := 'RECTANGLE';
        c_cat_key_vip      CONSTANT varchar := 'VIP';
        c_cat_key_premium  CONSTANT varchar := 'PREMIUM';

        -- Currencies & Formatting
        c_currency_amd     CONSTANT varchar := 'AMD';
        c_tz_yerevan       CONSTANT varchar := 'Asia/Yerevan';

        -- ==========================================
        -- 1. ID VARIABLES (Pre-generating UUIDs)
        -- ==========================================

        -- Organization
        v_org_id                    uuid    := gen_random_uuid();
        v_merchant_id               uuid    := gen_random_uuid();

        -- Staff IDs
        v_staff_super_id            uuid    := gen_random_uuid();
        v_staff_owner_id            uuid    := gen_random_uuid();
        v_staff_manager_id          uuid    := gen_random_uuid();
        v_staff_scanner_id          uuid    := gen_random_uuid();
        v_membership_owner          uuid    := gen_random_uuid();
        v_membership_manager        uuid    := gen_random_uuid();
        v_membership_scanner        uuid    := gen_random_uuid();

        -- Venue A: Hall
        v_venue_hall_id             uuid    := gen_random_uuid();
        v_city_yvn_id               bigint;
        v_cat_hall_id               bigint;
        v_chart_hall_id             uuid    := gen_random_uuid();

        -- Venue B: Club
        v_venue_club_id             uuid    := gen_random_uuid();
        v_city_gym_id               bigint;
        v_cat_club_id               bigint;
        v_chart_club_id             uuid    := gen_random_uuid();

        -- Event Helpers
        v_event_hall_id             uuid    := gen_random_uuid();
        v_session_hall_id           uuid    := gen_random_uuid();
        v_event_club_id             uuid    := gen_random_uuid();
        v_session_club_id           uuid    := gen_random_uuid();

        -- Prices
        v_price_vip                 uuid    := gen_random_uuid();
        v_price_standard            uuid    := gen_random_uuid();
        v_price_early_bird          uuid    := gen_random_uuid();

        -- Loop Helpers
        v_zone_id                   bigint;
        v_table_id                  bigint;
        i                           integer;
        j                           integer;

    BEGIN

        -- ====================================================
        -- 1. ORGANIZATION & MERCHANT
        -- ====================================================
        RAISE NOTICE 'Seeding Organization...';

        INSERT INTO organizations (id, name, slug, type, contact_email, is_active, created_at, last_modified_at)
        VALUES (v_org_id, 'Acme Entertainment Group', 'acme-ent', c_org_type_private, 'biz@acme.am', true, NOW(),
                NOW());

        INSERT INTO merchant_profile (id, name, organization_id, created_at, last_modified_at)
        VALUES (v_merchant_id, 'Acme Global Merchant', v_org_id, NOW(), NOW());

        UPDATE organizations SET default_merchant_profile_id = v_merchant_id WHERE id = v_org_id;

        -- ====================================================
        -- 2. STAFF HIERARCHY
        -- ====================================================
        RAISE NOTICE 'Seeding Staff...';

        -- A. Super Admin
        INSERT INTO staff_identities (id, email, password_hash, first_name, last_name, is_platform_super_admin, status,
                                      created_at, last_modified_at)
        VALUES (v_staff_super_id, 'admin@platform.com', '$2a$12$RJk.jI4Wd7.X.mYTq3f4EO/y/2.d.z.w.g.h.j.k.l.m.n.o.p',
                'System', 'Admin', true, c_status_active, NOW(), NOW());

        -- B. Org Owner
        INSERT INTO staff_identities (id, email, password_hash, first_name, last_name, status, created_at,
                                      last_modified_at)
        VALUES (v_staff_owner_id, 'owner@acme.am', '$2a$12$RJk.jI4Wd7.X.mYTq3f4EO/y/2.d.z.w.g.h.j.k.l.m.n.o.p', 'Levon',
                'Owner', c_status_active, NOW(), NOW());

        INSERT INTO staff_memberships (id, staff_identity_id, organization_id, org_role, created_at, last_modified_at)
        VALUES (v_membership_owner, v_staff_owner_id, v_org_id, c_role_owner, NOW(), NOW());

        -- C. Venue Manager
        INSERT INTO staff_identities (id, email, password_hash, first_name, last_name, status, created_at,
                                      last_modified_at)
        VALUES (v_staff_manager_id, 'manager@acme.am', '$2a$12$RJk.jI4Wd7.X.mYTq3f4EO/y/2.d.z.w.g.h.j.k.l.m.n.o.p',
                'Ani', 'Planner', c_status_active, NOW(), NOW());

        INSERT INTO staff_memberships (id, staff_identity_id, organization_id, org_role, created_at, last_modified_at)
        VALUES (v_membership_manager, v_staff_manager_id, v_org_id, c_role_admin, NOW(), NOW());

        -- D. Scanner
        INSERT INTO staff_identities (id, email, password_hash, first_name, last_name, status, created_at,
                                      last_modified_at)
        VALUES (v_staff_scanner_id, 'guard@acme.am', '$2a$12$RJk.jI4Wd7.X.mYTq3f4EO/y/2.d.z.w.g.h.j.k.l.m.n.o.p',
                'Gevorg', 'Guard', c_status_active, NOW(), NOW());

        INSERT INTO staff_memberships (id, staff_identity_id, organization_id, org_role, created_at, last_modified_at)
        VALUES (v_membership_scanner, v_staff_scanner_id, v_org_id, c_role_member, NOW(), NOW());

        -- ====================================================
        -- 3. VENUES
        -- ====================================================
        RAISE NOTICE 'Seeding Venues...';

        -- Fetch Refs
        SELECT id INTO v_city_yvn_id FROM ref_cities WHERE slug = 'yerevan' LIMIT 1;
        SELECT id INTO v_city_gym_id FROM ref_cities WHERE slug = 'gyumri' LIMIT 1;
        SELECT id INTO v_cat_hall_id FROM ref_venue_categories WHERE code = c_venue_cat_hall LIMIT 1;
        SELECT id INTO v_cat_club_id FROM ref_venue_categories WHERE code = c_venue_cat_club LIMIT 1;

        -- Venue A
        INSERT INTO venues (id, organization_id, city_id, merchant_profile_id, category_id, name, slug, description,
                            address, status, time_zone, created_at, last_modified_at)
        VALUES (v_venue_hall_id, v_org_id, v_city_yvn_id, v_merchant_id, v_cat_hall_id,
                'Opera Grand Hall', 'opera-grand-hall', 'Classical venue.', '54 Tumanyan St', c_status_active,
                c_tz_yerevan, NOW(), NOW());

        -- Venue B
        INSERT INTO venues (id, organization_id, city_id, merchant_profile_id, category_id, name, slug, description,
                            address, status, time_zone, created_at, last_modified_at)
        VALUES (v_venue_club_id, v_org_id, v_city_gym_id, v_merchant_id, v_cat_club_id,
                'Black Box Club', 'black-box-gyumri', 'Techno vibes.', '22 Sayat Nova', c_status_active, c_tz_yerevan,
                NOW(), NOW());

        -- Org Links
        INSERT INTO organization_venue_links (organization_id, venue_id, created_at, last_modified_at)
        VALUES (v_org_id, v_venue_hall_id, NOW(), NOW()),
               (v_org_id, v_venue_club_id, NOW(), NOW());

        -- ====================================================
        -- 4. PERMISSIONS (Using Constants)
        -- ====================================================
        RAISE NOTICE 'Seeding Permissions...';

        -- Manager: MANAGER of Hall, VIEWER of Club
        INSERT INTO staff_venue_permissions (id, membership_id, venue_id, role, created_at, last_modified_at)
        VALUES (gen_random_uuid(), v_membership_manager, v_venue_hall_id, c_perm_manager, NOW(), NOW()),
               (gen_random_uuid(), v_membership_manager, v_venue_club_id, c_perm_viewer, NOW(), NOW());

        -- Scanner: SCANNER of Club
        INSERT INTO staff_venue_permissions (id, membership_id, venue_id, role, created_at, last_modified_at)
        VALUES (gen_random_uuid(), v_membership_scanner, v_venue_club_id, c_perm_scanner, NOW(), NOW());

        -- ====================================================
        -- 5. SEATING CHARTS
        -- ====================================================
        RAISE NOTICE 'Seeding Charts...';

        -- --- CHART A: HALL ---
        INSERT INTO seating_charts (id, venue_id, name, width, height, created_at, last_modified_at)
        VALUES (v_chart_hall_id, v_venue_hall_id, 'Symphony Layout', 3000, 3000, NOW(), NOW());

        -- Zone: Seats
        INSERT INTO chart_zones (chart_id, name, code, x_position, y_position, display_color, created_at,
                                 last_modified_at)
        VALUES (v_chart_hall_id, 'Orchestra', 'ORCHESTRA', 1500, 1000, '#B71C1C', NOW(), NOW())
        RETURNING id INTO v_zone_id;

        FOR i IN 1..5
            LOOP
                FOR j IN 1..10
                    LOOP
                        INSERT INTO chart_seats (zone_id, row_label, seat_number, code, category_key, x_position,
                                                 y_position, created_at, last_modified_at)
                        VALUES (v_zone_id, CHR(64 + i), cast(j as varchar), 'ORCH_' || CHR(64 + i) || '_' || j,
                                c_cat_key_vip, j * 60, i * 60, NOW(), NOW());
                    END LOOP;
            END LOOP;

        -- Zone: Tables
        INSERT INTO chart_zones (chart_id, name, code, x_position, y_position, display_color, created_at,
                                 last_modified_at)
        VALUES (v_chart_hall_id, 'Royal Boxes', 'BOXES', 500, 500, '#FFD700', NOW(), NOW())
        RETURNING id INTO v_zone_id;

        FOR i IN 1..2
            LOOP
                INSERT INTO chart_tables (zone_id, table_number, code, seat_capacity, shape, x_position, y_position,
                                          width, height, created_at, last_modified_at)
                VALUES (v_zone_id, 'Box ' || i, 'BOX_' || i, 4, c_shape_rect, 0, i * 300, 200, 150, NOW(), NOW())
                RETURNING id INTO v_table_id;

                FOR j IN 1..4
                    LOOP
                        INSERT INTO chart_seats (zone_id, table_id, row_label, seat_number, code, category_key,
                                                 x_position, y_position, created_at, last_modified_at)
                        VALUES (v_zone_id, v_table_id, 'Box', cast(j as varchar), 'BOX_' || i || '_S' || j,
                                c_cat_key_premium, 0, 0, NOW(), NOW());
                    END LOOP;
            END LOOP;

        -- --- CHART B: CLUB ---
        INSERT INTO seating_charts (id, venue_id, name, width, height, created_at, last_modified_at)
        VALUES (v_chart_club_id, v_venue_club_id, 'Rave Layout', 1000, 1000, NOW(), NOW());

        INSERT INTO chart_zones (chart_id, name, code, x_position, y_position, display_color, created_at,
                                 last_modified_at)
        VALUES (v_chart_club_id, 'Main Floor', 'FLOOR', 500, 500, '#212121', NOW(), NOW())
        RETURNING id INTO v_zone_id;

        INSERT INTO chart_ga_areas (zone_id, name, code, capacity, display_color, created_at, last_modified_at)
        VALUES (v_zone_id, 'General Admission', 'GA_MAIN', 500, '#00E676', NOW(), NOW());

        -- ====================================================
        -- 6. EVENTS & INVENTORY (Using Constants)
        -- ====================================================
        RAISE NOTICE 'Seeding Events...';

        -- --- EVENT A: CONCERT ---
        INSERT INTO events (id, venue_id, category_id, seating_chart_id, merchant_profile_id, title, description,
                            status, price_range, currency, created_at, last_modified_at)
        VALUES (v_event_hall_id, v_venue_hall_id, (SELECT id FROM ref_event_categories WHERE code = c_ev_cat_concert),
                v_chart_hall_id, v_merchant_id,
                'Aram Khachaturian Gala', 'Anniversary concert.', c_status_upcoming, '15000-50000', c_currency_amd,
                NOW(), NOW());

        INSERT INTO event_price_templates (id, event_id, template_name, price, color, is_anchor, created_at,
                                           last_modified_at)
        VALUES (v_price_vip, v_event_hall_id, 'Royal Box', 50000.00, '#FFD700', false, NOW(), NOW()),
               (v_price_standard, v_event_hall_id, 'Stalls', 15000.00, '#B71C1C', true, NOW(), NOW());

        INSERT INTO event_sessions (id, event_id, status, start_time, end_time, tickets_sold, created_at,
                                    last_modified_at)
        VALUES (v_session_hall_id, v_event_hall_id, c_status_upcoming, NOW() + interval '14 days',
                NOW() + interval '14 days 3 hours', 0, NOW(), NOW());

        -- Inventory Hall
        INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
        SELECT v_session_hall_id, s.id, v_price_standard, c_status_available, NOW(), NOW()
        FROM chart_seats s
                 JOIN chart_zones z ON s.zone_id = z.id
        WHERE z.chart_id = v_chart_hall_id
          AND s.category_key = c_cat_key_vip;

        INSERT INTO session_seat_configs (session_id, seat_id, price_template_id, status, created_at, last_modified_at)
        SELECT v_session_hall_id, s.id, v_price_vip, c_status_available, NOW(), NOW()
        FROM chart_seats s
                 JOIN chart_zones z ON s.zone_id = z.id
        WHERE z.chart_id = v_chart_hall_id
          AND s.category_key = c_cat_key_premium;

        -- --- EVENT B: CLUB ---
        INSERT INTO events (id, venue_id, category_id, seating_chart_id, merchant_profile_id, title, description,
                            status, price_range, currency, created_at, last_modified_at)
        VALUES (v_event_club_id, v_venue_club_id, (SELECT id FROM ref_event_categories WHERE code = c_ev_cat_festival),
                v_chart_club_id, v_merchant_id,
                'Neon Nights Rave', 'Techno.', c_status_upcoming, '3000', c_currency_amd, NOW(), NOW());

        INSERT INTO event_price_templates (id, event_id, template_name, price, color, is_anchor, created_at,
                                           last_modified_at)
        VALUES (v_price_early_bird, v_event_club_id, 'Early Bird', 3000.00, '#00E676', true, NOW(), NOW());

        INSERT INTO event_sessions (id, event_id, status, start_time, end_time, tickets_sold, created_at,
                                    last_modified_at)
        VALUES (v_session_club_id, v_event_club_id, c_status_upcoming, NOW() + interval '2 days',
                NOW() + interval '3 days', 0, NOW(), NOW());

        -- Inventory Club
        INSERT INTO session_level_configs (session_id, ga_area_id, price_template_id, status, capacity, sold_count,
                                           created_at, last_modified_at)
        SELECT v_session_club_id,
               ga.id,
               v_price_early_bird,
               c_status_available,
               ga.capacity,
               0,
               NOW(),
               NOW()
        FROM chart_ga_areas ga
                 JOIN chart_zones z ON ga.zone_id = z.id
        WHERE z.chart_id = v_chart_club_id;

        RAISE NOTICE 'Completed with Constants.';

    END
$$;