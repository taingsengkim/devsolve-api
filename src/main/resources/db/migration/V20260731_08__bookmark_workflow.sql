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
          AND table_record.relname = 'bookmarks'
          AND constraint_record.contype = 'c'
          AND pg_get_constraintdef(constraint_record.oid)
                ILIKE '%bookmarkable_type%'
    LOOP
        EXECUTE format(
                'ALTER TABLE public.bookmarks DROP CONSTRAINT %I',
                constraint_name
        );
    END LOOP;
END
$$;

UPDATE public.bookmarks
SET bookmarkable_type = LOWER(bookmarkable_type)
WHERE bookmarkable_type <> LOWER(bookmarkable_type);

DELETE FROM public.bookmarks
WHERE bookmarkable_type NOT IN (
    'program',
    'problem',
    'solution',
    'showcase'
);

DELETE FROM public.bookmarks duplicate
USING public.bookmarks retained
WHERE duplicate.user_id = retained.user_id
  AND duplicate.bookmarkable_type = retained.bookmarkable_type
  AND duplicate.bookmarkable_id = retained.bookmarkable_id
  AND duplicate.id > retained.id;

ALTER TABLE public.bookmarks
    ADD CONSTRAINT bookmarks_bookmarkable_type_check
    CHECK (
        bookmarkable_type IN (
            'program',
            'problem',
            'solution',
            'showcase'
        )
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_bookmarks_user_bookmarkable
    ON public.bookmarks (
        user_id,
        bookmarkable_type,
        bookmarkable_id
    );

CREATE INDEX IF NOT EXISTS idx_bookmarks_user_created
    ON public.bookmarks (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bookmarks_bookmarkable
    ON public.bookmarks (bookmarkable_type, bookmarkable_id);
