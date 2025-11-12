-- Migration: Add table booking support
-- Description: Adds table-related fields to levels and creates session_table_configs and cart_tables

-- ========================================
-- PART 1: Add table fields to levels
-- ========================================

-- Add table support fields to levels table
ALTER TABLE levels
    ADD COLUMN is_table           BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN table_booking_mode VARCHAR(20),
    ADD COLUMN table_capacity     INTEGER;

-- Add constraint: table_booking_mode must be set if is_table = true
ALTER TABLE levels
    ADD CONSTRAINT chk_table_booking_mode
        CHECK (
            (is_table = FALSE) OR
            (is_table = TRUE AND table_booking_mode IS NOT NULL)
            );

-- Add constraint: table_capacity must be set if is_table = true
ALTER TABLE levels
    ADD CONSTRAINT chk_table_capacity
        CHECK (
            (is_table = FALSE) OR
            (is_table = TRUE AND table_capacity IS NOT NULL AND table_capacity > 0)
            );

-- Add index for table queries
CREATE INDEX idx_level_is_table ON levels (is_table) WHERE is_table = TRUE;

COMMENT ON COLUMN levels.is_table IS 'Indicates if this level represents a table (group of seats)';
COMMENT ON COLUMN levels.table_booking_mode IS 'How the table can be booked: SEATS_ONLY, TABLE_ONLY, or FLEXIBLE';
COMMENT ON COLUMN levels.table_capacity IS 'Number of seats in the table';

-- ========================================
-- PART 2: Create session_table_configs
-- ========================================

CREATE TABLE session_table_configs
(
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT      NOT NULL,
    table_id          BIGINT      NOT NULL,
    price_template_id BIGINT,
    status            VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_session_table_session FOREIGN KEY (session_id)
        REFERENCES event_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_session_table_template FOREIGN KEY (price_template_id)
        REFERENCES event_price_templates (id) ON DELETE SET NULL,
    CONSTRAINT uk_session_table_config UNIQUE (session_id, table_id),
    CONSTRAINT chk_session_table_status CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD', 'BLOCKED', 'CLOSED'))
);

CREATE INDEX idx_session_table_config_session ON session_table_configs (session_id);
CREATE INDEX idx_session_table_config_table ON session_table_configs (table_id);
CREATE INDEX idx_session_table_config_template ON session_table_configs (price_template_id);
CREATE INDEX idx_session_table_config_status ON session_table_configs (status);

COMMENT ON TABLE session_table_configs IS 'Tracks table availability and pricing per session';
COMMENT ON COLUMN session_table_configs.table_id IS 'References levels table where is_table = true';
COMMENT ON COLUMN session_table_configs.status IS 'AVAILABLE: can be booked, RESERVED: in cart, SOLD: purchased, BLOCKED: some seats reserved/sold, CLOSED: manually closed';

-- ========================================
-- PART 3: Create cart_tables
-- ========================================

CREATE TABLE cart_tables
(
    id         BIGSERIAL PRIMARY KEY,
    cart_id    UUID           NOT NULL,
    session_id BIGINT         NOT NULL,
    table_id   BIGINT         NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    seat_count INTEGER        NOT NULL,
    created_at TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cart_table_cart FOREIGN KEY (cart_id)
        REFERENCES carts (token) ON DELETE CASCADE,
    CONSTRAINT uk_cart_table_session_table UNIQUE (session_id, table_id),
    CONSTRAINT chk_cart_table_price CHECK (unit_price >= 0),
    CONSTRAINT chk_cart_table_seat_count CHECK (seat_count > 0)
);

CREATE INDEX idx_cart_table_session_id ON cart_tables (session_id);
CREATE INDEX idx_cart_table_cart ON cart_tables (cart_id);
CREATE INDEX idx_cart_table_table_id ON cart_tables (table_id);

COMMENT ON TABLE cart_tables IS 'Tables (whole unit bookings) in shopping cart';
COMMENT ON COLUMN cart_tables.unit_price IS 'Table price snapshotted at add-to-cart time';
COMMENT ON COLUMN cart_tables.seat_count IS 'Number of seats in this table (for display)';

-- ========================================
-- VERIFICATION
-- ========================================

-- Verify table structure
SELECT 'levels'                                as table_name,
       COUNT(*) FILTER (WHERE is_table = TRUE) as table_count,
       COUNT(*)                                as total_levels
FROM levels
UNION ALL
SELECT 'session_table_configs',
       COUNT(*),
       COUNT(*)
FROM session_table_configs
UNION ALL
SELECT 'cart_tables',
       COUNT(*),
       COUNT(*)
FROM cart_tables;

