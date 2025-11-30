-- Remove redundant lastActivityAt column from carts table
-- This field was never used (touch() method never called)
-- and always had the same value as expiresAt
ALTER TABLE carts
    DROP COLUMN last_activity_at;
