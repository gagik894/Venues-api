-- ================================================================
-- Venues API - Booking Module Database Migration
-- Version: 6
-- Description: Create tables for bookings, seat reservations, and guests
-- ================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ================================================================
-- GUESTS (for unauthenticated bookings)
-- ================================================================

CREATE TABLE guests
(
    id               BIGSERIAL PRIMARY KEY,
    email            VARCHAR(255) NOT NULL UNIQUE,
    name             VARCHAR(200) NOT NULL,
    phone            VARCHAR(20),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_guest_email ON guests (email);

-- ================================================================
-- SESSION SEAT CONFIGS (Seat pricing & status per session)
-- ================================================================

CREATE TABLE session_seat_configs
(
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT         NOT NULL REFERENCES event_sessions (id) ON DELETE CASCADE,
    seat_id           BIGINT         NOT NULL REFERENCES seats (id) ON DELETE CASCADE,
    price_template_id BIGINT         REFERENCES event_price_templates (id) ON DELETE SET NULL,
    price             DECIMAL(10, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_session_seat_config UNIQUE (session_id, seat_id),
    CONSTRAINT chk_session_seat_status CHECK (status IN ('AVAILABLE', 'CLOSED', 'BLOCKED'))
);

CREATE INDEX idx_session_seat_config_session ON session_seat_configs (session_id);
CREATE INDEX idx_session_seat_config_seat ON session_seat_configs (seat_id);
CREATE INDEX idx_session_seat_config_template ON session_seat_configs (price_template_id);
CREATE INDEX idx_session_seat_config_status ON session_seat_configs (status);

-- ================================================================
-- SESSION LEVEL CONFIGS (GA level pricing per session)
-- ================================================================

CREATE TABLE session_level_configs
(
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT         NOT NULL REFERENCES event_sessions (id) ON DELETE CASCADE,
    level_id          BIGINT         NOT NULL REFERENCES levels (id) ON DELETE CASCADE,
    price_template_id BIGINT         REFERENCES event_price_templates (id) ON DELETE SET NULL,
    price             DECIMAL(10, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_session_level_config UNIQUE (session_id, level_id),
    CONSTRAINT chk_session_level_status CHECK (status IN ('AVAILABLE', 'CLOSED'))
);

CREATE INDEX idx_session_level_config_session ON session_level_configs (session_id);
CREATE INDEX idx_session_level_config_level ON session_level_configs (level_id);
CREATE INDEX idx_session_level_config_template ON session_level_configs (price_template_id);
CREATE INDEX idx_session_level_config_status ON session_level_configs (status);

-- ================================================================
-- CART SEATS (Phase 1 - Temporary seat holds)
-- ================================================================

CREATE TABLE cart_seats
(
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT    NOT NULL REFERENCES event_sessions (id) ON DELETE CASCADE,
    seat_id           BIGINT    NOT NULL REFERENCES seats (id) ON DELETE CASCADE,
    user_id           BIGINT    REFERENCES users (id) ON DELETE SET NULL,
    guest_id          BIGINT    REFERENCES guests (id) ON DELETE SET NULL,
    reservation_token UUID      NOT NULL,
    expires_at        TIMESTAMP NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_cart_seat_session_seat UNIQUE (session_id, seat_id)
);

CREATE INDEX idx_cart_seat_session_id ON cart_seats (session_id);
CREATE INDEX idx_cart_seat_user_id ON cart_seats (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_cart_seat_guest_id ON cart_seats (guest_id) WHERE guest_id IS NOT NULL;
CREATE INDEX idx_cart_seat_token ON cart_seats (reservation_token);
CREATE INDEX idx_cart_seat_expires_at ON cart_seats (expires_at);

-- ================================================================
-- CART ITEMS (Temporary GA ticket holds)
-- ================================================================

CREATE TABLE cart_items
(
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT    NOT NULL REFERENCES event_sessions (id) ON DELETE CASCADE,
    level_id          BIGINT    NOT NULL REFERENCES levels (id) ON DELETE CASCADE,
    user_id           BIGINT    REFERENCES users (id) ON DELETE SET NULL,
    guest_id          BIGINT    REFERENCES guests (id) ON DELETE SET NULL,
    reservation_token UUID      NOT NULL,
    quantity          INTEGER   NOT NULL,
    expires_at        TIMESTAMP NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_cart_item_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_cart_item_session_id ON cart_items (session_id);
CREATE INDEX idx_cart_item_user_id ON cart_items (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_cart_item_guest_id ON cart_items (guest_id) WHERE guest_id IS NOT NULL;
CREATE INDEX idx_cart_item_token ON cart_items (reservation_token);
CREATE INDEX idx_cart_item_expires_at ON cart_items (expires_at);

-- ================================================================
-- BOOKINGS (Phase 2 - After checkout/payment)
-- ================================================================

CREATE TABLE bookings
(
    id                  UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    user_id             BIGINT         REFERENCES users (id) ON DELETE SET NULL,
    guest_id            BIGINT         REFERENCES guests (id) ON DELETE SET NULL,
    session_id          BIGINT         NOT NULL REFERENCES event_sessions (id) ON DELETE CASCADE,
    reservation_token   UUID           NOT NULL UNIQUE,
    total_price         DECIMAL(10, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'AMD',
    status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    confirmed_at        TIMESTAMP,
    cancelled_at        TIMESTAMP,
    cancellation_reason VARCHAR(500),
    payment_id          VARCHAR(100),
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_booking_owner CHECK (user_id IS NOT NULL OR guest_id IS NOT NULL),
    CONSTRAINT chk_booking_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'REFUNDED'))
);

CREATE INDEX idx_booking_user_id ON bookings (user_id);
CREATE INDEX idx_booking_guest_id ON bookings (guest_id);
CREATE INDEX idx_booking_session_id ON bookings (session_id);
CREATE INDEX idx_booking_status ON bookings (status);
CREATE INDEX idx_booking_reservation_token ON bookings (reservation_token);

-- ================================================================
-- BOOKING ITEMS (What's in each booking)
-- ================================================================

CREATE TABLE booking_items
(
    id                     BIGSERIAL PRIMARY KEY,
    booking_id             UUID           NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    seat_id                BIGINT REFERENCES seats (id) ON DELETE CASCADE,
    level_id               BIGINT REFERENCES levels (id) ON DELETE CASCADE,
    session_seat_config_id BIGINT         REFERENCES session_seat_configs (id) ON DELETE SET NULL,
    quantity               INTEGER        NOT NULL DEFAULT 1,
    unit_price             DECIMAL(10, 2) NOT NULL,
    price_template_name    VARCHAR(100),
    created_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_booking_item_type CHECK (seat_id IS NOT NULL OR level_id IS NOT NULL),
    CONSTRAINT chk_booking_item_quantity CHECK (
        (seat_id IS NOT NULL AND quantity = 1) OR
        (level_id IS NOT NULL AND quantity > 0)
        )
);

CREATE INDEX idx_booking_item_booking_id ON booking_items (booking_id);
CREATE INDEX idx_booking_item_seat_id ON booking_items (seat_id);
CREATE INDEX idx_booking_item_level_id ON booking_items (level_id);
CREATE INDEX idx_booking_item_config_id ON booking_items (session_seat_config_id);

-- ================================================================
-- COMMENTS
-- ================================================================

COMMENT ON TABLE guests IS 'Guest information for unauthenticated bookings (stored separately for reuse)';
COMMENT ON TABLE session_seat_configs IS 'Per-session seat configuration: pricing, status, availability';
COMMENT ON TABLE cart_seats IS 'Temporary seat holds (Phase 1) - only token + expiration';
COMMENT ON TABLE cart_items IS 'Temporary GA ticket holds (Phase 1) - only token + quantity + expiration';
COMMENT ON TABLE bookings IS 'Final bookings after checkout/payment (Phase 2)';
COMMENT ON TABLE booking_items IS 'Items in each booking (seats or GA tickets with prices)';

COMMENT ON COLUMN guests.email IS 'Unique email address (acts as guest identifier)';

COMMENT ON COLUMN session_seat_configs.price_template_id IS 'Which price template this seat uses for this session';
COMMENT ON COLUMN session_seat_configs.price IS 'Calculated price from price template (denormalized for performance)';
COMMENT ON COLUMN session_seat_configs.status IS 'AVAILABLE (can book), CLOSED (session-specific), BLOCKED (obstructed view)';

COMMENT ON COLUMN session_level_configs.price_template_id IS 'Which price template this GA level uses for this session';
COMMENT ON COLUMN session_level_configs.price IS 'Calculated price from price template (denormalized for performance)';
COMMENT ON COLUMN session_level_configs.status IS 'AVAILABLE (can book), CLOSED (session-specific)';

COMMENT ON COLUMN cart_seats.user_id IS 'Authenticated user (null for anonymous/guest)';
COMMENT ON COLUMN cart_seats.guest_id IS 'Guest (null for anonymous/authenticated user)';
COMMENT ON COLUMN cart_seats.reservation_token IS 'UUID token grouping all cart items (seats + GA) before checkout';
COMMENT ON COLUMN cart_seats.expires_at IS 'Auto-release seat if not checked out';

COMMENT ON COLUMN cart_items.user_id IS 'Authenticated user (null for anonymous/guest)';
COMMENT ON COLUMN cart_items.guest_id IS 'Guest (null for anonymous/authenticated user)';
COMMENT ON COLUMN cart_items.reservation_token IS 'UUID token grouping all cart items (seats + GA) before checkout';
COMMENT ON COLUMN cart_items.quantity IS 'Number of GA tickets for this level';

COMMENT ON COLUMN bookings.id IS 'UUID primary key (user-facing, safe for URLs/emails)';
COMMENT ON COLUMN bookings.user_id IS 'Authenticated user (null for guest)';
COMMENT ON COLUMN bookings.guest_id IS 'Guest (null for authenticated user)';
COMMENT ON COLUMN bookings.reservation_token IS 'UUID from cart that was converted to booking';
COMMENT ON COLUMN bookings.status IS 'PENDING (awaiting payment), CONFIRMED (paid), CANCELLED, REFUNDED';
COMMENT ON COLUMN bookings.payment_id IS 'Reference to payment transaction';

COMMENT ON COLUMN booking_items.seat_id IS 'Seat (for seated items, quantity always 1, can be null if GA)';
COMMENT ON COLUMN booking_items.level_id IS 'Level (for GA items, quantity > 0, can be null if seat)';
COMMENT ON COLUMN booking_items.session_seat_config_id IS 'Reference to seat configuration at time of booking';
COMMENT ON COLUMN booking_items.quantity IS 'Always 1 for seats, >0 for GA. One booking can have multiple seats AND multiple GA levels';
COMMENT ON COLUMN booking_items.unit_price IS 'Price per unit at time of booking (snapshot from session_seat_configs or level pricing)';
COMMENT ON COLUMN booking_items.price_template_name IS 'Template name for reference (VIP, Standard, etc.)';

