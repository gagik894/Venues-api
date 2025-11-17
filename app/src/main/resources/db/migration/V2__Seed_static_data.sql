-- Seed Categories
INSERT INTO event_categories (category_key, name, display_order, is_active, color, created_at, last_modified_at)
VALUES ('concert', 'Concerts', 10, true, '#FF5733', NOW(), NOW()),
       ('sport', 'Sports', 20, true, '#33FF57', NOW(), NOW()),
       ('theater', 'Theater', 30, true, '#3357FF', NOW(), NOW()),
       ('conference', 'Conferences', 40, true, '#F3FF33', NOW(), NOW())
ON CONFLICT (category_key) DO NOTHING;

-- Seed Default Platform
INSERT INTO platforms (id, name, status, api_url, shared_secret, webhook_enabled,
                       webhook_failure_count, webhook_success_count, created_at, last_modified_at)
VALUES ('11111111-1111-1111-1111-111111111111', -- Hardcoded UUID
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