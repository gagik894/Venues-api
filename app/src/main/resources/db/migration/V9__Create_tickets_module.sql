-- =========================================================
-- 1. TICKETS
-- Includes the "version" column originally added via ALTER
-- =========================================================
CREATE TABLE tickets
(
    id               UUID PRIMARY KEY   DEFAULT gen_random_uuid(),
    booking_id              UUID                NOT NULL,
    booking_item_id         BIGINT              NOT NULL,
    event_session_id        UUID                NOT NULL,
    qr_code                 VARCHAR(500) UNIQUE NOT NULL,
    ticket_type             VARCHAR(20)         NOT NULL,
    seat_id                 BIGINT,
    ga_area_id              BIGINT,
    table_id                BIGINT,
    max_scan_count          INT                 NOT NULL DEFAULT 1,
    status                  VARCHAR(20)         NOT NULL DEFAULT 'VALID',
    version          BIGINT    NOT NULL DEFAULT 0, -- Merged from ALTER statement
    invalidated_at          TIMESTAMP,
    invalidated_by_staff_id UUID,
    invalidation_reason     VARCHAR(500),
    created_at              TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for tickets
CREATE INDEX idx_ticket_qr_code ON tickets (qr_code);
CREATE INDEX idx_ticket_booking_id ON tickets (booking_id);
CREATE INDEX idx_ticket_status ON tickets (status);
CREATE INDEX idx_ticket_event_session ON tickets (event_session_id);

-- =========================================================
-- 2. SCANNER SESSIONS
-- Uses the updated schema with "last_modified_at"
-- =========================================================
CREATE TABLE scanner_sessions
(
    id                  UUID PRIMARY KEY             DEFAULT gen_random_uuid(),
    event_id            UUID                NOT NULL,
    session_name        VARCHAR(255)        NOT NULL,
    secret_token        VARCHAR(100) UNIQUE NOT NULL,
    valid_until         TIMESTAMP           NOT NULL,
    active              BOOLEAN             NOT NULL DEFAULT true,
    scan_location       VARCHAR(255),
    venue_id            UUID                NOT NULL,
    created_by_staff_id UUID                NOT NULL,
    created_at          TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at    TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for scanner_sessions
CREATE INDEX idx_scanner_session_token ON scanner_sessions (secret_token);
CREATE INDEX idx_scanner_session_event ON scanner_sessions (event_id);

-- =========================================================
-- 3. TICKET SCANS
-- Depends on both tickets and scanner_sessions
-- =========================================================
CREATE TABLE ticket_scans
(
    id                 BIGSERIAL PRIMARY KEY,
    ticket_id          UUID      NOT NULL REFERENCES tickets (id),
    scanner_session_id UUID      NOT NULL REFERENCES scanner_sessions (id),
    scanned_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scan_location      VARCHAR(255),
    device_info        VARCHAR(255),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for ticket_scans
CREATE INDEX idx_scan_session ON ticket_scans (scanner_session_id);
CREATE INDEX idx_scan_ticket ON ticket_scans (ticket_id);
CREATE INDEX idx_scan_timestamp ON ticket_scans (scanned_at);