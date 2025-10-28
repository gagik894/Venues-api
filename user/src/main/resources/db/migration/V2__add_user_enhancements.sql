-- Venues API - User Module Enhancement
-- Migration: Add new fields to users table and create related tables
-- Version: V2
-- Date: 2025-10-28

-- ===================================================================
-- ALTER users table - Add new fields
-- ===================================================================

-- Add avatar URL field
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512);

COMMENT ON COLUMN users.avatar_url IS 'URL to user profile picture/avatar stored in CDN';

-- Add referral program fields
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS referral_code VARCHAR(20) UNIQUE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS referrer_id BIGINT;

COMMENT ON COLUMN users.referral_code IS 'Unique referral code for this user, used in referral program';
COMMENT ON COLUMN users.referrer_id IS 'ID of the user who referred this user (nullable)';

-- Create index for referral code lookups
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_referral_code ON users (referral_code) WHERE referral_code IS NOT NULL;

-- Create index for referrer queries
CREATE INDEX IF NOT EXISTS idx_user_referrer_id ON users (referrer_id) WHERE referrer_id IS NOT NULL;

-- Add foreign key constraint for self-referential relationship
ALTER TABLE users
    ADD CONSTRAINT fk_user_referrer
        FOREIGN KEY (referrer_id) REFERENCES users (id)
            ON DELETE SET NULL;

-- ===================================================================
-- Create user_fcm_tokens table
-- ===================================================================
-- Stores Firebase Cloud Messaging tokens for push notifications
-- One user can have multiple tokens (multiple devices)

CREATE TABLE IF NOT EXISTS user_fcm_tokens
(
    -- Primary Key
    id               BIGSERIAL PRIMARY KEY,

    -- Foreign Key to users
    user_id          BIGINT       NOT NULL,

    -- FCM Token Data
    token            VARCHAR(512) NOT NULL UNIQUE,
    platform         VARCHAR(20)  NOT NULL,
    device_name      VARCHAR(100),

    -- Tracking
    last_used_at     TIMESTAMP,

    -- Audit Fields
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT fk_fcm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_fcm_platform CHECK (platform IN ('android', 'ios', 'web'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_fcm_user_id ON user_fcm_tokens (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_fcm_token ON user_fcm_tokens (token);
CREATE INDEX IF NOT EXISTS idx_fcm_last_used ON user_fcm_tokens (last_used_at);

-- Comments
COMMENT ON TABLE user_fcm_tokens IS 'Firebase Cloud Messaging tokens for push notifications';
COMMENT ON COLUMN user_fcm_tokens.user_id IS 'User who owns this FCM token';
COMMENT ON COLUMN user_fcm_tokens.token IS 'FCM token string from Firebase SDK (unique)';
COMMENT ON COLUMN user_fcm_tokens.platform IS 'Platform: android, ios, or web';
COMMENT ON COLUMN user_fcm_tokens.device_name IS 'Optional device name/identifier';
COMMENT ON COLUMN user_fcm_tokens.last_used_at IS 'Last time this token was used successfully';

-- ===================================================================
-- Create user_favorite_events table
-- ===================================================================
-- Join table for users and their favorite events

CREATE TABLE IF NOT EXISTS user_favorite_events
(
    -- Primary Key
    id                    BIGSERIAL PRIMARY KEY,

    -- Foreign Keys
    user_id               BIGINT    NOT NULL,
    event_id              BIGINT    NOT NULL,

    -- Additional Fields
    notifications_enabled BOOLEAN   NOT NULL DEFAULT TRUE,
    user_note             VARCHAR(500),

    -- Audit Fields
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_event_favorite UNIQUE (user_id, event_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_favorite_user_id ON user_favorite_events (user_id);
CREATE INDEX IF NOT EXISTS idx_favorite_event_id ON user_favorite_events (event_id);
CREATE INDEX IF NOT EXISTS idx_favorite_created_at ON user_favorite_events (created_at);

-- Comments
COMMENT ON TABLE user_favorite_events IS 'User favorite/bookmarked events';
COMMENT ON COLUMN user_favorite_events.user_id IS 'User who favorited the event';
COMMENT ON COLUMN user_favorite_events.event_id IS 'ID of the favorited event';
COMMENT ON COLUMN user_favorite_events.notifications_enabled IS 'Whether to send notifications about this event';
COMMENT ON COLUMN user_favorite_events.user_note IS 'Optional user note about why they favorited this event';

-- ===================================================================
-- Create user_blocked_users table
-- ===================================================================
-- Stores user blocking relationships for privacy/safety

CREATE TABLE IF NOT EXISTS user_blocked_users
(
    -- Primary Key
    id                   BIGSERIAL PRIMARY KEY,

    -- Foreign Keys (both to users table)
    blocking_user_id     BIGINT    NOT NULL,
    blocked_user_id      BIGINT    NOT NULL,

    -- Block Details
    block_reason         VARCHAR(50),
    block_reason_details VARCHAR(500),

    -- Audit Fields
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT fk_blocking_user FOREIGN KEY (blocking_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_blocked_user FOREIGN KEY (blocked_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_blocking_blocked_user UNIQUE (blocking_user_id, blocked_user_id),
    CONSTRAINT chk_not_self_block CHECK (blocking_user_id != blocked_user_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_blocking_user_id ON user_blocked_users (blocking_user_id);
CREATE INDEX IF NOT EXISTS idx_blocked_user_id ON user_blocked_users (blocked_user_id);
CREATE INDEX IF NOT EXISTS idx_block_reason ON user_blocked_users (block_reason);

-- Comments
COMMENT ON TABLE user_blocked_users IS 'User blocking relationships for privacy and safety';
COMMENT ON COLUMN user_blocked_users.blocking_user_id IS 'User who initiated the block';
COMMENT ON COLUMN user_blocked_users.blocked_user_id IS 'User who is blocked';
COMMENT ON COLUMN user_blocked_users.block_reason IS 'Reason category: spam, harassment, abuse, etc.';
COMMENT ON COLUMN user_blocked_users.block_reason_details IS 'Optional additional details';

-- ===================================================================
-- Create user_promo_codes table
-- ===================================================================
-- Stores user-specific promo code assignments and usage

CREATE TABLE IF NOT EXISTS user_promo_codes
(
    -- Primary Key
    id                 BIGSERIAL PRIMARY KEY,

    -- Foreign Key
    user_id            BIGINT         NOT NULL,

    -- Promo Code Details
    promo_code         VARCHAR(50)    NOT NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
    discount_type      VARCHAR(20)    NOT NULL,
    discount_value     NUMERIC(10, 2) NOT NULL,

    -- Usage Tracking
    max_uses           INTEGER,
    times_used         INTEGER        NOT NULL DEFAULT 0,
    expires_at         TIMESTAMP,
    used_in_booking_id BIGINT,

    -- Audit Fields
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT fk_promo_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_promo_code UNIQUE (user_id, promo_code),
    CONSTRAINT chk_promo_status CHECK (status IN ('AVAILABLE', 'USED', 'EXHAUSTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT chk_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT chk_discount_value_positive CHECK (discount_value > 0),
    CONSTRAINT chk_max_uses_positive CHECK (max_uses IS NULL OR max_uses > 0),
    CONSTRAINT chk_times_used_non_negative CHECK (times_used >= 0)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_user_promo_user_id ON user_promo_codes (user_id);
CREATE INDEX IF NOT EXISTS idx_user_promo_code ON user_promo_codes (promo_code);
CREATE INDEX IF NOT EXISTS idx_user_promo_status ON user_promo_codes (status);
CREATE INDEX IF NOT EXISTS idx_user_promo_expires ON user_promo_codes (expires_at) WHERE expires_at IS NOT NULL;

-- Comments
COMMENT ON TABLE user_promo_codes IS 'User-specific promo code assignments and usage tracking';
COMMENT ON COLUMN user_promo_codes.user_id IS 'User who owns/used this promo code';
COMMENT ON COLUMN user_promo_codes.promo_code IS 'Promo code string (e.g., SUMMER2025)';
COMMENT ON COLUMN user_promo_codes.status IS 'Status: AVAILABLE, USED, EXHAUSTED, EXPIRED, REVOKED';
COMMENT ON COLUMN user_promo_codes.discount_type IS 'Type: PERCENTAGE or FIXED_AMOUNT';
COMMENT ON COLUMN user_promo_codes.discount_value IS 'Discount value (percentage or fixed amount)';
COMMENT ON COLUMN user_promo_codes.max_uses IS 'Maximum uses allowed (NULL = unlimited)';
COMMENT ON COLUMN user_promo_codes.times_used IS 'Number of times used';
COMMENT ON COLUMN user_promo_codes.expires_at IS 'Expiration timestamp (NULL = no expiration)';
COMMENT ON COLUMN user_promo_codes.used_in_booking_id IS 'First booking where this code was used';

