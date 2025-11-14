ALTER TABLE booking_items
    DROP CONSTRAINT booking_items_booking_id_fkey;

ALTER TABLE booking_items
    DROP CONSTRAINT booking_items_level_id_fkey;

ALTER TABLE booking_items
    DROP CONSTRAINT booking_items_seat_id_fkey;

ALTER TABLE booking_items
    DROP CONSTRAINT booking_items_session_seat_config_id_fkey;

ALTER TABLE bookings
    DROP CONSTRAINT bookings_guest_id_fkey;

ALTER TABLE bookings
    DROP CONSTRAINT bookings_session_id_fkey;

ALTER TABLE bookings
    DROP CONSTRAINT bookings_user_id_fkey;

ALTER TABLE cart_items
    DROP CONSTRAINT cart_items_guest_id_fkey;

ALTER TABLE cart_items
    DROP CONSTRAINT cart_items_level_id_fkey;

ALTER TABLE cart_items
    DROP CONSTRAINT cart_items_session_id_fkey;

ALTER TABLE cart_items
    DROP CONSTRAINT cart_items_user_id_fkey;

ALTER TABLE cart_seats
    DROP CONSTRAINT cart_seats_guest_id_fkey;

ALTER TABLE cart_seats
    DROP CONSTRAINT cart_seats_seat_id_fkey;

ALTER TABLE cart_seats
    DROP CONSTRAINT cart_seats_session_id_fkey;

ALTER TABLE cart_seats
    DROP CONSTRAINT cart_seats_user_id_fkey;

ALTER TABLE event_category_translations
    DROP CONSTRAINT event_category_translations_category_id_fkey;

ALTER TABLE event_price_templates
    DROP CONSTRAINT event_price_templates_event_id_fkey;

ALTER TABLE event_secondary_images
    DROP CONSTRAINT event_secondary_images_event_id_fkey;

ALTER TABLE event_session_price_overrides
    DROP CONSTRAINT event_session_price_overrides_session_id_fkey;

ALTER TABLE event_sessions
    DROP CONSTRAINT event_sessions_event_id_fkey;

ALTER TABLE event_tags
    DROP CONSTRAINT event_tags_event_id_fkey;

ALTER TABLE event_translations
    DROP CONSTRAINT event_translations_event_id_fkey;

ALTER TABLE events
    DROP CONSTRAINT events_category_id_fkey;

ALTER TABLE events
    DROP CONSTRAINT events_venue_id_fkey;

ALTER TABLE user_blocked_users
    DROP CONSTRAINT fk_blocked_user;

ALTER TABLE user_blocked_users
    DROP CONSTRAINT fk_blocking_user;

ALTER TABLE bookings
    DROP CONSTRAINT fk_booking_platform;

ALTER TABLE bookings
    DROP CONSTRAINT fk_booking_venue;

ALTER TABLE user_favorite_events
    DROP CONSTRAINT fk_favorite_user;

ALTER TABLE user_fcm_tokens
    DROP CONSTRAINT fk_fcm_user;

ALTER TABLE levels
    DROP CONSTRAINT fk_level_seating_chart;

ALTER TABLE user_promo_codes
    DROP CONSTRAINT fk_promo_user;

ALTER TABLE users
    DROP CONSTRAINT fk_user_referrer;

ALTER TABLE session_level_configs
    DROP CONSTRAINT session_level_configs_level_id_fkey;

ALTER TABLE session_level_configs
    DROP CONSTRAINT session_level_configs_price_template_id_fkey;

ALTER TABLE session_level_configs
    DROP CONSTRAINT session_level_configs_session_id_fkey;

ALTER TABLE session_seat_configs
    DROP CONSTRAINT session_seat_configs_price_template_id_fkey;

ALTER TABLE session_seat_configs
    DROP CONSTRAINT session_seat_configs_seat_id_fkey;

ALTER TABLE session_seat_configs
    DROP CONSTRAINT session_seat_configs_session_id_fkey;

ALTER TABLE webhook_events
    DROP CONSTRAINT webhook_events_platform_id_fkey;

ALTER TABLE levels
    ADD is_table BOOLEAN;

ALTER TABLE levels
    ADD table_booking_mode VARCHAR(20);

ALTER TABLE levels
    ADD table_capacity INTEGER;

ALTER TABLE levels
    ALTER COLUMN is_table SET NOT NULL;

DROP TABLE booking_items CASCADE;

DROP TABLE bookings CASCADE;

DROP TABLE cart_items CASCADE;

DROP TABLE cart_seats CASCADE;

DROP TABLE event_categories CASCADE;

DROP TABLE event_category_translations CASCADE;

DROP TABLE event_price_templates CASCADE;

DROP TABLE event_secondary_images CASCADE;

DROP TABLE event_session_price_overrides CASCADE;

DROP TABLE event_sessions CASCADE;

DROP TABLE event_tags CASCADE;

DROP TABLE event_translations CASCADE;

DROP TABLE events CASCADE;

DROP TABLE guests CASCADE;

DROP TABLE platforms CASCADE;

DROP TABLE session_level_configs CASCADE;

DROP TABLE session_seat_configs CASCADE;

DROP TABLE user_blocked_users CASCADE;

DROP TABLE user_favorite_events CASCADE;

DROP TABLE user_fcm_tokens CASCADE;

DROP TABLE user_promo_codes CASCADE;

DROP TABLE users CASCADE;

DROP TABLE webhook_events CASCADE;