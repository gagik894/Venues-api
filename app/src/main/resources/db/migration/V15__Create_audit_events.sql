-- V15__Create_audit_events.sql
-- Purpose: Append-only audit log for staff-initiated actions (government-grade traceability).
-- Safe to rerun: guarded with IF NOT EXISTS and constraints are idempotent.
-- Verification: SELECT count(*) FROM audit_events;

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.tables
                       WHERE table_schema = 'public'
                         AND table_name = 'audit_events') THEN
            CREATE TABLE public.audit_events
            (
                id               UUID PRIMARY KEY,
                occurred_at      TIMESTAMPTZ   NOT NULL,
                actor_type       VARCHAR(32)   NOT NULL,
                actor_id         UUID          NULL,
                action           VARCHAR(128)  NOT NULL,
                outcome          VARCHAR(32)   NOT NULL,
                subject_type     VARCHAR(128)  NULL,
                subject_id       VARCHAR(128)  NULL,
                venue_id         UUID          NULL,
                organization_id  UUID          NULL,
                http_method      VARCHAR(16)   NULL,
                http_path        VARCHAR(2048) NULL,
                http_status      INTEGER       NULL,
                request_id       VARCHAR(128)  NULL,
                correlation_id   VARCHAR(128)  NULL,
                client_ip        VARCHAR(64)   NULL,
                user_agent       VARCHAR(512)  NULL,
                metadata         JSONB         NOT NULL DEFAULT '{}'::jsonb,
                created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
                last_modified_at TIMESTAMPTZ   NOT NULL DEFAULT now()
            );

            CREATE INDEX idx_audit_events_occurred_at ON public.audit_events (occurred_at);
            CREATE INDEX idx_audit_events_actor_id ON public.audit_events (actor_id);
            CREATE INDEX idx_audit_events_action ON public.audit_events (action);
            CREATE INDEX idx_audit_events_subject ON public.audit_events (subject_type, subject_id);
            CREATE INDEX idx_audit_events_venue_id ON public.audit_events (venue_id);
        END IF;
    END
$$;

-- Enforce append-only semantics: revoke UPDATE/DELETE for application role at deployment time if possible.
-- Verification query (manual):
--   SELECT count(*) FROM audit_events;