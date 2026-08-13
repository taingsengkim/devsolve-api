-- Values must match the @EnumeratedValue strings on the Java enums, and
-- every type here has to exist before Hibernate generates the tables that
-- reference it by columnDefinition.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'category_scope_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.category_scope_enum AS ENUM (
            'problem',
            'showcase'
        );
    END IF;
END
$$^^^

-- Problem context and solution revisioning. These statements are guarded so
-- they upgrade an existing Hibernate-managed database; on a fresh database
-- Hibernate creates the same structures from the entity mappings.
DO $$
BEGIN
    IF to_regclass('public.problems') IS NOT NULL THEN
        ALTER TABLE public.problems
            ADD COLUMN IF NOT EXISTS problem_type VARCHAR(30);
    END IF;
END
$$^^^

DO $$
BEGIN
    IF to_regclass('public.problems') IS NOT NULL THEN
        UPDATE public.problems
        SET problem_type = 'GENERAL'
        WHERE problem_type IS NULL;

        ALTER TABLE public.problems
            ALTER COLUMN problem_type SET DEFAULT 'GENERAL';
        ALTER TABLE public.problems
            ALTER COLUMN problem_type SET NOT NULL;
        ALTER TABLE public.problems
            ADD COLUMN IF NOT EXISTS expected_behavior TEXT;
        ALTER TABLE public.problems
            ADD COLUMN IF NOT EXISTS actual_behavior TEXT;
        ALTER TABLE public.problems
            ADD COLUMN IF NOT EXISTS attempts_tried TEXT;
        ALTER TABLE public.problems
            ADD COLUMN IF NOT EXISTS error_message TEXT;
        ALTER TABLE public.problems
            ADD COLUMN IF NOT EXISTS severity VARCHAR(20);
        ALTER TABLE public.problems
            ADD COLUMN IF NOT EXISTS repository_url VARCHAR(1000);
        CREATE TABLE IF NOT EXISTS public.problem_reproduction_steps (
            problem_id UUID NOT NULL REFERENCES public.problems (id),
            display_order INTEGER NOT NULL,
            instruction VARCHAR(1000) NOT NULL,
            PRIMARY KEY (problem_id, display_order)
        );

        CREATE TABLE IF NOT EXISTS public.problem_environments (
            problem_id UUID NOT NULL REFERENCES public.problems (id),
            display_order INTEGER NOT NULL,
            technology VARCHAR(100) NOT NULL,
            version VARCHAR(50),
            PRIMARY KEY (problem_id, display_order)
        );
    END IF;
END
$$^^^

DO $$
BEGIN
    IF to_regclass('public.solutions') IS NOT NULL THEN
        -- Keep nullable compatibility columns so this idempotent backfill can
        -- also run after a database was first created from the new entities.
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS description TEXT;
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS video_url VARCHAR(500);
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS diagram_url VARCHAR(500);
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS review_status VARCHAR(20);
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS reviewed_by UUID;
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP(6);
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS current_published_revision_id UUID;
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS latest_revision_id UUID;
        ALTER TABLE public.solutions
            ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

        -- Legacy content remains only as migration input. New writes use
        -- solution_revisions, so this old column can no longer be NOT NULL.
        ALTER TABLE public.solutions
            ALTER COLUMN description DROP NOT NULL;

        CREATE TABLE IF NOT EXISTS public.solution_revisions (
            id UUID PRIMARY KEY,
            solution_id UUID NOT NULL
                REFERENCES public.solutions (id),
            revision_number INTEGER NOT NULL,
            summary VARCHAR(250) NOT NULL,
            body_markdown TEXT NOT NULL,
            approach_type VARCHAR(20) NOT NULL,
            tradeoffs TEXT,
            moderation_status VARCHAR(20) NOT NULL,
            rejection_reason TEXT,
            reviewed_by UUID,
            reviewed_at TIMESTAMP(6),
            created_at TIMESTAMP(6) NOT NULL,
            updated_at TIMESTAMP(6) NOT NULL,
            CONSTRAINT uq_solution_revisions_number
                UNIQUE (solution_id, revision_number)
        );

        CREATE TABLE IF NOT EXISTS public.solution_revision_verification_steps (
            solution_revision_id UUID NOT NULL
                REFERENCES public.solution_revisions (id),
            display_order INTEGER NOT NULL,
            instruction VARCHAR(1000) NOT NULL,
            expected_result VARCHAR(1000) NOT NULL,
            PRIMARY KEY (solution_revision_id, display_order)
        );

        CREATE TABLE IF NOT EXISTS public.solution_revision_tested_with (
            solution_revision_id UUID NOT NULL
                REFERENCES public.solution_revisions (id),
            display_order INTEGER NOT NULL,
            technology VARCHAR(100) NOT NULL,
            version VARCHAR(50),
            PRIMARY KEY (solution_revision_id, display_order)
        );

        CREATE TABLE IF NOT EXISTS public.solution_resources (
            id UUID PRIMARY KEY,
            solution_revision_id UUID NOT NULL
                REFERENCES public.solution_revisions (id),
            type VARCHAR(30) NOT NULL,
            label VARCHAR(150) NOT NULL,
            url VARCHAR(1000) NOT NULL,
            display_order INTEGER NOT NULL,
            created_at TIMESTAMP(6) NOT NULL
        );

        CREATE TABLE IF NOT EXISTS public.solution_attachments (
            id UUID PRIMARY KEY,
            solution_revision_id UUID NOT NULL
                REFERENCES public.solution_revisions (id),
            file_name VARCHAR(255) NOT NULL,
            storage_key VARCHAR(500) NOT NULL,
            mime_type VARCHAR(100) NOT NULL,
            file_size BIGINT NOT NULL,
            created_at TIMESTAMP(6) NOT NULL
        );

        CREATE TABLE IF NOT EXISTS public.problem_accepted_solutions (
            id UUID PRIMARY KEY,
            problem_id UUID NOT NULL
                REFERENCES public.problems (id),
            solution_id UUID NOT NULL,
            accepted_by UUID NOT NULL,
            accepted_at TIMESTAMP(6) NOT NULL,
            CONSTRAINT uq_problem_accepted_solutions_problem_solution
                UNIQUE (problem_id, solution_id)
        );

        INSERT INTO public.solution_revisions (
            id,
            solution_id,
            revision_number,
            summary,
            body_markdown,
            approach_type,
            moderation_status,
            rejection_reason,
            reviewed_by,
            reviewed_at,
            created_at,
            updated_at
        )
        SELECT
            (md5(solution.id::text || ':revision:1'))::uuid,
            solution.id,
            1,
            LEFT(
                CASE
                    WHEN solution.description IS NULL
                         OR btrim(solution.description) = ''
                    THEN 'Migrated solution'
                    ELSE regexp_replace(
                        btrim(solution.description),
                        '[[:space:]]+',
                        ' ',
                        'g'
                    )
                END,
                250
            ),
            COALESCE(NULLIF(btrim(solution.description), ''),
                    'Migrated solution content.'),
            'EXPLANATION',
            CASE
                WHEN solution.review_status IN ('APPROVED', 'ACCEPTED')
                    THEN 'APPROVED'
                WHEN solution.review_status = 'REJECTED'
                    THEN 'REJECTED'
                ELSE 'PENDING'
            END,
            solution.rejection_reason,
            solution.reviewed_by,
            solution.reviewed_at,
            solution.created_at,
            solution.updated_at
        FROM public.solutions solution
        WHERE NOT EXISTS (
            SELECT 1
            FROM public.solution_revisions revision
            WHERE revision.solution_id = solution.id
        );

        UPDATE public.solutions solution
        SET latest_revision_id = revision.id,
            current_published_revision_id = CASE
                WHEN revision.moderation_status = 'APPROVED'
                    THEN revision.id
                ELSE NULL
            END
        FROM public.solution_revisions revision
        WHERE revision.solution_id = solution.id
          AND revision.revision_number = 1
          AND solution.latest_revision_id IS NULL;

        INSERT INTO public.solution_resources (
            id,
            solution_revision_id,
            type,
            label,
            url,
            display_order,
            created_at
        )
        SELECT
            (md5(solution.id::text || ':video'))::uuid,
            solution.latest_revision_id,
            'VIDEO',
            'Video',
            solution.video_url,
            0,
            solution.created_at
        FROM public.solutions solution
        WHERE solution.video_url IS NOT NULL
          AND btrim(solution.video_url) <> ''
          AND lower(btrim(solution.video_url)) LIKE 'https://%'
          AND NOT EXISTS (
              SELECT 1 FROM public.solution_resources resource
              WHERE resource.id = (md5(solution.id::text || ':video'))::uuid
          );

        INSERT INTO public.solution_resources (
            id,
            solution_revision_id,
            type,
            label,
            url,
            display_order,
            created_at
        )
        SELECT
            (md5(solution.id::text || ':diagram'))::uuid,
            solution.latest_revision_id,
            'DIAGRAM',
            'Diagram',
            solution.diagram_url,
            1,
            solution.created_at
        FROM public.solutions solution
        WHERE solution.diagram_url IS NOT NULL
          AND btrim(solution.diagram_url) <> ''
          AND lower(btrim(solution.diagram_url)) LIKE 'https://%'
          AND NOT EXISTS (
              SELECT 1 FROM public.solution_resources resource
              WHERE resource.id = (md5(solution.id::text || ':diagram'))::uuid
          );

        INSERT INTO public.problem_accepted_solutions (
            id,
            problem_id,
            solution_id,
            accepted_by,
            accepted_at
        )
        SELECT
            (md5(accepted.problem_id::text || ':accepted:'
                    || accepted.id::text))::uuid,
            accepted.problem_id,
            accepted.id,
            problem.author_id,
            COALESCE(accepted.reviewed_at, accepted.updated_at)
        FROM public.solutions accepted
        JOIN public.problems problem ON problem.id = accepted.problem_id
        WHERE accepted.review_status = 'ACCEPTED'
          AND accepted.deleted_at IS NULL
        ON CONFLICT (problem_id, solution_id) DO NOTHING;

        -- Backfill deployments that previously stored one accepted solution
        -- directly on problems. Dynamic SQL keeps fresh databases compatible
        -- because those legacy columns are no longer part of the entity.
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'problems'
              AND column_name = 'accepted_solution_id'
        ) THEN
            EXECUTE $migration$
                INSERT INTO public.problem_accepted_solutions (
                    id,
                    problem_id,
                    solution_id,
                    accepted_by,
                    accepted_at
                )
                SELECT
                    (md5(problem.id::text || ':accepted:'
                            || problem.accepted_solution_id::text))::uuid,
                    problem.id,
                    problem.accepted_solution_id,
                    COALESCE(problem.accepted_by, problem.author_id),
                    COALESCE(problem.accepted_at, problem.updated_at)
                FROM public.problems problem
                WHERE problem.accepted_solution_id IS NOT NULL
                ON CONFLICT (problem_id, solution_id) DO NOTHING
            $migration$;

            ALTER TABLE public.problems
                DROP CONSTRAINT IF EXISTS fk_problems_accepted_solution;
            ALTER TABLE public.problems
                DROP COLUMN IF EXISTS accepted_solution_id;
            ALTER TABLE public.problems
                DROP COLUMN IF EXISTS accepted_by;
            ALTER TABLE public.problems
                DROP COLUMN IF EXISTS accepted_at;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'fk_solutions_current_published_revision'
        ) THEN
            ALTER TABLE public.solutions
                ADD CONSTRAINT fk_solutions_current_published_revision
                FOREIGN KEY (current_published_revision_id)
                REFERENCES public.solution_revisions (id);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'fk_solutions_latest_revision'
        ) THEN
            ALTER TABLE public.solutions
                ADD CONSTRAINT fk_solutions_latest_revision
                FOREIGN KEY (latest_revision_id)
                REFERENCES public.solution_revisions (id);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'fk_problem_accepted_solutions_solution'
        ) THEN
            ALTER TABLE public.problem_accepted_solutions
                ADD CONSTRAINT fk_problem_accepted_solutions_solution
                FOREIGN KEY (solution_id)
                REFERENCES public.solutions (id);
        END IF;

        CREATE INDEX IF NOT EXISTS idx_solution_revisions_moderation
            ON public.solution_revisions (moderation_status, created_at);
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'uq_solutions_published_revision'
        ) THEN
            ALTER TABLE public.solutions
                ADD CONSTRAINT uq_solutions_published_revision
                UNIQUE (current_published_revision_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'uq_solutions_latest_revision'
        ) THEN
            ALTER TABLE public.solutions
                ADD CONSTRAINT uq_solutions_latest_revision
                UNIQUE (latest_revision_id);
        END IF;
        CREATE INDEX IF NOT EXISTS idx_solutions_problem_published
            ON public.solutions (problem_id)
            WHERE current_published_revision_id IS NOT NULL
              AND deleted_at IS NULL;
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

ALTER TABLE IF EXISTS public.programs
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP(6)^^^

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


-- Tag links for showcases. Hibernate creates these on a fresh database, but
-- the guards let the statements run against an existing one where ddl-auto
-- is not "update", so a deploy does not need a hand-applied migration.
DO $$
BEGIN
    IF to_regclass('public.showcases') IS NOT NULL
       AND to_regclass('public.tags') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.showcase_tags (
            showcase_id UUID NOT NULL
                REFERENCES public.showcases (id),
            tag_id UUID NOT NULL
                REFERENCES public.tags (id),
            created_at TIMESTAMP(6) NOT NULL,
            PRIMARY KEY (showcase_id, tag_id)
        );
    END IF;

    IF to_regclass('public.showcase_revisions') IS NOT NULL
       AND to_regclass('public.tags') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.showcase_revision_tags (
            revision_id UUID NOT NULL
                REFERENCES public.showcase_revisions (id),
            tag_id UUID NOT NULL
                REFERENCES public.tags (id),
            created_at TIMESTAMP(6) NOT NULL,
            PRIMARY KEY (revision_id, tag_id)
        );
    END IF;
END
$$^^^

-- Proof of concept requirements became structured guidelines, like the rules
-- of engagement and exclusions beside it. Existing rows hold free text: keep it
-- as the description and leave the rule list empty, which is the only reading
-- that loses nothing. Guarded on the current type so a database Hibernate has
-- already migrated is left alone.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'programs'
          AND column_name = 'proof_of_concept_requirements'
          AND data_type <> 'jsonb'
    ) THEN
        ALTER TABLE public.programs
            ALTER COLUMN proof_of_concept_requirements
            TYPE jsonb
            USING CASE
                WHEN proof_of_concept_requirements IS NULL
                     OR btrim(proof_of_concept_requirements) = ''
                    THEN NULL
                ELSE jsonb_build_object(
                    'description', proof_of_concept_requirements,
                    'rules', '[]'::jsonb
                )
            END;
    END IF;
END
$$^^^


-- Problem listings all filter on soft-deleted rows and then on either status
-- or author, and the public feed sorts by published_at. Without these each
-- page is a sequential scan over every problem ever posted.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'problems'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_problems_public_feed
            ON public.problems (status, published_at DESC)
            WHERE deleted_at IS NULL;

        CREATE INDEX IF NOT EXISTS idx_problems_author
            ON public.problems (author_id, status)
            WHERE deleted_at IS NULL;

        CREATE INDEX IF NOT EXISTS idx_problems_category
            ON public.problems (category_id)
            WHERE deleted_at IS NULL;
    END IF;
END
$$^^^
