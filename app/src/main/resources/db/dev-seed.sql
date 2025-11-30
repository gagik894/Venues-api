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

    END
$$;