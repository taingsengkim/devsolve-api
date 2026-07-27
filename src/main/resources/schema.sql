DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'membership_status_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.membership_status_enum AS ENUM (
            'active',
            'suspended',
            'removed'
        );
    END IF;
END
$$^^^

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'report_state_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.report_state_enum AS ENUM (
            'new',
            'triaging',
            'needs_more_info',
            'valid_confirmed',
            'resolved',
            'rejected',
            'duplicate'
        );
    END IF;
END
$$^^^

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'disclosure_status_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.disclosure_status_enum AS ENUM (
            'not_disclosed',
            'pending_disclosure',
            'disclosed'
        );
    END IF;
END
$$^^^

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'dispute_status_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.dispute_status_enum AS ENUM (
            'open',
            'under_review',
            'resolved',
            'dismissed'
        );
    END IF;
END
$$^^^

CREATE OR REPLACE FUNCTION public.reconcile_report_severity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.id IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM public.disputes d
           WHERE d.report_id = NEW.id
             AND d.status IN ('open', 'under_review')
       ) THEN
        NEW.severity := NULL;
    ELSIF NEW.triage_severity IS NULL THEN
        NEW.severity := NULL;
    ELSIF NEW.reported_severity = NEW.triage_severity THEN
        NEW.severity := NEW.triage_severity;
    ELSE
        NEW.severity := NULL;
    END IF;

    RETURN NEW;
END
$$^^^

CREATE OR REPLACE FUNCTION public.open_report_severity_dispute()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.triage_severity IS NOT NULL
       AND NEW.reported_severity <> NEW.triage_severity
       AND NOT EXISTS (
           SELECT 1
           FROM public.disputes d
           WHERE d.report_id = NEW.id
             AND d.status IN ('open', 'under_review')
       ) THEN
        INSERT INTO public.disputes (
            report_id,
            raised_by,
            reason,
            status
        )
        VALUES (
            NEW.id,
            NEW.reporter_id,
            'Automatically opened because the reported and triage severities differ',
            'open'
        );
    END IF;

    RETURN NEW;
END
$$^^^

DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL
       AND to_regclass('public.disputes') IS NOT NULL THEN
        EXECUTE 'DROP TRIGGER IF EXISTS trg_reconcile_report_severity ON public.reports';
        EXECUTE 'CREATE TRIGGER trg_reconcile_report_severity BEFORE INSERT OR UPDATE OF reported_severity, triage_severity ON public.reports FOR EACH ROW EXECUTE FUNCTION public.reconcile_report_severity()';

        EXECUTE 'DROP TRIGGER IF EXISTS trg_open_report_severity_dispute ON public.reports';
        EXECUTE 'CREATE TRIGGER trg_open_report_severity_dispute AFTER INSERT OR UPDATE OF reported_severity, triage_severity ON public.reports FOR EACH ROW EXECUTE FUNCTION public.open_report_severity_dispute()';
    END IF;
END
$$^^^

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'industry_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.industry_enum AS ENUM (
            'technology',
            'finance',
            'healthcare',
            'ecommerce',
            'government',
            'education',
            'other'
        );
    END IF;
END
$$^^^

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'engagement_type_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.engagement_type_enum AS ENUM (
            'bounty',
            'response'
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'program_state_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.program_state_enum AS ENUM (
            'draft',
            'active',
            'paused',
            'closed'
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'submission_state_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.submission_state_enum AS ENUM (
            'pending_review',
            'approved',
            'rejected'
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'visibility_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.visibility_enum AS ENUM (
            'public',
            'invite_only',
            'private'
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'severity_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.severity_enum AS ENUM (
            'none',
            'low',
            'medium',
            'high',
            'critical'
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'asset_type_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.asset_type_enum AS ENUM (
            'url',
            'wildcard',
            'ip_range',
            'mobile_app',
            'api',
            'source_code',
            'hardware',
            'other'
        );
    END IF;
END
$$^^^

ALTER TABLE IF EXISTS public.organizations
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'pending'^^^

ALTER TABLE IF EXISTS public.organizations
    ADD COLUMN IF NOT EXISTS owner_job_title VARCHAR(100) NOT NULL DEFAULT 'Unknown'^^^

ALTER TABLE IF EXISTS public.organizations
    ADD COLUMN IF NOT EXISTS company_size VARCHAR(20) NOT NULL DEFAULT 'Unknown'^^^

ALTER TABLE IF EXISTS public.organizations
    ADD COLUMN IF NOT EXISTS country VARCHAR(100) NOT NULL DEFAULT 'Unknown'^^^

ALTER TABLE IF EXISTS public.organizations
    ADD COLUMN IF NOT EXISTS joining_reason VARCHAR(1000) NOT NULL DEFAULT ''^^^

ALTER TABLE IF EXISTS public.organization_members
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'member'^^^

ALTER TABLE IF EXISTS public.organization_members
    ADD COLUMN IF NOT EXISTS invitation_token VARCHAR(36)^^^

DO $$
BEGIN
    IF to_regclass('public.organizations') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'ck_organizations_status'
       ) THEN
        ALTER TABLE public.organizations
            ADD CONSTRAINT ck_organizations_status
            CHECK (status IN ('pending', 'active', 'rejected'));
    END IF;

    IF to_regclass('public.organization_members') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'ck_organization_members_role'
       ) THEN
        ALTER TABLE public.organization_members
            ADD CONSTRAINT ck_organization_members_role
            CHECK (role IN ('manager', 'member', 'viewer'));
    END IF;

    IF to_regclass('public.organization_members') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_organization_members_invitation_token
            ON public.organization_members (invitation_token)
            WHERE invitation_token IS NOT NULL;
    END IF;

    IF to_regclass('public.organizations') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_organizations_active_owner
            ON public.organizations (owner_id)
            WHERE deleted_at IS NULL;

        CREATE INDEX IF NOT EXISTS idx_organizations_public_status
            ON public.organizations (status)
            WHERE deleted_at IS NULL;
    END IF;
END
$$^^^
