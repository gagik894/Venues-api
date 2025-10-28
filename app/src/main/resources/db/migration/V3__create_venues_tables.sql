-- Migration V3: Create Venues Tables
-- Description: Creates all tables for the venue module including venues, schedules, translations,
--              photos, reviews, followers, and promo codes.
-- Author: Venues API Team
-- Date: 2025-10-28

-- ===========================================
-- Create Venues Table
-- ===========================================

CREATE TABLE venues
(
    id                                  BIGSERIAL PRIMARY KEY,

    -- Basic Information
    name                                VARCHAR(255)        NOT NULL,
    description                         TEXT,
    image_url                           VARCHAR(500),

    -- Location
    address                             VARCHAR(500)        NOT NULL,
    city                                VARCHAR(100),
    latitude                            DOUBLE PRECISION,
    longitude                           DOUBLE PRECISION,

    -- Contact
    email                               VARCHAR(255) UNIQUE NOT NULL,
    phone_number                        VARCHAR(50),
    website                             VARCHAR(500),
    custom_domain                       VARCHAR(255),

    -- Classification
    category                            VARCHAR(50),

    -- Operating Hours
    is_always_open                      BOOLEAN             NOT NULL DEFAULT FALSE,

    -- Verification & Status
    verified                            BOOLEAN             NOT NULL DEFAULT FALSE,
    official                            BOOLEAN             NOT NULL DEFAULT FALSE,
    verification_document_url           VARCHAR(500),
    status                              VARCHAR(20)         NOT NULL DEFAULT 'PENDING_APPROVAL',

    -- Authentication
    password_hash                       VARCHAR(255)        NOT NULL,
    email_password                      VARCHAR(255),
    secret_email                        VARCHAR(255),

    -- Payment Integration (encrypted at rest recommended)
    telcel_postpone_bill_issuer         VARCHAR(255),
    idram_rec_account                   VARCHAR(255),
    idram_secret_key                    VARCHAR(255),
    telcel_store_key                    VARCHAR(255),
    arca_username                       VARCHAR(255),
    arca_password                       VARCHAR(255),
    converse_merchant_id                VARCHAR(255),
    converse_secret_key                 VARCHAR(255),

    -- Notifications
    fcm_token                           VARCHAR(500),

    -- Security & Auditing
    failed_login_attempts               INTEGER             NOT NULL DEFAULT 0,
    account_locked_until                TIMESTAMP,
    last_login_at                       TIMESTAMP,
    email_verified                      BOOLEAN             NOT NULL DEFAULT FALSE,
    email_verification_token            VARCHAR(255),
    email_verification_token_expires_at TIMESTAMP,

    -- Audit Fields
    created_at                          TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at                    TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_venue_status CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'INACTIVE', 'DELETED'))
);

-- Indexes for venues
CREATE INDEX idx_venue_email ON venues (email);
CREATE INDEX idx_venue_city ON venues (city);
CREATE INDEX idx_venue_category ON venues (category);
CREATE INDEX idx_venue_verified ON venues (verified);
CREATE INDEX idx_venue_status ON venues (status);

-- ===========================================
-- Create Venue Schedules Table
-- ===========================================

CREATE TABLE venue_schedules
(
    id          BIGSERIAL PRIMARY KEY,
    venue_id    BIGINT      NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL,
    open_time   TIME,
    close_time  TIME,
    is_closed   BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_day_of_week CHECK (day_of_week IN
                                      ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT uk_venue_schedule_venue_day UNIQUE (venue_id, day_of_week)
);

CREATE INDEX idx_venue_schedule_venue_id ON venue_schedules (venue_id);
CREATE INDEX idx_venue_schedule_day ON venue_schedules (day_of_week);

-- ===========================================
-- Create Venue Translations Table
-- ===========================================

CREATE TABLE venue_translations
(
    id               BIGSERIAL PRIMARY KEY,
    venue_id         BIGINT       NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    language         VARCHAR(10)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_venue_translation_venue_language UNIQUE (venue_id, language)
);

CREATE INDEX idx_venue_translation_venue_id ON venue_translations (venue_id);
CREATE INDEX idx_venue_translation_language ON venue_translations (language);

-- ===========================================
-- Create Venue Photos Table
-- ===========================================

CREATE TABLE venue_photos
(
    id            BIGSERIAL PRIMARY KEY,
    venue_id      BIGINT       NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    user_id       BIGINT       NOT NULL, -- References users(id) from user module
    url           VARCHAR(500) NOT NULL,
    caption       VARCHAR(500),
    display_order INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_venue_photo_venue_id ON venue_photos (venue_id);
CREATE INDEX idx_venue_photo_user_id ON venue_photos (user_id);

-- ===========================================
-- Create Venue Reviews Table
-- ===========================================

CREATE TABLE venue_reviews
(
    id               BIGSERIAL PRIMARY KEY,
    venue_id         BIGINT    NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    user_id          BIGINT    NOT NULL, -- References users(id) from user module
    rating           INTEGER   NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment          TEXT,
    is_moderated     BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_venue_review_user_venue UNIQUE (venue_id, user_id)
);

CREATE INDEX idx_venue_review_venue_id ON venue_reviews (venue_id);
CREATE INDEX idx_venue_review_user_id ON venue_reviews (user_id);
CREATE INDEX idx_venue_review_rating ON venue_reviews (rating);

-- ===========================================
-- Create Venue Followers Table
-- ===========================================

CREATE TABLE venue_followers
(
    id                    BIGSERIAL PRIMARY KEY,
    venue_id              BIGINT    NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    user_id               BIGINT    NOT NULL, -- References users(id) from user module
    notifications_enabled BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_venue_follower_user_venue UNIQUE (venue_id, user_id)
);

CREATE INDEX idx_venue_follower_venue_id ON venue_followers (venue_id);
CREATE INDEX idx_venue_follower_user_id ON venue_followers (user_id);

-- ===========================================
-- Create Venue Promo Codes Table
-- ===========================================

CREATE TABLE venue_promo_codes
(
    id                  BIGSERIAL PRIMARY KEY,
    venue_id            BIGINT         NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    code                VARCHAR(50)    NOT NULL,
    description         VARCHAR(255),
    discount_type       VARCHAR(20)    NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    discount_value      DECIMAL(10, 2) NOT NULL CHECK (discount_value >= 0),
    min_order_amount    DECIMAL(10, 2) CHECK (min_order_amount >= 0),
    max_discount_amount DECIMAL(10, 2) CHECK (max_discount_amount >= 0),
    max_usage_count     INTEGER CHECK (max_usage_count >= 1),
    current_usage_count INTEGER        NOT NULL DEFAULT 0,
    expires_at          TIMESTAMP,
    is_active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_venue_promo_code_venue_code UNIQUE (venue_id, code)
);

CREATE INDEX idx_venue_promo_code_venue_id ON venue_promo_codes (venue_id);
CREATE INDEX idx_venue_promo_code_code ON venue_promo_codes (code);
CREATE INDEX idx_venue_promo_code_active ON venue_promo_codes (is_active);

-- ===========================================
-- Comments for Documentation
-- ===========================================

COMMENT
    ON TABLE venues IS 'Cultural venues (theaters, opera houses, museums, etc.) that host events';
COMMENT
    ON TABLE venue_schedules IS 'Operating hours for venues by day of week';
COMMENT
    ON TABLE venue_translations IS 'Multi-language translations for venue name and description';
COMMENT
    ON TABLE venue_photos IS 'Photos uploaded for venues';
COMMENT
    ON TABLE venue_reviews IS 'User reviews and ratings for venues';
COMMENT
    ON TABLE venue_followers IS 'Users following venues for updates';
COMMENT
    ON TABLE venue_promo_codes IS 'Promotional discount codes offered by venues';


