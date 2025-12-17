-- Add preferred_language column to users, staff, and guests for email localization
-- Supported languages: en (English), hy (Armenian), ru (Russian)
-- Default: en

-- Users table
ALTER TABLE users
    ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'en';

CREATE INDEX idx_user_language ON users (preferred_language);

-- Staff identities table
ALTER TABLE staff_identities
    ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'en';

CREATE INDEX idx_staff_language ON staff_identities (preferred_language);

-- Guests table
ALTER TABLE guests
    ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'en';

CREATE INDEX idx_guest_language ON guests (preferred_language);
