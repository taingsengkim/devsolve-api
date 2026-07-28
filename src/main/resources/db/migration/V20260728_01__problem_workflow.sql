-- DevSolve Problem workflow alignment.
-- This migration intentionally preserves existing rows and does not infer a
-- category scope for legacy data.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type type
        JOIN pg_namespace namespace
          ON namespace.oid = type.typnamespace
        WHERE type.typname = 'category_scope_enum'
          AND namespace.nspname = 'public'
    ) THEN
        CREATE TYPE public.category_scope_enum AS ENUM (
            'problem',
            'showcase'
        );
    END IF;
END
$$;

ALTER TABLE public.categories
    ADD COLUMN IF NOT EXISTS scope public.category_scope_enum;

DO $$
DECLARE
    legacy_constraint record;
BEGIN
    FOR legacy_constraint IN
        SELECT constraint_row.conname
        FROM pg_constraint constraint_row
        WHERE constraint_row.conrelid = 'public.categories'::regclass
          AND constraint_row.contype = 'u'
          AND (
              SELECT array_agg(attribute.attname ORDER BY key_column.ordinality)
              FROM unnest(constraint_row.conkey)
                   WITH ORDINALITY AS key_column(attnum, ordinality)
              JOIN pg_attribute attribute
                ON attribute.attrelid = constraint_row.conrelid
               AND attribute.attnum = key_column.attnum
          ) = ARRAY['slug']::name[]
    LOOP
        EXECUTE format(
                'ALTER TABLE public.categories DROP CONSTRAINT %I',
                legacy_constraint.conname
        );
    END LOOP;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_categories_scope_slug
    ON public.categories (scope, slug);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.categories'::regclass
          AND conname = 'uk_categories_scope_slug'
    ) THEN
        ALTER TABLE public.categories
            ADD CONSTRAINT uk_categories_scope_slug
            UNIQUE USING INDEX uk_categories_scope_slug;
    END IF;
END
$$;

-- Until legacy rows are explicitly scoped, retain their former global
-- uniqueness and avoid creating ambiguous null-scope slugs.
CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_legacy_slug
    ON public.categories (slug)
    WHERE scope IS NULL;

DO $$
DECLARE
    oversized_title_count bigint;
BEGIN
    SELECT count(*)
    INTO oversized_title_count
    FROM public.problems
    WHERE char_length(title) > 180;

    IF oversized_title_count > 0 THEN
        RAISE EXCEPTION
            'Problem migration stopped: % existing title(s) exceed 180 characters. Shorten or explicitly map those rows, then rerun the migration.',
            oversized_title_count;
    END IF;
END
$$;

ALTER TABLE public.problems
    ALTER COLUMN title TYPE VARCHAR(180),
    ALTER COLUMN description TYPE TEXT,
    ALTER COLUMN view_count TYPE BIGINT
        USING view_count::BIGINT;

ALTER TABLE public.problems
    ADD COLUMN IF NOT EXISTS sdlc_phase VARCHAR(40),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE public.problems
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE public.problems
    ALTER COLUMN status TYPE VARCHAR(30)
        USING upper(status::text);

ALTER TABLE public.problems
    ALTER COLUMN status SET DEFAULT 'DRAFT',
    ALTER COLUMN status SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'problems'
          AND column_name = 'deleted_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE public.problems
            ALTER COLUMN deleted_at TYPE TIMESTAMPTZ
                USING deleted_at AT TIME ZONE 'UTC';
    END IF;
END
$$;

ALTER TABLE public.problems
    DROP CONSTRAINT IF EXISTS ck_problems_status;

ALTER TABLE public.problems
    ADD CONSTRAINT ck_problems_status CHECK (
        status IN (
            'DRAFT',
            'PENDING_APPROVAL',
            'PUBLISHED',
            'RESOLVED',
            'CLOSED',
            'REJECTED'
        )
    );

ALTER TABLE public.problems
    DROP CONSTRAINT IF EXISTS ck_problems_sdlc_phase;

ALTER TABLE public.problems
    ADD CONSTRAINT ck_problems_sdlc_phase CHECK (
        sdlc_phase IS NULL OR sdlc_phase IN (
            'PLANNING',
            'REQUIREMENTS_ANALYSIS',
            'DESIGN',
            'DEVELOPMENT',
            'TESTING',
            'DEPLOYMENT',
            'MAINTENANCE'
        )
    );

ALTER TABLE public.problems
    DROP CONSTRAINT IF EXISTS ck_problems_title_length;

ALTER TABLE public.problems
    ADD CONSTRAINT ck_problems_title_length CHECK (
        char_length(title) BETWEEN 10 AND 180
    ) NOT VALID;

ALTER TABLE public.problems
    DROP CONSTRAINT IF EXISTS ck_problems_view_count;

ALTER TABLE public.problems
    ADD CONSTRAINT ck_problems_view_count CHECK (view_count >= 0);

CREATE INDEX IF NOT EXISTS idx_problems_public_feed
    ON public.problems (published_at DESC)
    WHERE deleted_at IS NULL
      AND status IN ('PUBLISHED', 'RESOLVED', 'CLOSED');

CREATE INDEX IF NOT EXISTS idx_problems_author_active
    ON public.problems (author_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS public.problem_tech_stack (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    version VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_problem_tech_stack_problem
        FOREIGN KEY (problem_id)
        REFERENCES public.problems (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_problem_tech_stack_name
        CHECK (char_length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_problem_tech_stack_version
        CHECK (
            version IS NULL
            OR char_length(btrim(version)) BETWEEN 1 AND 50
        )
);

CREATE INDEX IF NOT EXISTS idx_problem_tech_stack_problem_id
    ON public.problem_tech_stack (problem_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_problem_tech_stack_identity
    ON public.problem_tech_stack (
        problem_id,
        lower(name),
        lower(coalesce(version, ''))
    );

CREATE TABLE IF NOT EXISTS public.problem_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id UUID NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_problem_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_problem_attachments_problem
        FOREIGN KEY (problem_id)
        REFERENCES public.problems (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_problem_attachments_uploaded_by
        FOREIGN KEY (uploaded_by)
        REFERENCES public.user_profiles (id),
    CONSTRAINT ck_problem_attachments_size
        CHECK (size_bytes BETWEEN 1 AND 10485760)
);

CREATE INDEX IF NOT EXISTS idx_problem_attachments_problem_id
    ON public.problem_attachments (problem_id);

-- Some development databases were created incrementally by Hibernate and do
-- not yet contain the shared tag tables. Flyway runs before Hibernate, so
-- create the canonical shared tables here when they are absent.
CREATE TABLE IF NOT EXISTS public.tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    slug VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    usage_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tags_name UNIQUE (name),
    CONSTRAINT uq_tags_slug UNIQUE (slug),
    CONSTRAINT ck_tags_usage_count CHECK (usage_count >= 0)
);

CREATE TABLE IF NOT EXISTS public.problem_tags (
    problem_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_problem_tags PRIMARY KEY (problem_id, tag_id),
    CONSTRAINT fk_problem_tags_problem
        FOREIGN KEY (problem_id)
        REFERENCES public.problems (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_problem_tags_tag
        FOREIGN KEY (tag_id)
        REFERENCES public.tags (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_problem_tags_tag_id
    ON public.problem_tags (tag_id);

DO $$
DECLARE
    duplicate_slug_count bigint;
BEGIN
    SELECT count(*)
    INTO duplicate_slug_count
    FROM (
        SELECT lower(slug)
        FROM public.tags
        GROUP BY lower(slug)
        HAVING count(*) > 1
    ) duplicate_slugs;

    IF duplicate_slug_count > 0 THEN
        RAISE EXCEPTION
            'Problem migration stopped: tags contain case-insensitive duplicate slugs. Merge those tags before rerunning the migration.';
    END IF;
END
$$;

UPDATE public.tags
SET slug = lower(slug)
WHERE slug <> lower(slug);

ALTER TABLE public.tags
    DROP CONSTRAINT IF EXISTS ck_tags_lowercase_slug;

ALTER TABLE public.tags
    ADD CONSTRAINT ck_tags_lowercase_slug
    CHECK (slug = lower(slug));

COMMENT ON COLUMN public.categories.scope IS
    'Legacy rows remain NULL until manually mapped to problem or showcase.';

COMMENT ON COLUMN public.problem_attachments.storage_key IS
    'Internal generated object-storage key. Never expose directly through the API.';
