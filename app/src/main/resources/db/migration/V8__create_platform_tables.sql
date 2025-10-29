-- ================================================================
-- Venues API - Platform Module Database Migration
-- Version: 8
-- Description: Create tables for external platform integrations and webhooks
-- ================================================================

-- ================================================================
-- PLATFORMS
-- ================================================================

CREATE TABLE platforms
(
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL UNIQUE,
    api_url               VARCHAR(500) NOT NULL,
    shared_secret         VARCHAR(255) NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    webhook_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    description           TEXT,
    contact_email         VARCHAR(255),
    rate_limit            INTEGER,
    last_webhook_success  TIMESTAMP,
    last_webhook_failure  TIMESTAMP,
    webhook_success_count BIGINT       NOT NULL DEFAULT 0,
    webhook_failure_count BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_platform_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE')),
    CONSTRAINT chk_rate_limit CHECK (rate_limit IS NULL OR rate_limit > 0)
);

CREATE INDEX idx_platform_status ON platforms (status);
CREATE INDEX idx_platform_webhook_enabled ON platforms (webhook_enabled);

-- ================================================================
-- WEBHOOK EVENTS
-- ================================================================

CREATE TABLE webhook_events
(
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    platform_id      BIGINT      NOT NULL REFERENCES platforms (id) ON DELETE CASCADE,
    event_type       VARCHAR(50) NOT NULL,
    session_id       BIGINT      NOT NULL,
    seat_identifier  VARCHAR(50),
    level_identifier VARCHAR(50),
    payload          TEXT        NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    response_code    INTEGER,
    response_body    TEXT,
    error_message    TEXT,
    attempt_count    INTEGER     NOT NULL DEFAULT 0,
    next_retry_at    TIMESTAMP,
    last_attempt_at  TIMESTAMP,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_webhook_event_type CHECK (event_type IN (
                                                            'SEAT_RESERVED',
                                                            'SEAT_RELEASED',
                                                            'SEAT_BOOKED',
                                                            'BOOKING_CANCELLED',
                                                            'GA_AVAILABILITY_CHANGED',
                                                            'SESSION_CONFIG_UPDATED'
        )),
    CONSTRAINT chk_webhook_status CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    CONSTRAINT chk_attempt_count CHECK (attempt_count >= 0 AND attempt_count <= 10)
);

CREATE INDEX idx_webhook_platform_id ON webhook_events (platform_id);
CREATE INDEX idx_webhook_status ON webhook_events (status);
CREATE INDEX idx_webhook_event_type ON webhook_events (event_type);
CREATE INDEX idx_webhook_session_id ON webhook_events (session_id);
CREATE INDEX idx_webhook_next_retry ON webhook_events (next_retry_at);
CREATE INDEX idx_webhook_created_at ON webhook_events (created_at);

-- Composite index for finding pending retries
CREATE INDEX idx_webhook_pending_retry ON webhook_events (status, next_retry_at, attempt_count)
    WHERE status = 'PENDING';

-- ================================================================
-- COMMENTS
-- ================================================================

COMMENT ON TABLE platforms IS 'External platforms that can integrate with our booking API';
COMMENT ON TABLE webhook_events IS 'Webhook callback events sent to platforms';

COMMENT ON COLUMN platforms.name IS 'Unique platform name';
COMMENT ON COLUMN platforms.api_url IS 'Base URL for platform API/webhooks';
COMMENT ON COLUMN platforms.shared_secret IS 'Secret key for HMAC signature validation';
COMMENT ON COLUMN platforms.status IS 'Platform status (ACTIVE, SUSPENDED, INACTIVE)';
COMMENT ON COLUMN platforms.webhook_enabled IS 'Whether to send webhook callbacks to this platform';
COMMENT ON COLUMN platforms.rate_limit IS 'Maximum API requests per minute (NULL = unlimited)';

COMMENT ON COLUMN webhook_events.event_type IS 'Type of event (SEAT_RESERVED, SEAT_RELEASED, etc.)';
COMMENT ON COLUMN webhook_events.status IS 'Delivery status (PENDING, DELIVERED, FAILED)';
COMMENT ON COLUMN webhook_events.payload IS 'JSON payload sent to platform';
COMMENT ON COLUMN webhook_events.attempt_count IS 'Number of delivery attempts';
COMMENT ON COLUMN webhook_events.next_retry_at IS 'Next scheduled retry time for failed deliveries';

