-- =============================================================================
-- V18: Government-Grade Staff Audit Log
-- =============================================================================
-- Purpose: Create a purpose-built audit table for tracking staff actions with
-- full accountability: who did what, when, what changed, and the outcome.
-- Replaces the broken audit_events table which logged low-value HTTP noise.
--
-- Retention Policy: 3 years. Schedule a job to run monthly:
--   DELETE FROM staff_audit_log WHERE occurred_at < NOW() - INTERVAL '3 years';
-- =============================================================================

-- Drop old audit_events table and its indexes (contained low-value HTTP noise)
DROP INDEX IF EXISTS idx_audit_events_occurred_at;
DROP INDEX IF EXISTS idx_audit_events_actor;
DROP INDEX IF EXISTS idx_audit_events_venue_id;
DROP INDEX IF EXISTS idx_audit_events_action;
DROP INDEX IF EXISTS idx_audit_events_subject;
DROP TABLE IF EXISTS audit_events;

-- =============================================================================
-- New Staff Audit Log Table
-- =============================================================================
CREATE TABLE staff_audit_log
(
    -- Primary key
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),

    -- When the action occurred
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Who performed the action (staff member) - required for staff audits
    staff_id        UUID         NOT NULL,

    -- Optional: platform API client if action was via platform integration
    platform_id     UUID         NULL,

    -- Context: which venue/organization was affected
    venue_id        UUID         NULL,
    organization_id UUID         NULL,

    -- What action was performed (e.g., EVENT_CREATE, TICKET_SALE, STAFF_LOGIN)
    action          VARCHAR(64)  NOT NULL,

    -- Categorization for filtering and reporting
    category        VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM',
    -- SECURITY: login, logout, password changes, permission changes
    -- SALES: ticket sales, refunds, cart operations
    -- EVENT_MANAGEMENT: event create/update/publish/cancel
    -- CONFIGURATION: venue settings, seating charts, pricing templates
    -- PLATFORM: platform API operations
    -- MEDIA: file uploads/deletions

    -- Severity for alerting and compliance
    severity        VARCHAR(16)  NOT NULL DEFAULT 'INFO',
    -- INFO: routine operations
    -- IMPORTANT: significant changes (event publish, pricing update)
    -- CRITICAL: security events (login failures, permission changes)

    -- What entity was affected
    subject_type    VARCHAR(64)  NULL, -- e.g., 'event', 'venue', 'ticket', 'booking'
    subject_id      VARCHAR(128) NULL, -- UUID or other identifier of the subject

    -- Human-readable description of what happened
    -- e.g., "Updated event 'Summer Concert' status from DRAFT to PUBLISHED"
    description     TEXT         NULL,

    -- For UPDATE actions: JSON object with field changes
    -- Format: {"fieldName": {"old": "previous value", "new": "new value"}, ...}
    changes         JSONB        NULL,

    -- Outcome of the action
    outcome         VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS',
    -- SUCCESS: action completed successfully
    -- FAILURE: action failed (see failure_reason)

    -- If outcome is FAILURE, why did it fail?
    failure_reason  TEXT         NULL,

    -- Minimal HTTP context for forensics
    client_ip       VARCHAR(64)  NULL,
    user_agent      VARCHAR(512) NULL,

    -- Flexible metadata for action-specific data
    -- e.g., {"ticketCount": 5, "totalAmount": 150.00, "paymentMethod": "CASH"}
    metadata        JSONB        NOT NULL DEFAULT '{}'::jsonb,

    -- Standard timestamps
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT chk_audit_severity CHECK (severity IN ('INFO', 'IMPORTANT', 'CRITICAL')),
    CONSTRAINT chk_audit_category CHECK (category IN (
                                                      'SECURITY', 'SALES', 'EVENT_MANAGEMENT', 'CONFIGURATION',
                                                      'PLATFORM', 'MEDIA', 'SYSTEM'
        ))
);

-- Add table comment
COMMENT ON TABLE staff_audit_log IS 'Government-grade audit log tracking all staff actions with full accountability. Retention: 3 years.';

-- Column comments for documentation
COMMENT ON COLUMN staff_audit_log.staff_id IS 'UUID of the staff member who performed the action. Required for all entries.';
COMMENT ON COLUMN staff_audit_log.platform_id IS 'UUID of platform API client if action was via platform integration.';
COMMENT ON COLUMN staff_audit_log.action IS 'Action code matching AuditAction enum (e.g., EVENT_CREATE, TICKET_SALE).';
COMMENT ON COLUMN staff_audit_log.category IS 'Category for filtering: SECURITY, SALES, EVENT_MANAGEMENT, CONFIGURATION, PLATFORM, MEDIA.';
COMMENT ON COLUMN staff_audit_log.severity IS 'Severity for alerting: INFO (routine), IMPORTANT (significant), CRITICAL (security).';
COMMENT ON COLUMN staff_audit_log.description IS 'Human-readable description of the action for audit reports.';
COMMENT ON COLUMN staff_audit_log.changes IS 'JSON object with old/new values for UPDATE actions: {"field": {"old": x, "new": y}}.';
COMMENT ON COLUMN staff_audit_log.metadata IS 'Action-specific data: ticket counts, amounts, IDs, etc.';

-- =============================================================================
-- Indexes for Common Query Patterns
-- =============================================================================

-- Find all actions by a specific staff member (sorted by time)
CREATE INDEX idx_staff_audit_staff_time ON staff_audit_log (staff_id, occurred_at DESC);

-- Find all actions for a specific venue (sorted by time)
CREATE INDEX idx_staff_audit_venue_time ON staff_audit_log (venue_id, occurred_at DESC) WHERE venue_id IS NOT NULL;

-- Find all actions of a specific type
CREATE INDEX idx_staff_audit_action ON staff_audit_log (action);

-- Filter by category and time (for compliance reports)
CREATE INDEX idx_staff_audit_category_time ON staff_audit_log (category, occurred_at DESC);

-- Find security events quickly (login failures, permission changes)
CREATE INDEX idx_staff_audit_security ON staff_audit_log (occurred_at DESC)
    WHERE category = 'SECURITY' AND severity = 'CRITICAL';

-- Find failures for troubleshooting
CREATE INDEX idx_staff_audit_failures ON staff_audit_log (occurred_at DESC)
    WHERE outcome = 'FAILURE';

-- =============================================================================
-- Verification Query (run after migration)
-- =============================================================================
-- SELECT 
--     (SELECT COUNT(*) FROM staff_audit_log) as new_table_count,
--     (SELECT COUNT(*) FROM audit_events_deprecated) as old_table_count,
--     (SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'staff_audit_log') as index_count;
-- Expected: new_table_count = 0, old_table_count = (preserved data), index_count = 6
