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

-- Where a researcher stands with one company. Only 'approved' can file a report.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'researcher_access_status_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.researcher_access_status_enum AS ENUM (
            'pending',
            'approved',
            'rejected',
            'revoked'
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

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'report_environment_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.report_environment_enum AS ENUM (
            'production',
            'staging',
            'development',
            'testing',
            'local'
        );
    END IF;
END
$$^^^


-- Submission detail a triager needs to reproduce a finding without a round
-- trip: where it lives, which environment it was seen in, how to repeat it,
-- and the CVSS the severity claim is based on. Hibernate adds these on a
-- fresh database; the VPS does not run with ddl-auto "update", so without
-- this block a deploy ships the fields and no columns to put them in.
DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL THEN
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS steps_to_reproduce TEXT;
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS proof_of_concept TEXT;
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS remediation_recommendation TEXT;
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS target_endpoint VARCHAR(1000);
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS environment
                public.report_environment_enum;
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS discovered_at TIMESTAMP(6);
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS reference_links JSONB;
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS cvss_vector VARCHAR(255);
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS cvss_score NUMERIC(3, 1);
    END IF;
END
$$^^^


-- The rest of the Report entity's columns.
--
-- schema.sql never creates public.reports — it only alters it — so the table
-- on any long-lived database is whatever Hibernate built when ddl-auto was
-- still "update", plus whatever blocks like this one have added since. A field
-- added to the entity after that and not listed here exists on a fresh
-- database and nowhere else.
--
-- That is not a hypothetical failure mode. Hibernate names every column of an
-- entity in its SELECT, so one missing column does not degrade the rows that
-- use it: it fails the statement. Every read of a Report then 500s, including
-- the ones that would have matched nothing, and the endpoint that only reads
-- Reports through a join — the public hacktivity feed — 500s for every caller
-- and every page while its neighbours stay green.
--
-- ADD COLUMN IF NOT EXISTS throughout, so this is a no-op wherever Hibernate
-- got there first.
DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL THEN

        -- The two severities either side of triage, and the agreed one that
        -- reconcile_report_severity() settles between them.
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS severity public.severity_enum;
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS triage_severity public.severity_enum;

        -- Who triaged it and when.
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS triaged_by UUID;
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS triaged_at TIMESTAMP(6);

        -- The report this one duplicates, once triage has said so.
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS duplicate_of_id UUID;

        -- The in-scope target the finding sits on.
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS asset_id UUID;

        -- What resolving the finding was worth to its reporter, stamped once
        -- when the report is first resolved. Left NULL on reports resolved
        -- before reputation was paid on resolution: backfilling them would
        -- hand out standing for findings that were closed under the older
        -- rule, and nothing here ever subtracts reputation again.
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS reputation_points INTEGER;
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS reputation_awarded_at TIMESTAMP(6);

        -- The windowed leaderboards group by this over a cut-off date, which
        -- without an index is a scan of every report on the platform per page.
        CREATE INDEX IF NOT EXISTS idx_reports_reputation_awarded_at
            ON public.reports (reputation_awarded_at);

        -- Whether the finding is public. NOT NULL in the entity, so it is
        -- added nullable, backfilled and then tightened — an existing row has
        -- no answer to a question that was not being asked when it was
        -- written, and "not disclosed" is the safe one.
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS disclosure_status
                public.disclosure_status_enum;

        UPDATE public.reports
        SET disclosure_status = 'not_disclosed'
        WHERE disclosure_status IS NULL;

        ALTER TABLE public.reports
            ALTER COLUMN disclosure_status SET DEFAULT 'not_disclosed';
        ALTER TABLE public.reports
            ALTER COLUMN disclosure_status SET NOT NULL;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_reports_triaged_by'
              AND conrelid = 'public.reports'::regclass
        ) AND to_regclass('public.user_profiles') IS NOT NULL THEN
            ALTER TABLE public.reports
                ADD CONSTRAINT fk_reports_triaged_by
                FOREIGN KEY (triaged_by)
                REFERENCES public.user_profiles (id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_reports_duplicate_of'
              AND conrelid = 'public.reports'::regclass
        ) THEN
            ALTER TABLE public.reports
                ADD CONSTRAINT fk_reports_duplicate_of
                FOREIGN KEY (duplicate_of_id)
                REFERENCES public.reports (id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_reports_asset'
              AND conrelid = 'public.reports'::regclass
        ) AND to_regclass('public.program_assets') IS NOT NULL THEN
            ALTER TABLE public.reports
                ADD CONSTRAINT fk_reports_asset
                FOREIGN KEY (asset_id)
                REFERENCES public.program_assets (id);
        END IF;

        -- The public feed filters on disclosure and triage reads the duplicate
        -- chain; both are lookups over the whole report table without these.
        CREATE INDEX IF NOT EXISTS idx_reports_disclosure_status
            ON public.reports (disclosure_status);
        CREATE INDEX IF NOT EXISTS idx_reports_duplicate_of_id
            ON public.reports (duplicate_of_id);
    END IF;
END
$$^^^


-- Severity disputes. The mediation queue is the only way a report whose two
-- severity claims disagree ever becomes payable, so the table has to exist
-- wherever the triggers below do.
DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL
       AND to_regclass('public.user_profiles') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.disputes (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            report_id UUID NOT NULL
                REFERENCES public.reports (id),
            raised_by UUID NOT NULL
                REFERENCES public.user_profiles (id),
            reason TEXT NOT NULL,
            status public.dispute_status_enum NOT NULL DEFAULT 'open',
            resolved_severity public.severity_enum,
            resolved_by UUID
                REFERENCES public.user_profiles (id),
            resolution_notes TEXT,
            created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            resolved_at TIMESTAMP(6)
        );
    END IF;

    IF to_regclass('public.disputes') IS NOT NULL THEN
        -- open_report_severity_dispute() inserts without an id or a created_at,
        -- because a trigger has no application-side generator to call. Hibernate
        -- creates both columns NOT NULL with no default, so on a database it
        -- built the auto-dispute insert fails outright and takes the whole
        -- triage down with it. These defaults are what make that insert legal.
        ALTER TABLE public.disputes
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.disputes
            ALTER COLUMN created_at SET DEFAULT now();

        -- The administrator queue reads live disputes oldest first, and every
        -- triage and reward checks whether one report has an active dispute.
        CREATE INDEX IF NOT EXISTS idx_disputes_status_created
            ON public.disputes (status, created_at);

        CREATE INDEX IF NOT EXISTS idx_disputes_report
            ON public.disputes (report_id);
    END IF;
END
$$^^^

CREATE OR REPLACE FUNCTION public.reconcile_report_severity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    -- Held as text, not severity_enum. This CREATE FUNCTION runs unguarded on
    -- every startup, and a declared variable's type has to exist when the body
    -- is validated — but severity_enum is created further down this file, so on
    -- a fresh database it does not exist yet. text always does, and the cast
    -- back below is resolved on first call, by which point the type is there.
    ruled_severity text;
BEGIN
    -- An administrator's ruling on a severity dispute outranks both the
    -- reporter's claim and the company's, and it is the thing that unfreezes
    -- the report — so it is read before either of them. Without this branch the
    -- ruling survives only until the next write to the row: Hibernate updates
    -- every column, so this trigger fires on saves that changed neither
    -- severity, and the final ELSE would null the agreed severity back out.
    IF NEW.id IS NOT NULL THEN
        SELECT d.resolved_severity::text
          INTO ruled_severity
          FROM public.disputes d
         WHERE d.report_id = NEW.id
           AND d.status IN ('resolved', 'dismissed')
           AND d.resolved_severity IS NOT NULL
         ORDER BY d.resolved_at DESC NULLS LAST, d.created_at DESC
         LIMIT 1;
    END IF;

    IF ruled_severity IS NOT NULL THEN
        NEW.severity := ruled_severity::public.severity_enum;
    ELSIF NEW.id IS NOT NULL
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
    -- Only a fresh assessment can open a dispute. Hibernate writes every column
    -- on any report save, so this trigger also fires for updates that touched
    -- neither severity — a disclosure change, an administrator writing the
    -- ruled severity — and without this guard resolving a dispute would open
    -- another one on the very next save, deadlocking the report for good.
    IF TG_OP = 'UPDATE'
       AND NEW.reported_severity IS NOT DISTINCT FROM OLD.reported_severity
       AND NEW.triage_severity IS NOT DISTINCT FROM OLD.triage_severity THEN
        RETURN NEW;
    END IF;

    IF NEW.triage_severity IS NOT NULL
       AND NEW.reported_severity <> NEW.triage_severity
       -- A report gets one severity dispute, ever. Once an administrator has
       -- ruled, the ruling stands and re-triaging cannot reopen the argument,
       -- so this looks for any dispute rather than only a live one.
       AND NOT EXISTS (
           SELECT 1
           FROM public.disputes d
           WHERE d.report_id = NEW.id
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
            'not_submitted',
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

-- Databases created before programs had an unsubmitted state already own
-- submission_state_enum, so the CREATE TYPE above is skipped for them.
-- Kept outside the DO block: ALTER TYPE ... ADD VALUE must not run inside a
-- subtransaction.
ALTER TYPE public.submission_state_enum
    ADD VALUE IF NOT EXISTS 'not_submitted' BEFORE 'pending_review'^^^

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

-- A program handed to administrators is no longer an authoring draft. Repair
-- rows produced by the former submit/re-review transition and prevent that
-- pair from returning. Deleted rows are exempt until restore applies the rule.
DO $$
BEGIN
    IF to_regclass('public.programs') IS NOT NULL THEN
        UPDATE public.programs
        SET state = 'active'
        WHERE state::text = 'draft'
          AND submission_state::text = 'pending_review'
          AND deleted_at IS NULL;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'ck_programs_no_pending_draft'
              AND conrelid = 'public.programs'::regclass
        ) THEN
            ALTER TABLE public.programs
                ADD CONSTRAINT ck_programs_no_pending_draft
                CHECK (
                    deleted_at IS NOT NULL
                    OR state::text <> 'draft'
                    OR submission_state::text <> 'pending_review'
                );
        END IF;
    END IF;
END
$$^^^

DO $$
BEGIN
    -- The organization banner. Nullable with no backfill: no cover is the
    -- correct state for every row that predates it.
    IF to_regclass('public.organizations') IS NOT NULL THEN
        ALTER TABLE public.organizations
            ADD COLUMN IF NOT EXISTS cover_image_url VARCHAR(500);
    END IF;

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


-- Recognition and hacktivity. Hibernate builds both on a fresh database, but
-- the VPS does not run with ddl-auto "update", so without these a deploy ships
-- the entities and no tables to put them in. Guarded on the parents so the
-- block is inert until reports, programs and organizations exist.
DO $$
BEGIN
    IF to_regclass('public.user_profiles') IS NOT NULL
       AND to_regclass('public.programs') IS NOT NULL
       AND to_regclass('public.reports') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.recognitions (
            id UUID PRIMARY KEY,
            user_id UUID NOT NULL
                REFERENCES public.user_profiles (id),
            program_id UUID NOT NULL
                REFERENCES public.programs (id),
            report_id UUID NOT NULL
                REFERENCES public.reports (id),
            title VARCHAR(255) NOT NULL,
            description TEXT,
            awarded_by UUID NOT NULL,
            awarded_at TIMESTAMP(6) NOT NULL,
            severity public.severity_enum NOT NULL,
            created_at TIMESTAMP(6) NOT NULL,
            updated_at TIMESTAMP(6) NOT NULL
        );

        -- One recognition per report, or the same finding can be awarded twice
        -- and paid twice. Created separately from the table so that a database
        -- where Hibernate already built recognitions without the constraint
        -- picks it up too. If this fails, the table already holds duplicates
        -- and they have to be reconciled by hand before the guard can go on.
        CREATE UNIQUE INDEX IF NOT EXISTS uq_recognitions_report
            ON public.recognitions (report_id);

        CREATE INDEX IF NOT EXISTS idx_recognitions_user_id
            ON public.recognitions (user_id);

        CREATE INDEX IF NOT EXISTS idx_recognitions_program_id
            ON public.recognitions (program_id);
    END IF;

    IF to_regclass('public.recognitions') IS NOT NULL
       AND to_regclass('public.organizations') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.hacktivities (
            id UUID PRIMARY KEY,
            recognition_id UUID NOT NULL UNIQUE
                REFERENCES public.recognitions (id),
            user_id UUID NOT NULL
                REFERENCES public.user_profiles (id),
            organization_id UUID NOT NULL
                REFERENCES public.organizations (id),
            report_id UUID NOT NULL
                REFERENCES public.reports (id),
            program_id UUID NOT NULL
                REFERENCES public.programs (id),
            created_at TIMESTAMP(6) NOT NULL
        );

        -- The feed sorts by created_at and filters by researcher, organization
        -- or program. Without these every page is a sequential scan over the
        -- whole feed.
        CREATE INDEX IF NOT EXISTS idx_hacktivity_created_at
            ON public.hacktivities (created_at);

        CREATE INDEX IF NOT EXISTS idx_hacktivity_user_id
            ON public.hacktivities (user_id);

        CREATE INDEX IF NOT EXISTS idx_hacktivity_organization_id
            ON public.hacktivities (organization_id);

        CREATE INDEX IF NOT EXISTS idx_hacktivity_program_id
            ON public.hacktivities (program_id);
    END IF;
END
$$^^^


-- The severity a recognition was awarded at.
--
-- The CREATE TABLE above declares it, which is enough for a database built
-- from this script and no use at all to one that already had the table:
-- CREATE TABLE IF NOT EXISTS does nothing to a table that exists, so every
-- database created before the column was added to the entity has been missing
-- it ever since. Hibernate names every column of an entity in its SELECT, so
-- the miss does not degrade anything gracefully — it failed the statement.
-- The public hacktivity feed left joins recognitions, so it answered every
-- caller, on every page, with
--   ERROR: column r2_0.severity does not exist
-- while its neighbours stayed green. The one endpoint that never joined
-- recognitions, /hacktivity/stats, kept working, which is what made this look
-- like a feed bug rather than a missing column.
--
-- Added nullable, backfilled, then tightened, the same shape disclosure_status
-- uses above: an existing row has an answer to this, it just lives on the
-- report.
DO $$
BEGIN
    IF to_regclass('public.recognitions') IS NOT NULL THEN

        ALTER TABLE public.recognitions
            ADD COLUMN IF NOT EXISTS severity public.severity_enum;

        -- Copied column to column rather than written as literals. The enum's
        -- labels differ between a database Hibernate built and one this script
        -- did, and a copy needs no literal to get wrong.
        UPDATE public.recognitions recognition
           SET severity = report.severity
          FROM public.reports report
         WHERE report.id = recognition.report_id
           AND recognition.severity IS NULL
           AND report.severity IS NOT NULL;

        -- Only once every row has one. A recognition whose report severity is
        -- still unsettled cannot be filled in from anywhere, and failing this
        -- statement would abort the rest of the script with it.
        IF NOT EXISTS (
            SELECT 1 FROM public.recognitions WHERE severity IS NULL
        ) THEN
            ALTER TABLE public.recognitions
                ALTER COLUMN severity SET NOT NULL;
        END IF;
    END IF;
END
$$^^^


-- What each feed row says happened, as a stored fact rather than something a
-- reader infers from which nested object came back non-null.
--
-- Added nullable, backfilled, then tightened, so it applies to a database that
-- already holds rows. A plain varchar rather than a Postgres enum: widening the
-- vocabulary is then an application change, with no CREATE TYPE that has to
-- land before the column on every environment.
DO $$
BEGIN
    IF to_regclass('public.hacktivities') IS NOT NULL THEN

        ALTER TABLE public.hacktivities
            ADD COLUMN IF NOT EXISTS event_type VARCHAR(40);

        -- Existing rows all came from the recognition path. Which of the two
        -- kinds they were is still recoverable: a recognised report carrying a
        -- payout with money on it was a bounty. Points-only is not — it moves
        -- the leaderboard, not anybody's bank.
        --
        -- Guarded separately from the column, and followed by an unconditional
        -- sweep, so that a database without report_rewards still ends up with
        -- every row filled in. Skipping the backfill and then asking for NOT
        -- NULL would fail this script, and a script that fails is a service
        -- that does not start.
        IF to_regclass('public.report_rewards') IS NOT NULL THEN
            UPDATE public.hacktivities hacktivity
            SET event_type = 'BOUNTY_AWARDED'
            WHERE hacktivity.event_type IS NULL
              AND EXISTS (
                    SELECT 1
                    FROM public.report_rewards reward
                    WHERE reward.report_id = hacktivity.report_id
                      AND reward.amount IS NOT NULL
                      AND reward.amount > 0
                );
        END IF;

        UPDATE public.hacktivities
        SET event_type = 'RECOGNITION_AWARDED'
        WHERE event_type IS NULL;

        ALTER TABLE public.hacktivities
            ALTER COLUMN event_type SET DEFAULT 'RECOGNITION_AWARDED';

        ALTER TABLE public.hacktivities
            ALTER COLUMN event_type SET NOT NULL;

        -- eventType is a feed filter, so it is a WHERE clause on the same
        -- scans created_at already carries an index for.
        CREATE INDEX IF NOT EXISTS idx_hacktivity_event_type
            ON public.hacktivities (event_type);
    END IF;
END
$$^^^


-- problems.category_id is mapped as a plain UUID column rather than an
-- association, so Hibernate never generated a foreign key for it. Without one
-- a category delete succeeded and left every problem filed under it pointing
-- at a row that no longer exists: no category on the listing, and no way to
-- re-submit the draft. The service refuses such a delete now, but a count
-- followed by a delete is still a race, and nothing outside the service is
-- bound by that check at all.
--
-- Added only when the column is already clean. If orphans exist they have to
-- be repointed by hand first; failing every deploy until someone notices would
-- help nobody.
--
-- The orphan check sits in a nested IF rather than beside the to_regclass
-- guards. PL/pgSQL prepares a condition as one query before evaluating any of
-- it, so a guard cannot protect a table reference standing next to it: on a
-- fresh database, where problems does not exist yet, naming it in the same
-- condition fails to parse and takes the whole script down. Every other block
-- in this file keeps table references in the body for the same reason.
DO $$
BEGIN
    IF to_regclass('public.problems') IS NOT NULL
       AND to_regclass('public.categories') IS NOT NULL THEN

        IF NOT EXISTS (
               SELECT 1
               FROM pg_constraint
               WHERE conname = 'fk_problems_category'
           )
           AND NOT EXISTS (
               SELECT 1
               FROM public.problems problem
               LEFT JOIN public.categories category
                   ON category.id = problem.category_id
               WHERE category.id IS NULL
           ) THEN
            ALTER TABLE public.problems
                ADD CONSTRAINT fk_problems_category
                FOREIGN KEY (category_id)
                REFERENCES public.categories (id);
        END IF;
    END IF;
END
$$^^^


-- Comments gained two more ways of being gone and a record of being edited.
--
-- removed_at is not deleted_at. A comment with replies underneath it cannot be
-- taken away without taking somebody else's writing with it, so deleting one
-- clears the text and leaves the row holding its place in the thread;
-- deleted_at stays for comments nothing hangs off, which really do disappear.
-- removal_reason separates "the author took this back" from "a moderator took
-- this down", which read very differently to anyone following the thread.
--
-- edited_at exists because updated_at cannot answer the question. Hibernate
-- bumps updated_at whenever the row changes, including on removal, and it
-- already equals created_at on a comment nobody has touched.
DO $$
BEGIN
    IF to_regclass('public.comments') IS NOT NULL THEN
        ALTER TABLE public.comments
            ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP WITHOUT TIME ZONE;

        ALTER TABLE public.comments
            ADD COLUMN IF NOT EXISTS removed_at TIMESTAMP WITHOUT TIME ZONE;

        ALTER TABLE public.comments
            ADD COLUMN IF NOT EXISTS removed_by UUID;

        ALTER TABLE public.comments
            ADD COLUMN IF NOT EXISTS removal_reason VARCHAR(20);
    END IF;
END
$$^^^


-- Indexes for the paths the comment feature actually takes.
--
-- Reading a discussion filters on the target and the parent every time, and
-- the rate limiter counts an author's recent comments on every post. Without
-- these all three are sequential scans that get slower as the table grows,
-- which is exactly the point at which somebody notices.
DO $$
BEGIN
    IF to_regclass('public.comments') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_comments_target
            ON public.comments (commentable_type, commentable_id)
            WHERE deleted_at IS NULL;

        CREATE INDEX IF NOT EXISTS idx_comments_parent
            ON public.comments (parent_comment_id)
            WHERE deleted_at IS NULL;

        CREATE INDEX IF NOT EXISTS idx_comments_author_created
            ON public.comments (author_id, created_at);
    END IF;
END
$$^^^


-- Vote tallies are read per target now that comment listings carry their
-- scores. The unique constraint on (user_id, votable_type, votable_id) cannot
-- serve that lookup: it leads with the user, and the aggregate does not know
-- one.
DO $$
BEGIN
    IF to_regclass('public.votes') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_votes_votable
            ON public.votes (votable_type, votable_id);
    END IF;
END
$$^^^


-- Search on both public feeds is LOWER(col) LIKE '%term%'. A leading wildcard
-- makes a B-tree index useless, so every search was a sequential scan over
-- title, overview and description — the cost of which grows with the platform
-- while the query looks unchanged.
--
-- pg_trgm indexes trigrams rather than prefixes, which is the one index type
-- that can serve a leading wildcard. The indexes are on LOWER(col) because
-- that is what the queries compare; an index on the bare column would be
-- ignored.
CREATE EXTENSION IF NOT EXISTS pg_trgm^^^


DO $$
BEGIN
    IF to_regclass('public.showcases') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_showcases_title_trgm
            ON public.showcases
            USING gin (LOWER(title) gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS idx_showcases_overview_trgm
            ON public.showcases
            USING gin (LOWER(overview) gin_trgm_ops);

        -- The feed always filters on review status and orders by recency;
        -- keeping both in one index lets the common listing skip the sort.
        CREATE INDEX IF NOT EXISTS idx_showcases_status_created
            ON public.showcases (review_status, created_at DESC, id DESC)
            WHERE deleted_at IS NULL;
    END IF;
END
$$^^^


DO $$
BEGIN
    IF to_regclass('public.problems') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_problems_title_trgm
            ON public.problems
            USING gin (LOWER(title) gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS idx_problems_description_trgm
            ON public.problems
            USING gin (LOWER(description) gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS idx_problems_status_published
            ON public.problems (status, published_at DESC, id DESC);
    END IF;
END
$$^^^


-- The user profile name is searched as part of the showcase feed, through the
-- join to the author.
DO $$
BEGIN
    IF to_regclass('public.user_profiles') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_user_profiles_full_name_trgm
            ON public.user_profiles
            USING gin (LOWER(full_name) gin_trgm_ops);
    END IF;
END
$$^^^


-- Public program discovery has its own publication timestamp and view count.
-- Existing public programs are backfilled once so the default newest sort
-- does not send them to the end of the feed after this feature is deployed.
DO $$
BEGIN
    IF to_regclass('public.programs') IS NOT NULL THEN
        ALTER TABLE public.programs
            ADD COLUMN IF NOT EXISTS view_count BIGINT DEFAULT 0;

        UPDATE public.programs
        SET view_count = 0
        WHERE view_count IS NULL;

        ALTER TABLE public.programs
            ALTER COLUMN view_count SET DEFAULT 0;

        ALTER TABLE public.programs
            ALTER COLUMN view_count SET NOT NULL;

        ALTER TABLE public.programs
            ADD COLUMN IF NOT EXISTS published_at TIMESTAMP(6);

        UPDATE public.programs
        SET published_at = COALESCE(updated_at, created_at)
        WHERE published_at IS NULL
          AND state::text = 'active'
          AND submission_state::text = 'approved'
          AND visibility::text = 'public'
          AND deleted_at IS NULL;
    END IF;
END
$$^^^


-- A draft is unfinished work, so the fields an author has not reached yet
-- must be storable as absent. While these were NOT NULL, saving step one of a
-- program wizard meant inventing the answers to steps two and three, and
-- invented text that survives to publication is what researchers end up bound
-- by. Completeness is enforced at submission instead. Only NOT NULL is
-- dropped; no row changes.
DO $$
BEGIN
    IF to_regclass('public.programs') IS NOT NULL THEN
        ALTER TABLE public.programs
            ALTER COLUMN engagement_type DROP NOT NULL;

        ALTER TABLE public.programs
            ALTER COLUMN rules_of_engagement DROP NOT NULL;

        ALTER TABLE public.programs
            ALTER COLUMN exclusions DROP NOT NULL;
    END IF;
END
$$^^^


-- Program search uses leading-wildcard LOWER(...) matches, while the common
-- feed and aggregate sorts have predictable filter keys. These indexes keep
-- those paths from degrading into full scans as programs and reports grow.
DO $$
BEGIN
    IF to_regclass('public.programs') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_programs_name_trgm
            ON public.programs
            USING gin (LOWER(name) gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS idx_programs_handle_trgm
            ON public.programs
            USING gin (LOWER(handle) gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS idx_programs_description_trgm
            ON public.programs
            USING gin (LOWER(description) gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS idx_programs_public_published
            ON public.programs (
                state,
                submission_state,
                visibility,
                published_at DESC,
                id DESC
            )
            WHERE deleted_at IS NULL;

        CREATE INDEX IF NOT EXISTS idx_programs_organization_public
            ON public.programs (
                organization_id,
                state,
                submission_state,
                visibility,
                published_at DESC,
                id DESC
            )
            WHERE deleted_at IS NULL;

        CREATE INDEX IF NOT EXISTS idx_programs_public_filters
            ON public.programs (
                engagement_type,
                offers_bounties,
                minimum_bounty,
                maximum_bounty
            )
            WHERE deleted_at IS NULL;

        CREATE INDEX IF NOT EXISTS idx_programs_views
            ON public.programs (view_count DESC, id DESC)
            WHERE deleted_at IS NULL;
    END IF;

    IF to_regclass('public.organizations') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_organizations_name_trgm
            ON public.organizations
            USING gin (LOWER(name) gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS idx_organizations_slug_trgm
            ON public.organizations
            USING gin (LOWER(slug) gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS idx_organizations_program_filters
            ON public.organizations (industry, LOWER(country));
    END IF;

    IF to_regclass('public.program_assets') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_program_assets_public_filters
            ON public.program_assets (
                program_id,
                is_in_scope,
                asset_type,
                max_severity
            );
    END IF;

    IF to_regclass('public.reports') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_reports_program
            ON public.reports (program_id);
    END IF;

    IF to_regclass('public.follows') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_follows_followable
            ON public.follows (followable_type, followable_id);
    END IF;
END
$$^^^

-- PROGRAM joined FlaggableType. Hibernate wrote the check constraint on
-- content_flags.flaggable_type when it first created the table, listing only
-- the values that existed then, and ddl-auto=update never revises one — so an
-- existing database rejects a program flag until the constraint is replaced.
-- Any check mentioning the column is dropped, since its generated name is not
-- guaranteed, and this block re-applies cleanly on every boot.
DO $$
DECLARE
    stale_constraint TEXT;
BEGIN
    IF to_regclass('public.content_flags') IS NOT NULL THEN
        FOR stale_constraint IN
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class rel
                ON rel.oid = con.conrelid
            JOIN pg_namespace nsp
                ON nsp.oid = rel.relnamespace
            WHERE nsp.nspname = 'public'
              AND rel.relname = 'content_flags'
              AND con.contype = 'c'
              AND pg_get_constraintdef(con.oid) LIKE '%flaggable_type%'
        LOOP
            EXECUTE format(
                'ALTER TABLE public.content_flags DROP CONSTRAINT %I',
                stale_constraint
            );
        END LOOP;

        ALTER TABLE public.content_flags
            ADD CONSTRAINT content_flags_flaggable_type_check
            CHECK (flaggable_type IN (
                'PROGRAM',
                'PROBLEM',
                'SOLUTION',
                'COMMENT',
                'SHOWCASE'
            ));
    END IF;
END
$$^^^


-- The content filter raises flags nobody reported, so a flag no longer needs
-- a reporter and now records where it came from.
--
-- reporter_id loses its NOT NULL rather than pointing at a system account.
-- A row in user_profiles standing in for "the filter" would have to be
-- excluded by hand from every user search, follower count and leaderboard,
-- and the first query that forgot would show it to somebody.
--
-- Existing rows are all reports somebody made by hand, which is what the
-- backfill says before the column is made NOT NULL.
DO $$
BEGIN
    IF to_regclass('public.content_flags') IS NOT NULL THEN
        ALTER TABLE public.content_flags
            ALTER COLUMN reporter_id DROP NOT NULL;

        ALTER TABLE public.content_flags
            ADD COLUMN IF NOT EXISTS source VARCHAR(20);

        UPDATE public.content_flags
        SET source = 'USER'
        WHERE source IS NULL;

        ALTER TABLE public.content_flags
            ALTER COLUMN source SET DEFAULT 'USER';

        ALTER TABLE public.content_flags
            ALTER COLUMN source SET NOT NULL;

        -- The duplicate guard for automated flags looks up exactly this.
        CREATE INDEX IF NOT EXISTS idx_content_flags_source_target
            ON public.content_flags (source, flaggable_type, flaggable_id);
    END IF;
END
$$^^^


-- The weakness catalog: the closed vocabulary a report is classified under.
--
-- Created here rather than left to Hibernate because this file runs before
-- ddl-auto and the seed below needs the table to exist on the same boot, and
-- because the VPS does not run with ddl-auto "update" at all — without this
-- block the entity ships with no table to put it in, and every submission
-- carrying a weaknessId fails.
CREATE TABLE IF NOT EXISTS public.weaknesses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cwe_id VARCHAR(20),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now()
)^^^


-- "IF NOT EXISTS" above means the defaults it declares only ever land on a
-- database that did not already have this table. Where Hibernate built it on
-- an earlier boot the columns are NOT NULL with no default, because
-- @GeneratedValue, @CreationTimestamp and @Builder.Default all produce their
-- values in Java. The seed below supplies only cwe_id, name and description
-- and leaves the rest to the database, so without these it fails NOT NULL on
-- id, then created_at, then is_active, and takes startup down with it.
DO $$
BEGIN
    IF to_regclass('public.weaknesses') IS NOT NULL THEN
        ALTER TABLE public.weaknesses
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.weaknesses
            ALTER COLUMN created_at SET DEFAULT now();
        ALTER TABLE public.weaknesses
            ALTER COLUMN is_active SET DEFAULT TRUE;
    END IF;
END
$$^^^


-- Named explicitly rather than left as an inline UNIQUE, so the seed below
-- can rely on ON CONFLICT (cwe_id) resolving to it whether the table was
-- created by the statement above or by Hibernate on an earlier boot.
CREATE UNIQUE INDEX IF NOT EXISTS ux_weaknesses_cwe_id
    ON public.weaknesses (cwe_id)^^^


-- Reports point at the catalog. Nullable: a reporter who does not recognise
-- the class leaves it unset and triage assigns it.
DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL THEN
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS weakness_id UUID;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_reports_weakness'
              AND conrelid = 'public.reports'::regclass
        ) THEN
            ALTER TABLE public.reports
                ADD CONSTRAINT fk_reports_weakness
                FOREIGN KEY (weakness_id)
                REFERENCES public.weaknesses (id);
        END IF;

        -- Counting reports by class is the reason the field is a catalog
        -- rather than free text, and the delete guard on a catalog entry
        -- runs the same lookup.
        CREATE INDEX IF NOT EXISTS idx_reports_weakness_id
            ON public.reports (weakness_id);
    END IF;
END
$$^^^


-- A starting catalog: the CWE Top 25 plus the web classes a bug bounty
-- programme actually receives that the Top 25 leaves out. Names are the
-- common ones a reporter would recognise in a picker, with the formal
-- definition in the description; administrators extend the list from
-- /api/v1/admin/weaknesses.
--
-- ON CONFLICT so a redeploy leaves an edited or retired entry alone: this
-- seeds a catalog once, it does not own it afterwards.
INSERT INTO public.weaknesses (cwe_id, name, description)
VALUES
    ('CWE-79', 'Cross-site Scripting (XSS)',
     'Input is rendered into a page without neutralization, running attacker script in the browser of another user.'),
    ('CWE-89', 'SQL Injection',
     'Input reaches an SQL statement unescaped, letting an attacker read or change data the query never intended.'),
    ('CWE-78', 'OS Command Injection',
     'Input reaches a shell command, letting an attacker run arbitrary commands on the host.'),
    ('CWE-77', 'Command Injection',
     'Input reaches a command interpreter and alters the command that gets executed.'),
    ('CWE-94', 'Code Injection',
     'Input is evaluated as source code, letting an attacker execute code inside the application.'),
    ('CWE-918', 'Server-Side Request Forgery (SSRF)',
     'The server can be made to send requests to an address the attacker chooses, reaching internal services.'),
    ('CWE-352', 'Cross-Site Request Forgery (CSRF)',
     'A state-changing request can be forged from another site using the session of a logged-in victim.'),
    ('CWE-22', 'Path Traversal',
     'A path built from input escapes its intended directory and reaches other files.'),
    ('CWE-434', 'Unrestricted File Upload',
     'A file of a dangerous type can be uploaded and then executed or served back.'),
    ('CWE-611', 'XML External Entity (XXE)',
     'An XML parser resolves external entities, exposing local files or making the server issue requests.'),
    ('CWE-502', 'Deserialization of Untrusted Data',
     'Untrusted serialized data is deserialized, letting an attacker influence the objects the process creates.'),
    ('CWE-1336', 'Server-Side Template Injection (SSTI)',
     'Input is evaluated by a template engine, which usually leads to code execution.'),
    ('CWE-1321', 'Prototype Pollution',
     'Object prototype attributes can be modified through input, changing behaviour across the application.'),
    ('CWE-862', 'Missing Authorization',
     'A protected action can be performed with no authorization check at all.'),
    ('CWE-863', 'Incorrect Authorization',
     'An authorization check exists but admits requests it should refuse.'),
    ('CWE-639', 'Insecure Direct Object Reference (IDOR)',
     'Swapping an identifier in a request reaches data belonging to another account.'),
    ('CWE-269', 'Improper Privilege Management',
     'A user can obtain or keep privileges beyond what their role allows.'),
    ('CWE-732', 'Incorrect Permission Assignment',
     'A file, bucket, or record is readable or writable by more principals than intended.'),
    ('CWE-306', 'Missing Authentication for Critical Function',
     'A sensitive function is reachable with no authentication at all.'),
    ('CWE-287', 'Improper Authentication',
     'The authentication mechanism can be bypassed or satisfied by someone who should fail it.'),
    ('CWE-307', 'Improper Restriction of Excessive Authentication Attempts',
     'Credentials can be guessed because repeated attempts are not limited.'),
    ('CWE-521', 'Weak Password Requirements',
     'Password rules allow credentials that are trivial to guess.'),
    ('CWE-640', 'Weak Password Recovery Mechanism',
     'The forgotten-password flow can be abused to take over an account.'),
    ('CWE-620', 'Unverified Password Change',
     'A password can be changed without proving the current one or the identity behind the session.'),
    ('CWE-384', 'Session Fixation',
     'An attacker can set or keep a session identifier that survives the login of the victim.'),
    ('CWE-613', 'Insufficient Session Expiration',
     'Sessions or tokens stay valid long after they should have ended.'),
    ('CWE-798', 'Use of Hard-coded Credentials',
     'Credentials are embedded in source, configuration, or a shipped artifact.'),
    ('CWE-200', 'Exposure of Sensitive Information',
     'Data is disclosed to someone who should not be able to read it.'),
    ('CWE-209', 'Sensitive Information in an Error Message',
     'Errors return stack traces, queries, or internals useful to an attacker.'),
    ('CWE-538', 'Sensitive Information in an Accessible File',
     'Backups, logs, or configuration files are reachable over the network.'),
    ('CWE-319', 'Cleartext Transmission of Sensitive Information',
     'Sensitive data travels over a channel an attacker can read.'),
    ('CWE-311', 'Missing Encryption of Sensitive Data',
     'Sensitive data is stored or sent with no encryption.'),
    ('CWE-327', 'Broken or Risky Cryptographic Algorithm',
     'A cipher, hash, or mode no longer considered safe is in use.'),
    ('CWE-330', 'Use of Insufficiently Random Values',
     'A token or identifier that has to be unguessable is predictable.'),
    ('CWE-601', 'Open Redirect',
     'A redirect target comes from input, sending users to a site the attacker controls behind a trusted link.'),
    ('CWE-1021', 'Clickjacking',
     'The interface can be framed by another site and the clicks on it redirected.'),
    ('CWE-942', 'Permissive Cross-domain Policy',
     'A CORS or cross-domain policy trusts origins it should not.'),
    ('CWE-113', 'HTTP Response Splitting',
     'CRLF sequences from input reach response headers and split the response.'),
    ('CWE-444', 'HTTP Request Smuggling',
     'Two servers disagree on where a request ends, letting one request hide inside another.'),
    ('CWE-614', 'Sensitive Cookie Without Secure Flag',
     'A session cookie can be sent over an unencrypted connection.'),
    ('CWE-1004', 'Sensitive Cookie Without HttpOnly Flag',
     'A session cookie is readable by script, so an XSS becomes session theft.'),
    ('CWE-565', 'Reliance on Cookies Without Validation',
     'A security decision is made from a cookie the client can edit.'),
    ('CWE-345', 'Insufficient Verification of Data Authenticity',
     'Data is trusted without checking that it came from the claimed source.'),
    ('CWE-841', 'Improper Enforcement of Behavioral Workflow',
     'Steps in a flow can be skipped, repeated, or reordered for gain. The usual home for a business logic finding.'),
    ('CWE-770', 'Allocation of Resources Without Limits or Throttling',
     'An operation can be repeated or enlarged without limit.'),
    ('CWE-400', 'Uncontrolled Resource Consumption',
     'Input can drive the service into exhausting CPU, memory, or storage.'),
    ('CWE-20', 'Improper Input Validation',
     'Input is not validated before use, and the effect depends on where it lands.'),
    ('CWE-190', 'Integer Overflow or Wraparound',
     'Arithmetic wraps past the range of the type and produces a value the code does not expect.'),
    ('CWE-787', 'Out-of-bounds Write',
     'A write lands outside the bounds of the intended buffer.'),
    ('CWE-125', 'Out-of-bounds Read',
     'A read reaches memory outside the bounds of the intended buffer.'),
    ('CWE-119', 'Improper Restriction of Operations Within Buffer Bounds',
     'An operation reads or writes past the end of a buffer.'),
    ('CWE-416', 'Use After Free',
     'Memory is used after it has been released.'),
    ('CWE-476', 'NULL Pointer Dereference',
     'A null pointer is dereferenced and the process crashes.')
ON CONFLICT (cwe_id) DO NOTHING^^^


-- Reports a reporter has started and not filed.
--
-- A table of its own rather than a DRAFT value on reports: title,
-- vulnerability_information and reported_severity are NOT NULL there, and a
-- half-written draft has none of them. Holding drafts on the reports table
-- would mean dropping those three constraints for every real report, and
-- teaching every query, trigger and notification path to skip a state none of
-- them were written to expect — where one miss puts a draft in a triage queue.
--
-- Last in this file because it needs both severity_enum and
-- report_environment_enum, and both are created above.
DO $$
BEGIN
    IF to_regclass('public.programs') IS NOT NULL
       AND to_regclass('public.user_profiles') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.report_drafts (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            program_id UUID NOT NULL
                REFERENCES public.programs (id) ON DELETE CASCADE,
            reporter_id UUID NOT NULL
                REFERENCES public.user_profiles (id) ON DELETE CASCADE,
            title VARCHAR(255),
            vulnerability_information TEXT,
            impact TEXT,
            steps_to_reproduce TEXT,
            proof_of_concept TEXT,
            remediation_recommendation TEXT,
            target_endpoint VARCHAR(1000),
            environment public.report_environment_enum,
            discovered_at TIMESTAMP(6),
            reference_links JSONB,
            reported_severity public.severity_enum,
            cvss_vector VARCHAR(255),
            cvss_score NUMERIC(3, 1),
            -- Deliberately not foreign keys. A draft outlives a weakness being
            -- retired or an asset leaving scope, and a constraint here would
            -- either block that or take the draft with it. Nothing reads these
            -- until submit, which resolves both and reports a stale one as an
            -- error the reporter can act on.
            weakness_id UUID,
            asset_id UUID,
            created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            updated_at TIMESTAMP(6) NOT NULL DEFAULT now()
        );
    END IF;

    IF to_regclass('public.report_drafts') IS NOT NULL THEN
        -- The CREATE above is skipped entirely on a database where Hibernate
        -- built this table first, taking its column defaults with it. Setting
        -- them here is what makes the two paths agree; the weakness catalog
        -- lost a deploy to exactly this.
        ALTER TABLE public.report_drafts
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.report_drafts
            ALTER COLUMN created_at SET DEFAULT now();
        ALTER TABLE public.report_drafts
            ALTER COLUMN updated_at SET DEFAULT now();

        -- The "continue where you left off" list: one reporter's drafts,
        -- most recently edited first.
        CREATE INDEX IF NOT EXISTS idx_report_drafts_reporter_updated
            ON public.report_drafts (reporter_id, updated_at DESC);

        -- Backs both the per-program listing and the count that caps how many
        -- drafts one reporter can hold against a single program.
        CREATE INDEX IF NOT EXISTS idx_report_drafts_reporter_program
            ON public.report_drafts (reporter_id, program_id);
    END IF;
END
$$^^^


-- Which researchers a company has cleared to report against its programs. Held
-- per organization rather than per program, one row per pair.
--
-- Created here rather than left to Hibernate because the VPS does not run with
-- ddl-auto "update": without this block the entity ships with no table.
DO $$
BEGIN
    IF to_regclass('public.organizations') IS NOT NULL
       AND to_regclass('public.user_profiles') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.organization_researchers (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            organization_id UUID NOT NULL
                REFERENCES public.organizations (id) ON DELETE CASCADE,
            user_id UUID NOT NULL
                REFERENCES public.user_profiles (id) ON DELETE CASCADE,
            status public.researcher_access_status_enum NOT NULL
                DEFAULT 'pending',
            motivation TEXT,
            review_note TEXT,
            requested_at TIMESTAMP(6),
            -- SET NULL, not CASCADE: a reviewer closing their account must not
            -- take every decision they ever made with it.
            reviewed_by UUID
                REFERENCES public.user_profiles (id) ON DELETE SET NULL,
            reviewed_at TIMESTAMP(6),
            revision INTEGER NOT NULL DEFAULT 1,
            created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            updated_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            CONSTRAINT uq_organization_researchers_org_user
                UNIQUE (organization_id, user_id)
        );
    END IF;

    IF to_regclass('public.organization_researchers') IS NOT NULL THEN
        -- The CREATE above is skipped entirely on a database where Hibernate
        -- built this table first, taking its column defaults with it. Setting
        -- them here is what makes the two paths agree; the weakness catalog
        -- lost a deploy to exactly this.
        ALTER TABLE public.organization_researchers
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.organization_researchers
            ALTER COLUMN status
            SET DEFAULT 'pending'::public.researcher_access_status_enum;
        ALTER TABLE public.organization_researchers
            ALTER COLUMN revision SET DEFAULT 1;
        ALTER TABLE public.organization_researchers
            ALTER COLUMN created_at SET DEFAULT now();
        ALTER TABLE public.organization_researchers
            ALTER COLUMN updated_at SET DEFAULT now();

        -- Needed whichever path built the table: the backfill below relies on
        -- it to stay idempotent.
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'uq_organization_researchers_org_user'
        ) THEN
            ALTER TABLE public.organization_researchers
                ADD CONSTRAINT uq_organization_researchers_org_user
                UNIQUE (organization_id, user_id);
        END IF;

        -- The company's review queue, filtered by status.
        CREATE INDEX IF NOT EXISTS idx_organization_researchers_org_status
            ON public.organization_researchers (organization_id, status);

        -- The researcher's own list of companies.
        CREATE INDEX IF NOT EXISTS idx_organization_researchers_user
            ON public.organization_researchers (user_id, updated_at DESC);
    END IF;
END
$$^^^

-- Approval is now required for every program, public ones included, which on an
-- existing database would cut off every researcher mid-engagement. Anyone who
-- has already reported to a company is therefore backfilled as approved; the
-- company can revoke it. Only existing relationships are granted, and
-- ON CONFLICT makes this a no-op on every boot after the first.
DO $$
BEGIN
    IF to_regclass('public.organization_researchers') IS NOT NULL
       AND to_regclass('public.reports') IS NOT NULL
       AND to_regclass('public.programs') IS NOT NULL THEN
        INSERT INTO public.organization_researchers (
            organization_id,
            user_id,
            status,
            review_note,
            requested_at,
            reviewed_at,
            revision,
            created_at,
            updated_at
        )
        SELECT program.organization_id,
               report.reporter_id,
               'approved'::public.researcher_access_status_enum,
               'Approved automatically: this researcher was already reporting '
                   || 'to this organization before approval was required.',
               MIN(report.submitted_at),
               now(),
               1,
               now(),
               now()
        FROM public.reports report
        JOIN public.programs program
            ON program.id = report.program_id
        GROUP BY program.organization_id, report.reporter_id
        ON CONFLICT (organization_id, user_id) DO NOTHING;
    END IF;
END
$$^^^

-- Notification email preferences ------------------------------------------
--
-- Only choices are stored. Someone who has never opened their settings has no
-- row here and is served by NotificationType.emailedByDefault(), so account
-- creation writes nothing and a new notification type takes its default from
-- the enum instead of from whatever a migration guessed for it.
DO $$
BEGIN
    IF to_regclass('public.user_profiles') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.notification_preferences (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id UUID NOT NULL
                REFERENCES public.user_profiles (id) ON DELETE CASCADE,
            notifiable_type VARCHAR(20) NOT NULL,
            email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            updated_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            CONSTRAINT uq_notification_preferences_user_type
                UNIQUE (user_id, notifiable_type)
        );
    END IF;

    IF to_regclass('public.notification_preferences') IS NOT NULL THEN
        -- The CREATE above is skipped on a database where Hibernate built this
        -- table first, taking its column defaults with it. Setting them here is
        -- what makes the two paths agree.
        ALTER TABLE public.notification_preferences
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.notification_preferences
            ALTER COLUMN created_at SET DEFAULT now();
        ALTER TABLE public.notification_preferences
            ALTER COLUMN updated_at SET DEFAULT now();

        -- The settings screen and every email decision read one user's rows.
        CREATE INDEX IF NOT EXISTS idx_notification_preferences_user
            ON public.notification_preferences (user_id);
    END IF;
END
$$^^^

-- Refused uploads ----------------------------------------------------------
--
-- One row per upload a scanner called dangerous. The file is never stored;
-- sha256_hash is what identifies it, and is the value an operator pastes into
-- VirusTotal or a threat feed to see what it was.
--
-- The uploader and organization columns are copies, not foreign keys. An
-- incident has to stay readable after the account that caused it is deleted or
-- renamed -- that is most of what an audit trail is for. A key with ON DELETE
-- CASCADE would erase the record of what somebody did by deleting them, and
-- one without it would block the deletion.
DO $$
BEGIN
    CREATE TABLE IF NOT EXISTS public.security_incidents (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        uploader_user_id UUID NOT NULL,
        uploader_username VARCHAR(255),
        uploader_email VARCHAR(255),
        organization_id UUID,
        organization_name VARCHAR(255),
        report_id UUID,
        filename VARCHAR(255) NOT NULL,
        file_size_bytes BIGINT NOT NULL,
        sha256_hash VARCHAR(64) NOT NULL,
        verdict VARCHAR(20) NOT NULL,
        malicious_engines_count INTEGER NOT NULL DEFAULT 0,
        suspicious_engines_count INTEGER NOT NULL DEFAULT 0,
        total_engines_count INTEGER NOT NULL DEFAULT 0,
        analysis_id VARCHAR(255),
        blocked_at TIMESTAMP(6) NOT NULL DEFAULT now()
    );

    IF to_regclass('public.security_incidents') IS NOT NULL THEN
        -- The CREATE above is skipped on a database where Hibernate built this
        -- table first, taking its column defaults with it. Setting them here is
        -- what makes the two paths agree.
        ALTER TABLE public.security_incidents
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.security_incidents
            ALTER COLUMN blocked_at SET DEFAULT now();

        -- The table is read newest-first, and filtered by company on the
        -- organization's own view.
        CREATE INDEX IF NOT EXISTS idx_security_incidents_blocked_at
            ON public.security_incidents (blocked_at DESC);
        CREATE INDEX IF NOT EXISTS idx_security_incidents_organization_id
            ON public.security_incidents (organization_id);
        CREATE INDEX IF NOT EXISTS idx_security_incidents_uploader_user_id
            ON public.security_incidents (uploader_user_id);
        -- Looking up every other time the same content was seen is the first
        -- thing anybody does with a hash.
        CREATE INDEX IF NOT EXISTS idx_security_incidents_sha256
            ON public.security_incidents (sha256_hash);
    END IF;
END
$$^^^

-- Hacktivity rows without a recognition --------------------------------------
--
-- Every feed entry used to be a recognition, so recognition_id was NOT NULL.
-- A resolution and a disclosure are also things the feed carries now, and
-- neither has a recognition behind it: the report was fixed or published
-- whether or not anybody was credited for it.
--
-- The unique constraint stays. Postgres does not treat NULLs as equal in a
-- unique index, so any number of rows may have none while two recognitions
-- still cannot share one.
DO $$
BEGIN
    IF to_regclass('public.hacktivities') IS NOT NULL THEN
        ALTER TABLE public.hacktivities
            ALTER COLUMN recognition_id DROP NOT NULL;
    END IF;
END
$$^^^

-- Researcher report counters ------------------------------------------------
--
-- total_reports and valid_reports are denormalised onto the profile and are
-- read by the leaderboard, the public profile and the admin user list. Nothing
-- ever wrote them, so every row has carried zero since the columns were added
-- and every one of those screens showed a researcher with no reports.
--
-- The application keeps them current now, on submission and on every triage
-- decision. This brings the rows that predate that back in line.
--
-- Written as a correction rather than a one-shot migration: it only touches
-- rows whose stored counts disagree with the reports themselves, so it is
-- idempotent, costs nothing on a database that is already correct, and repairs
-- any row that drifts later.
DO $$
BEGIN
    IF to_regclass('public.user_profiles') IS NOT NULL
       AND to_regclass('public.reports') IS NOT NULL THEN

        UPDATE public.user_profiles profile
           SET total_reports = counted.total_reports,
               valid_reports = counted.valid_reports
          FROM (
              SELECT candidate.id,
                     count(report.id) AS total_reports,
                     count(report.id) FILTER (
                         WHERE report.state IN ('valid_confirmed', 'resolved')
                     ) AS valid_reports
                FROM public.user_profiles candidate
                LEFT JOIN public.reports report
                       ON report.reporter_id = candidate.id
               GROUP BY candidate.id
          ) counted
         WHERE profile.id = counted.id
           AND (
                   profile.total_reports IS DISTINCT FROM counted.total_reports
                OR profile.valid_reports IS DISTINCT FROM counted.valid_reports
           );
    END IF;
END
$$^^^

-- Profile usernames -------------------------------------------------------
--
-- The handle a profile is shared by. Registration has always collected one and
-- sent it to Keycloak, but never stored it here, so nothing on this side could
-- resolve a profile by name and clients derived a display name from the email
-- instead — a name that existed only in the browser and matched no row.
--
-- Uniqueness is case-insensitive: sengkim and Sengkim reading as two people is
-- how impersonation starts. That needs an index on lower(username), which no
-- JPA annotation expresses, so Hibernate cannot create it and it lives here.
DO $$
BEGIN
    IF to_regclass('public.user_profiles') IS NOT NULL THEN
        ALTER TABLE public.user_profiles
            ADD COLUMN IF NOT EXISTS username VARCHAR(30);
        ALTER TABLE public.user_profiles
            ADD COLUMN IF NOT EXISTS username_changed_at TIMESTAMP(6);
        -- The profile banner. Nullable with no backfill: no cover is the
        -- correct state for every row that predates it.
        ALTER TABLE public.user_profiles
            ADD COLUMN IF NOT EXISTS cover_image_url TEXT;

        -- Rows that predate the column. Derived from the email so the handle
        -- is recognisable to the person who owns it rather than a random
        -- string they log in to find; numbered only where two addresses clean
        -- up to the same thing. username_changed_at stays null, so nobody is
        -- serving a cooldown on a name they never chose.
        WITH ranked AS (
            SELECT id,
                   base,
                   row_number() OVER (
                       PARTITION BY base ORDER BY created_at, id
                   ) AS rn
            FROM (
                SELECT id,
                       created_at,
                       CASE WHEN length(trimmed) >= 3
                            THEN trimmed
                            ELSE 'member'
                       END AS base
                FROM (
                    SELECT id,
                           created_at,
                           regexp_replace(
                               regexp_replace(
                                   left(
                                       regexp_replace(
                                           lower(split_part(email, '@', 1)),
                                           '[^a-z0-9._-]', '', 'g'
                                       ), 30
                                   ),
                                   '^[._-]+', ''
                               ),
                               '[._-]+$', ''
                           ) AS trimmed
                    FROM public.user_profiles
                    WHERE username IS NULL
                ) cleaned
            ) based
        )
        UPDATE public.user_profiles p
           SET username = CASE
                   WHEN r.rn = 1 THEN r.base
                   ELSE regexp_replace(
                            left(r.base, 30 - length(r.rn::text)),
                            '[._-]+$', ''
                        ) || r.rn::text
               END
          FROM ranked r
         WHERE p.id = r.id;

        -- Both guarded rather than asserted: a database that somehow still
        -- holds a null or a duplicate should start and be fixed, not refuse to
        -- boot on the one statement that would have told us about it.
        IF NOT EXISTS (
            SELECT 1 FROM public.user_profiles WHERE username IS NULL
        ) THEN
            ALTER TABLE public.user_profiles
                ALTER COLUMN username SET NOT NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM public.user_profiles
            GROUP BY lower(username)
            HAVING count(*) > 1
        ) THEN
            CREATE UNIQUE INDEX IF NOT EXISTS ux_user_profiles_username_lower
                ON public.user_profiles (lower(username));
        END IF;
    END IF;
END
$$^^^


-- Retest lifecycle ---------------------------------------------------------
--
-- A report goes to 'retesting' when a fix is deployed and the researcher who
-- found the bug is asked to confirm it holds. Kept outside a DO block: ALTER
-- TYPE ... ADD VALUE must not run inside a subtransaction.
ALTER TYPE public.report_state_enum
    ADD VALUE IF NOT EXISTS 'retesting' AFTER 'valid_confirmed'^^^

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE t.typname = 'retest_verdict_enum'
          AND n.nspname = 'public'
    ) THEN
        CREATE TYPE public.retest_verdict_enum AS ENUM (
            'verified_fixed',
            'still_vulnerable'
        );
    END IF;
END
$$^^^

-- One row per round of fix verification.
--
-- A table rather than a pair of columns on reports, because a fix that does
-- not hold is normal: the report goes back to valid_confirmed and the company
-- tries again. Overwriting the last attempt would erase the evidence that the
-- first fix failed, which is what both sides argue from later.
--
-- An attempt is open while completed_at is null, and at most one per report is
-- open at a time -- the application holds that, not a constraint, because
-- closing one is also what triage does when it moves a report on without
-- waiting for the researcher.
DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL
       AND to_regclass('public.user_profiles') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.report_retests (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            report_id UUID NOT NULL
                REFERENCES public.reports (id) ON DELETE CASCADE,
            attempt_number INTEGER NOT NULL DEFAULT 1,
            environment public.report_environment_enum,
            target_endpoint VARCHAR(1000),
            request_notes TEXT,
            bounty_reward NUMERIC(10, 2),
            -- Not ON DELETE CASCADE: deleting the member who asked for a
            -- retest must not delete the record that it was asked for.
            requested_by UUID NOT NULL
                REFERENCES public.user_profiles (id),
            requested_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            -- When the researcher's window to answer runs out. Nullable: rows
            -- written before there was a window have no deadline to miss, and
            -- the expiry sweep skips them rather than lapsing them all at once.
            due_at TIMESTAMP(6),
            verdict public.retest_verdict_enum,
            result_notes TEXT,
            attachment_ids JSONB,
            completed_by UUID
                REFERENCES public.user_profiles (id),
            completed_at TIMESTAMP(6),
            created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            updated_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            CONSTRAINT uq_report_retests_attempt
                UNIQUE (report_id, attempt_number)
        );
    END IF;

    IF to_regclass('public.report_retests') IS NOT NULL THEN
        -- The CREATE above is skipped on a database where Hibernate built this
        -- table first, taking its column defaults with it. Setting them here is
        -- what makes the two paths agree.
        ALTER TABLE public.report_retests
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.report_retests
            ALTER COLUMN attempt_number SET DEFAULT 1;
        ALTER TABLE public.report_retests
            ALTER COLUMN requested_at SET DEFAULT now();
        ALTER TABLE public.report_retests
            ALTER COLUMN created_at SET DEFAULT now();
        ALTER TABLE public.report_retests
            ALTER COLUMN updated_at SET DEFAULT now();

        -- Added after the table shipped, so an existing database reaches the
        -- column this way rather than through the CREATE above.
        ALTER TABLE public.report_retests
            ADD COLUMN IF NOT EXISTS due_at TIMESTAMP(6);

        -- Every read of this table is "the history for one report", and the
        -- open-attempt lookup filters that by completed_at.
        CREATE INDEX IF NOT EXISTS idx_report_retests_report_id
            ON public.report_retests (report_id, attempt_number);

        -- The expiry sweep, which runs hourly against the whole table and on
        -- nearly every run matches nothing. Partial on the open attempts so
        -- the index stays the size of the outstanding work rather than of
        -- every retest ever run.
        CREATE INDEX IF NOT EXISTS idx_report_retests_due_at
            ON public.report_retests (due_at)
            WHERE completed_at IS NULL AND due_at IS NOT NULL;
    END IF;
END
$$^^^


-- Company analytics indexes ---------------------------------------------------
--
-- Every figure on /api/v1/organizations/{id}/analytics is an aggregate over
-- reports, scoped by program and bounded by a window of submitted_at. Without
-- these, one page view is seven sequential scans of the whole report table --
-- and the page has a refresh button and a time-range switcher.
--
-- The composite leads with program_id because the organization filter reaches
-- reports through programs: the planner picks the organization's programs
-- first, then walks this index once per program over the window. The plain
-- submitted_at index above it would make that a filter rather than a range
-- scan.
DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_reports_program_submitted_at
            ON public.reports (program_id, submitted_at);

        -- The asset breakdown groups by the asset a report was filed against.
        -- Partial: asset_id is nullable, and a report that names no in-scope
        -- target appears in no row of that chart.
        CREATE INDEX IF NOT EXISTS idx_reports_asset_id
            ON public.reports (asset_id)
            WHERE asset_id IS NOT NULL;

        -- The researcher leaderboard groups by reporter. Nothing else indexed
        -- this column, so every one of those groupings was a scan.
        CREATE INDEX IF NOT EXISTS idx_reports_reporter_id
            ON public.reports (reporter_id);
    END IF;

    -- Payouts are folded to one row per report before being joined, which is
    -- a grouping over this column on every analytics query that mentions
    -- money.
    IF to_regclass('public.report_rewards') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_report_rewards_report_id
            ON public.report_rewards (report_id);
    END IF;
END
$$^^^

-- Auto-approval switches ------------------------------------------------------
--
-- Whether the review model may publish a kind of post without a moderator. One
-- row per kind, keyed by the kind itself, so there is no way to end up with two
-- rows disagreeing about the same switch.
--
-- Deliberately not seeded. Absent reads as off, so a database that has never
-- heard of this feature -- and one where somebody deleted the row -- behaves
-- exactly as it did before auto-approval existed.
CREATE TABLE IF NOT EXISTS public.auto_approval_settings (
    target      VARCHAR(20) PRIMARY KEY,
    enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by  UUID,
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT now()
)^^^

-- Reporter-named weakness ------------------------------------------------------
--
-- A reporter picks a class from the catalog, says they are not sure, or names
-- one themselves. The third answer lands here rather than in public.weaknesses:
-- the catalog is shared by every program and shown in every picker, so letting
-- a submission write to it fills it with duplicates and typos everybody
-- afterwards has to scroll past. Triage clears this when it settles the class.
DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL THEN
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS suggested_weakness VARCHAR(255);
    END IF;
END
$$^^^

DO $$
BEGIN
    IF to_regclass('public.report_drafts') IS NOT NULL THEN
        ALTER TABLE public.report_drafts
            ADD COLUMN IF NOT EXISTS suggested_weakness VARCHAR(255);
    END IF;
END
$$^^^
-- Report timeline --------------------------------------------------------------
--
-- One row per thing that happened to a report, append-only.
--
-- The reports row only ever holds where things ended up, which is enough to
-- render a report and nowhere near enough to answer how it got there -- the
-- question both sides ask the moment they disagree. A report that was
-- confirmed, paid, reopened by a failed retest and resolved again reads, from
-- that row alone, as a report that was simply resolved. Disputes are decided on
-- exactly that history, so it has to exist somewhere it cannot be edited after
-- the fact.
--
-- actor_id is nullable because the platform itself acts: a retest that lapses
-- on its deadline is closed by the clock, and attributing that to the last
-- person who touched the report would be the one lie this table exists to
-- prevent.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'report_activity_type_enum'
    ) THEN
        CREATE TYPE public.report_activity_type_enum AS ENUM (
            'submitted',
            'state_changed',
            'severity_changed',
            'reward_granted',
            'retest_requested',
            'retest_submitted',
            'retest_expired',
            'disclosure_changed'
        );
    END IF;
END
$$^^^

DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL
       AND to_regclass('public.user_profiles') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.report_activities (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            report_id UUID NOT NULL
                REFERENCES public.reports (id) ON DELETE CASCADE,
            -- Not ON DELETE CASCADE: deleting the member who triaged a report
            -- must not delete the record that it was triaged.
            actor_id UUID
                REFERENCES public.user_profiles (id),
            activity_type public.report_activity_type_enum NOT NULL,
            from_state public.report_state_enum,
            to_state public.report_state_enum,
            severity public.severity_enum,
            detail TEXT,
            created_at TIMESTAMP(6) NOT NULL DEFAULT now()
        );
    END IF;

    IF to_regclass('public.report_activities') IS NOT NULL THEN
        -- The CREATE above is skipped on a database where Hibernate built this
        -- table first, taking its column defaults with it. Setting them here is
        -- what makes the two paths agree.
        ALTER TABLE public.report_activities
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.report_activities
            ALTER COLUMN created_at SET DEFAULT now();

        -- Every read of this table is "the whole history for one report", in
        -- order. Nothing else queries it.
        CREATE INDEX IF NOT EXISTS idx_report_activities_report_id
            ON public.report_activities (report_id, created_at);
    END IF;
END
$$^^^

-- First response ---------------------------------------------------------------
--
-- When anybody but the reporter first acted on the report. The number a
-- researcher judges a program by, and the one thing the row could not answer
-- afterwards: triaged_at is overwritten by every re-triage, so a report
-- answered in an hour and re-triaged a month later looks, from that column
-- alone, like a month of silence.
--
-- Null on every report that existed before this column. A metric over these
-- has to read null as "no data" rather than as zero, so the medians start
-- empty and fill in as reports are answered.
DO $$
BEGIN
    IF to_regclass('public.reports') IS NOT NULL THEN
        ALTER TABLE public.reports
            ADD COLUMN IF NOT EXISTS first_responded_at TIMESTAMP(6);
    END IF;
END
$$^^^

-- Reporter acceptance of a triage severity ------------------------------------
--
-- A severity disagreement is now put to the reporter before it reaches an
-- administrator. It used to open straight into the administrators' queue, which
-- made the platform arbitrate arguments the two sides had not had yet -- most
-- disagreements are one party reading the impact differently, and the reporter
-- agreeing costs nobody anything.
--
-- respond_by is when their window closes. Silence past it settles at the triage
-- severity: a report with no agreed severity cannot be resolved, rewarded or
-- retested, so without a deadline one unresponsive researcher would freeze that
-- report for good.
--
-- Null on every dispute that is not awaiting_reporter, including every dispute
-- raised before this step existed -- the sweep skips those rather than settling
-- them all at once.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'dispute_status_enum'
    ) THEN
        ALTER TYPE public.dispute_status_enum
            ADD VALUE IF NOT EXISTS 'awaiting_reporter';
    END IF;
END
$$^^^

DO $$
BEGIN
    IF to_regclass('public.disputes') IS NOT NULL THEN
        ALTER TABLE public.disputes
            ADD COLUMN IF NOT EXISTS respond_by TIMESTAMP(6);

        -- The hourly sweep, which on nearly every run matches nothing. Partial
        -- so the index stays the size of the outstanding answers rather than of
        -- every dispute ever raised.
        CREATE INDEX IF NOT EXISTS idx_disputes_respond_by
            ON public.disputes (respond_by)
            WHERE respond_by IS NOT NULL;
    END IF;
END
$$^^^

-- Showcase and solution drafts -------------------------------------------------
--
-- Work somebody has started and not posted. Separate tables rather than a DRAFT
-- state on showcases and solutions, for the reason report_drafts is separate:
-- title and overview are NOT NULL on a showcase, and summary, body and approach
-- are all required on a solution -- a draft has none of them after the first
-- keystroke. Carrying drafts on the real tables would mean relaxing those
-- columns for every genuine row and then auditing every listing, feed, search
-- index, count and moderation query to exclude a state they were never written
-- to expect, where anything missed puts unfinished work in front of the public.
--
-- Nothing here is validated beyond a length cap. Autosave has to accept a form
-- mid-keystroke, so the rules live at submit, where the draft is turned into a
-- real request and put through the same validated path a direct post takes.
--
-- category_id and problem_id are plain identifiers rather than foreign keys: a
-- draft can outlive the category being removed or the problem being closed, and
-- a constraint would either block that or delete somebody's unfinished work
-- with it. Submit resolves them through the normal lookups and refuses if they
-- have stopped being valid.
DO $$
BEGIN
    IF to_regclass('public.user_profiles') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS public.showcase_drafts (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            author_id UUID NOT NULL
                REFERENCES public.user_profiles (id) ON DELETE CASCADE,
            category_id UUID,
            title VARCHAR(255),
            overview TEXT,
            cover_image_url VARCHAR(500),
            live_url VARCHAR(500),
            repo_url VARCHAR(500),
            video_url VARCHAR(500),
            tag_ids JSONB,
            tags JSONB,
            created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            updated_at TIMESTAMP(6) NOT NULL DEFAULT now()
        );

        CREATE TABLE IF NOT EXISTS public.solution_drafts (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            author_id UUID NOT NULL
                REFERENCES public.user_profiles (id) ON DELETE CASCADE,
            problem_id UUID NOT NULL,
            summary VARCHAR(250),
            body_markdown TEXT,
            approach_type VARCHAR(20),
            tradeoffs TEXT,
            verification_steps JSONB,
            tested_with JSONB,
            resources JSONB,
            created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
            updated_at TIMESTAMP(6) NOT NULL DEFAULT now()
        );
    END IF;

    IF to_regclass('public.showcase_drafts') IS NOT NULL THEN
        -- The CREATE above is skipped on a database where Hibernate built this
        -- table first, taking its column defaults with it. Setting them here is
        -- what makes the two paths agree.
        ALTER TABLE public.showcase_drafts
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.showcase_drafts
            ALTER COLUMN created_at SET DEFAULT now();
        ALTER TABLE public.showcase_drafts
            ALTER COLUMN updated_at SET DEFAULT now();

        -- Every read is "my drafts, newest edit first". Nothing else queries
        -- this table.
        CREATE INDEX IF NOT EXISTS idx_showcase_drafts_author
            ON public.showcase_drafts (author_id, updated_at DESC);
    END IF;

    IF to_regclass('public.solution_drafts') IS NOT NULL THEN
        ALTER TABLE public.solution_drafts
            ALTER COLUMN id SET DEFAULT gen_random_uuid();
        ALTER TABLE public.solution_drafts
            ALTER COLUMN created_at SET DEFAULT now();
        ALTER TABLE public.solution_drafts
            ALTER COLUMN updated_at SET DEFAULT now();

        CREATE INDEX IF NOT EXISTS idx_solution_drafts_author
            ON public.solution_drafts (author_id, updated_at DESC);

        -- "Have I already started answering this problem?", which the answer
        -- form asks on open.
        CREATE INDEX IF NOT EXISTS idx_solution_drafts_author_problem
            ON public.solution_drafts (author_id, problem_id);
    END IF;
END
$$^^^
