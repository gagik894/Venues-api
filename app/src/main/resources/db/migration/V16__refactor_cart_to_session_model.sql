-- ================================================================
-- V16: Refactor Cart to Session Model
-- ================================================================
-- Change from individual item expiration to unified cart session
-- All items in a cart expire together

-- Create carts table (cart session)
CREATE TABLE carts
(
    id               BIGSERIAL PRIMARY KEY,
    token            UUID      NOT NULL UNIQUE,
    user_id          BIGINT REFERENCES users (id) ON DELETE CASCADE,
    guest_id         BIGINT REFERENCES guests (id) ON DELETE CASCADE,
    event_session_id BIGINT    NOT NULL,
    expires_at       TIMESTAMP NOT NULL,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cart_token ON carts (token);
CREATE INDEX idx_cart_user ON carts (user_id);
CREATE INDEX idx_cart_expires ON carts (expires_at);
CREATE INDEX idx_cart_session ON carts (event_session_id);

COMMENT ON TABLE carts IS 'Shopping cart sessions - all items expire together';
COMMENT ON COLUMN carts.token IS 'Cart session token - replaces individual item tokens';
COMMENT ON COLUMN carts.expires_at IS 'When entire cart expires - extended on activity';
COMMENT ON COLUMN carts.last_activity_at IS 'Last time user interacted with cart';

-- Migrate existing cart_seats data to new structure
-- 1. Create carts from existing cart_seats (group by token)
INSERT INTO carts (token, user_id, guest_id, event_session_id, expires_at, created_at, last_modified_at)
SELECT DISTINCT ON (cs.reservation_token) cs.reservation_token,
                                          cs.user_id,
                                          cs.guest_id,
                                          cs.session_id,
                                          MAX(cs.expires_at) OVER (PARTITION BY cs.reservation_token), -- Latest expiration
                                          MIN(cs.created_at) OVER (PARTITION BY cs.reservation_token), -- Earliest creation
                                          NOW()
FROM cart_seats cs;

-- 2. Add cart_id column to cart_seats
ALTER TABLE cart_seats
    ADD COLUMN cart_id BIGINT;

-- 3. Populate cart_id from carts table
UPDATE cart_seats cs
SET cart_id = c.id
FROM carts c
WHERE cs.reservation_token = c.token;

-- 4. Make cart_id NOT NULL
ALTER TABLE cart_seats
    ALTER COLUMN cart_id SET NOT NULL;

-- 5. Add foreign key
ALTER TABLE cart_seats
    ADD CONSTRAINT fk_cart_seat_cart
        FOREIGN KEY (cart_id)
            REFERENCES carts (id)
            ON DELETE CASCADE;

-- 6. Drop old columns from cart_seats
ALTER TABLE cart_seats
    DROP COLUMN reservation_token,
    DROP COLUMN expires_at,
    DROP COLUMN user_id,
    DROP COLUMN guest_id;

-- 7. Drop old indexes
DROP INDEX IF EXISTS idx_cart_seat_token;
DROP INDEX IF EXISTS idx_cart_seat_expires_at;

-- 8. Add new index
CREATE INDEX idx_cart_seat_cart ON cart_seats (cart_id);

-- Migrate existing cart_items data to new structure
-- 1. Create carts from existing cart_items (group by token)
INSERT INTO carts (token, user_id, guest_id, event_session_id, expires_at, created_at, last_modified_at)
SELECT DISTINCT ON (ci.reservation_token) ci.reservation_token,
                                          ci.user_id,
                                          ci.guest_id,
                                          ci.session_id,
                                          MAX(ci.expires_at) OVER (PARTITION BY ci.reservation_token),
                                          MIN(ci.created_at) OVER (PARTITION BY ci.reservation_token),
                                          NOW()
FROM cart_items ci
WHERE NOT EXISTS (SELECT 1
                  FROM carts c
                  WHERE c.token = ci.reservation_token);

-- 2. Add cart_id column to cart_items
ALTER TABLE cart_items
    ADD COLUMN cart_id BIGINT;

-- 3. Populate cart_id from carts table
UPDATE cart_items ci
SET cart_id = c.id
FROM carts c
WHERE ci.reservation_token = c.token;

-- 4. Make cart_id NOT NULL
ALTER TABLE cart_items
    ALTER COLUMN cart_id SET NOT NULL;

-- 5. Add foreign key
ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_item_cart
        FOREIGN KEY (cart_id)
            REFERENCES carts (id)
            ON DELETE CASCADE;

-- 6. Drop old columns from cart_items
ALTER TABLE cart_items
    DROP COLUMN reservation_token,
    DROP COLUMN expires_at,
    DROP COLUMN user_id,
    DROP COLUMN guest_id;

-- 7. Drop old indexes
DROP INDEX IF EXISTS idx_cart_item_token;
DROP INDEX IF EXISTS idx_cart_item_expires_at;

-- 8. Add new index
CREATE INDEX idx_cart_item_cart ON cart_items (cart_id);

-- Update constraints and comments
COMMENT ON COLUMN cart_seats.cart_id IS 'FK to carts - all seats expire with cart session';
COMMENT ON COLUMN cart_items.cart_id IS 'FK to carts - all items expire with cart session';

