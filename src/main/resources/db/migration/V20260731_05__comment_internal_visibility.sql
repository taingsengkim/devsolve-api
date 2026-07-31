ALTER TABLE public.comments
    ADD COLUMN IF NOT EXISTS is_internal BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT constraint_record.conname
        FROM pg_constraint constraint_record
        JOIN pg_class table_record
          ON table_record.oid = constraint_record.conrelid
        JOIN pg_namespace schema_record
          ON schema_record.oid = table_record.relnamespace
        WHERE schema_record.nspname = 'public'
          AND table_record.relname = 'comments'
          AND constraint_record.contype = 'c'
          AND pg_get_constraintdef(constraint_record.oid)
              ILIKE '%commentable_type%'
    LOOP
        EXECUTE format(
                'ALTER TABLE public.comments DROP CONSTRAINT %I',
                constraint_name
        );
    END LOOP;
END
$$;

UPDATE public.comments
SET commentable_type = LOWER(commentable_type)
WHERE commentable_type <> LOWER(commentable_type);

UPDATE public.comments
SET is_internal = FALSE
WHERE is_internal = TRUE
  AND commentable_type <> 'report';

UPDATE public.comments AS child
SET parent_comment_id = NULL
FROM public.comments AS parent
WHERE child.parent_comment_id = parent.id
  AND child.is_internal <> parent.is_internal;

ALTER TABLE public.comments
    ADD CONSTRAINT comments_commentable_type_check
    CHECK (
        commentable_type IN (
            'report',
            'solution',
            'program',
            'problem',
            'showcase'
        )
    );

ALTER TABLE public.comments
    DROP CONSTRAINT IF EXISTS comments_internal_report_check;

ALTER TABLE public.comments
    ADD CONSTRAINT comments_internal_report_check
    CHECK (is_internal = FALSE OR commentable_type = 'report');
