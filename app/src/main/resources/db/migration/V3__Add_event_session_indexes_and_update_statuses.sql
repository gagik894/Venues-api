-- 1. Add new columns
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS first_session_start TIMESTAMP WITH TIME ZONE;
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS last_session_end TIMESTAMP WITH TIME ZONE;

-- 2. DROP OLD CONSTRAINTS
-- We must remove the rules enforcing 'UPCOMING', 'PAST', etc. before we can change them.
ALTER TABLE events
    DROP CONSTRAINT IF EXISTS events_status_check;
ALTER TABLE event_sessions
    DROP CONSTRAINT IF EXISTS event_sessions_check;
-- Note: Check your DB for the exact name of the session constraint if the line above fails.
-- It is likely 'event_sessions_status_check' based on standard naming conventions.
ALTER TABLE event_sessions
    DROP CONSTRAINT IF EXISTS event_sessions_status_check;


-- 3. Update Event Data (Now allowed because constraint is gone)
UPDATE events
SET status = 'PUBLISHED'
WHERE status = 'UPCOMING';
UPDATE events
SET status = 'PUBLISHED'
WHERE status = 'ONGOING';
UPDATE events
SET status = 'SUSPENDED'
WHERE status = 'PAUSED';
UPDATE events
SET status = 'SUSPENDED'
WHERE status = 'MAINTENANCE';
UPDATE events
SET status = 'SUSPENDED'
WHERE status = 'CANCELLED';
UPDATE events
SET status = 'ARCHIVED'
WHERE status = 'PAST';
-- Ensure any DRAFT remains DRAFT (No change needed usually)

-- 4. Update Session Data
UPDATE event_sessions
SET status = 'ON_SALE'
WHERE status = 'UPCOMING';
UPDATE event_sessions
SET status = 'SALES_CLOSED'
WHERE status = 'PAST';
UPDATE event_sessions
SET status = 'SALES_CLOSED'
WHERE status = 'ONGOING';

-- 5. ADD NEW CONSTRAINTS
-- Now that the data is clean, we enforce the new Enums.

ALTER TABLE events
    ADD CONSTRAINT events_status_check
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUSPENDED', 'ARCHIVED', 'DELETED'));

ALTER TABLE event_sessions
    ADD CONSTRAINT event_sessions_status_check
        CHECK (status IN ('SCHEDULED', 'ON_SALE', 'PAUSED', 'SOLD_OUT', 'SALES_CLOSED', 'CANCELLED'));


-- 6. Backfill dates
WITH session_stats AS (SELECT event_id,
                              MIN(start_time) as min_start,
                              MAX(end_time)   as max_end
                       FROM event_sessions
                       GROUP BY event_id)
UPDATE events e
SET first_session_start = s.min_start,
    last_session_end    = s.max_end
FROM session_stats s
WHERE e.id = s.event_id;

-- 7. Create Indexes
CREATE INDEX idx_event_first_session_start ON events (first_session_start);
CREATE INDEX idx_event_last_session_end ON events (last_session_end);

-- Create the "Money Index" for the homepage we discussed
CREATE INDEX idx_events_home_feed ON events (status, first_session_start, last_session_end);