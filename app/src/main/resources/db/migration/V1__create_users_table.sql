-- Venues API - Government-Sponsored Cultural Venues Portal
-- Copyright (c) 2025 Government Cultural Department
--
-- Initial database schema for User module

-- ===================================================================
-- Users Table
-- ===================================================================
-- Stores registered user accounts with authentication and profile information
-- ===================================================================

CREATE TABLE IF NOT EXISTS users
(
    -- Primary Key
    id
                          BIGSERIAL
        PRIMARY
            KEY,

    -- Authentication
    email
                          VARCHAR(255) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,

    -- Profile Information
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    phone_number          VARCHAR(20),

    -- Authorization
    role                  VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING_VERIFICATION',

    -- Security Tracking
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP,
    last_login_at         TIMESTAMP,
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Audit Fields
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_role CHECK
        (
        role
            IN
        (
         'USER',
         'ADMIN'
            )),
    CONSTRAINT chk_status CHECK
        (
        status
            IN
        (
         'ACTIVE',
         'PENDING_VERIFICATION',
         'SUSPENDED',
         'DELETED'
            )),
    CONSTRAINT chk_email_format CHECK
        (
        email
            ~
            *
        '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
        )
);

-- ===================================================================
-- Indexes for Performance
-- ===================================================================

-- Primary lookup index (login)
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_email ON users (email);

-- Status filtering (admin queries)
CREATE INDEX IF NOT EXISTS idx_user_status ON users (status);

-- Role filtering (admin queries)
CREATE INDEX IF NOT EXISTS idx_user_role ON users (role);

-- Email verification status
CREATE INDEX IF NOT EXISTS idx_user_email_verified ON users (email_verified);

-- Audit trail (created date range queries)
CREATE INDEX IF NOT EXISTS idx_user_created_at ON users (created_at);

-- ===================================================================
-- Comments for Documentation
-- ===================================================================

COMMENT
    ON TABLE users IS 'Registered users of the Venues API system';
COMMENT
    ON COLUMN users.id IS 'Primary key, auto-generated';
COMMENT
    ON COLUMN users.email IS 'User email address, used for login (unique)';
COMMENT
    ON COLUMN users.password_hash IS 'BCrypt hashed password (never store plain text)';
COMMENT
    ON COLUMN users.first_name IS 'User first name';
COMMENT
    ON COLUMN users.last_name IS 'User last name';
COMMENT
    ON COLUMN users.phone_number IS 'User phone number (optional)';
COMMENT
    ON COLUMN users.role IS 'User role: USER or ADMIN';
COMMENT
    ON COLUMN users.status IS 'Account status: ACTIVE, PENDING_VERIFICATION, SUSPENDED, DELETED';
COMMENT
    ON COLUMN users.failed_login_attempts IS 'Number of consecutive failed login attempts';
COMMENT
    ON COLUMN users.locked_until IS 'Account locked until this timestamp (null if not locked)';
COMMENT
    ON COLUMN users.last_login_at IS 'Timestamp of last successful login';
COMMENT
    ON COLUMN users.email_verified IS 'Whether email address has been verified';
COMMENT
    ON COLUMN users.created_at IS 'Account creation timestamp';
COMMENT
    ON COLUMN users.last_modified_at IS 'Last modification timestamp';

-- ===================================================================
-- Seed Data (Optional - for development/testing)
-- ===================================================================

-- Admin user (password: Admin@123)
-- BCrypt hash with strength 12
INSERT INTO users (email, password_hash, first_name, last_name, role, status, email_verified)
VALUES ('admin@venues.gov',
        '$2a$12$yZpZqDzZIbwV02XJpmF5s.LKM1Q1O4ZMOJijEePeZQK7LZMaU9Loe',
        'System',
        'Administrator',
        'ADMIN',
        'ACTIVE',
        true)
ON CONFLICT (email) DO NOTHING;

-- Test user (password: User@123)
INSERT INTO users (email, password_hash, first_name, last_name, role, status, email_verified)
VALUES ('user@test.com',
        '$2a$12$ESuNRmdZCr6T0ZJ8qvyAGOaqxiWfeySADZlxvikCAtUQW13tt3PcO',
        'Test',
        'User',
        'USER',
        'ACTIVE',
        true)
ON CONFLICT (email) DO NOTHING;

-- ===================================================================
-- Trigger for Automatic last_modified_at Update
-- ===================================================================

CREATE
    OR REPLACE FUNCTION update_last_modified_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.last_modified_at
        = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$
    LANGUAGE plpgsql;

CREATE TRIGGER trigger_users_last_modified_at
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION update_last_modified_at();

-- ===================================================================
-- End of Migration
-- ===================================================================

