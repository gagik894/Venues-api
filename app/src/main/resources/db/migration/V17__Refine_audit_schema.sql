-- V17__Refine_audit_schema.sql
-- Purpose: Refine audit table to be less generic and more business-focused.
-- Replaces generic 'actor' fields with explicit 'staff_id'/'user_id'.
-- Removes technical HTTP/tracing noise to focus on business value.

DO
$$
    BEGIN
        -- Add explicit identity columns
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.columns
                       WHERE table_name = 'audit_events'
                         AND column_name = 'staff_id') THEN
            ALTER TABLE public.audit_events
                ADD COLUMN staff_id UUID NULL;
            CREATE INDEX idx_audit_events_staff_id ON public.audit_events (staff_id);
        END IF;

        IF NOT EXISTS (SELECT 1
                       FROM information_schema.columns
                       WHERE table_name = 'audit_events'
                         AND column_name = 'user_id') THEN
            ALTER TABLE public.audit_events
                ADD COLUMN user_id UUID NULL;
            CREATE INDEX idx_audit_events_user_id ON public.audit_events (user_id);
        END IF;

        -- Migrate existing data (best effort mapping)
        -- Map actor_type='STAFF' to staff_id
        UPDATE public.audit_events SET staff_id = actor_id WHERE actor_type = 'STAFF' AND staff_id IS NULL;
        -- Map actor_type='USER' to user_id
        UPDATE public.audit_events SET user_id = actor_id WHERE actor_type = 'USER' AND user_id IS NULL;

        -- Drop generic/technical columns that are no longer needed
        ALTER TABLE public.audit_events
            DROP COLUMN IF EXISTS actor_type,
            DROP COLUMN IF EXISTS actor_id,
            DROP COLUMN IF EXISTS http_method,
            DROP COLUMN IF EXISTS http_path,
            DROP COLUMN IF EXISTS http_status,
            DROP COLUMN IF EXISTS request_id,
            DROP COLUMN IF EXISTS correlation_id;

    END
$$;
