-- ================================================================
-- Venues API - Update Status Constraints
-- Version: 14
-- Description: Add RESERVED and SOLD status values to check constraints
-- ================================================================

-- Drop old constraints
ALTER TABLE session_seat_configs
    DROP CONSTRAINT IF EXISTS chk_session_seat_status;

ALTER TABLE session_level_configs
    DROP CONSTRAINT IF EXISTS chk_session_level_status;

-- Add new constraints with RESERVED and SOLD
ALTER TABLE session_seat_configs
    ADD CONSTRAINT chk_session_seat_status
        CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD', 'CLOSED', 'BLOCKED'));

-- For GA levels, status stays AVAILABLE (we use capacity tracking)
-- But we still add RESERVED and SOLD for consistency
ALTER TABLE session_level_configs
    ADD CONSTRAINT chk_session_level_status
        CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD', 'CLOSED'));

