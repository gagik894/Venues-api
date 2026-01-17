-- V16__Enhance_audit_schema.sql
-- Purpose: Add structured columns for accurate traceability (government standards).
-- Adds 'changes' for difference tracking, 'severity' and 'category' for filtering, and 'description' for readability.

DO
$$
    BEGIN
        -- Add 'changes' column for recording old/new value pairs (JSONB for flexibility)
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.columns
                       WHERE table_name = 'audit_events'
                         AND column_name = 'changes') THEN
            ALTER TABLE public.audit_events
                ADD COLUMN changes JSONB NULL;
        END IF;

        -- Add 'severity' for risk assessment (e.g., INFO, CRITICAL)
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.columns
                       WHERE table_name = 'audit_events'
                         AND column_name = 'severity') THEN
            ALTER TABLE public.audit_events
                ADD COLUMN severity VARCHAR(32) NOT NULL DEFAULT 'INFO';
        END IF;

        -- Add 'category' for domain grouping (e.g., SECURITY, BUSINESS)
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.columns
                       WHERE table_name = 'audit_events'
                         AND column_name = 'category') THEN
            ALTER TABLE public.audit_events
                ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'SYSTEM';
        END IF;

        -- Add 'description' for a human-readable summary of the event
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.columns
                       WHERE table_name = 'audit_events'
                         AND column_name = 'description') THEN
            ALTER TABLE public.audit_events
                ADD COLUMN description TEXT NULL;
        END IF;

        -- Create indices for new queryable fields
        CREATE INDEX IF NOT EXISTS idx_audit_events_severity ON public.audit_events (severity);
        CREATE INDEX IF NOT EXISTS idx_audit_events_category ON public.audit_events (category);

    END
$$;
