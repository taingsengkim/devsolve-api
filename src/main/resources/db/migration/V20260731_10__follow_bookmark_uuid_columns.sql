DO $$
DECLARE
    invalid_row_count BIGINT;
    uuid_pattern CONSTANT TEXT :=
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
BEGIN
    IF to_regclass('public.bookmarks') IS NOT NULL
       AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'bookmarks'
              AND column_name IN ('id', 'bookmarkable_id')
              AND data_type <> 'uuid'
       ) THEN
        SELECT COUNT(*)
        INTO invalid_row_count
        FROM public.bookmarks
        WHERE (id IS NOT NULL AND id::TEXT !~* uuid_pattern)
           OR (
                bookmarkable_id IS NOT NULL
                AND bookmarkable_id::TEXT !~* uuid_pattern
           );

        IF invalid_row_count > 0 THEN
            RAISE EXCEPTION
                'Cannot convert bookmarks UUID columns: % invalid row(s)',
                invalid_row_count;
        END IF;

        ALTER TABLE public.bookmarks
            ALTER COLUMN id DROP DEFAULT,
            ALTER COLUMN id TYPE UUID USING id::TEXT::UUID,
            ALTER COLUMN id SET DEFAULT gen_random_uuid(),
            ALTER COLUMN bookmarkable_id TYPE UUID
                USING bookmarkable_id::TEXT::UUID;
    END IF;

    IF to_regclass('public.follows') IS NOT NULL
       AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'follows'
              AND column_name IN ('id', 'followable_id')
              AND data_type <> 'uuid'
       ) THEN
        SELECT COUNT(*)
        INTO invalid_row_count
        FROM public.follows
        WHERE (id IS NOT NULL AND id::TEXT !~* uuid_pattern)
           OR (
                followable_id IS NOT NULL
                AND followable_id::TEXT !~* uuid_pattern
           );

        IF invalid_row_count > 0 THEN
            RAISE EXCEPTION
                'Cannot convert follows UUID columns: % invalid row(s)',
                invalid_row_count;
        END IF;

        ALTER TABLE public.follows
            ALTER COLUMN id DROP DEFAULT,
            ALTER COLUMN id TYPE UUID USING id::TEXT::UUID,
            ALTER COLUMN id SET DEFAULT gen_random_uuid(),
            ALTER COLUMN followable_id TYPE UUID
                USING followable_id::TEXT::UUID;
    END IF;
END
$$;
