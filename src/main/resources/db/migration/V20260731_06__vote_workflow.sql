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
          AND table_record.relname = 'votes'
          AND constraint_record.contype = 'c'
          AND (
                pg_get_constraintdef(constraint_record.oid)
                    ILIKE '%votable_type%'
                OR pg_get_constraintdef(constraint_record.oid)
                    ILIKE '%vote_value%'
          )
    LOOP
        EXECUTE format(
                'ALTER TABLE public.votes DROP CONSTRAINT %I',
                constraint_name
        );
    END LOOP;
END
$$;

UPDATE public.votes
SET votable_type = LOWER(votable_type)
WHERE votable_type <> LOWER(votable_type);

DELETE FROM public.votes
WHERE vote_value NOT IN (-1, 1)
   OR votable_type NOT IN (
        'problem',
        'solution',
        'comment',
        'showcase'
   );

ALTER TABLE public.votes
    ADD CONSTRAINT votes_votable_type_check
    CHECK (
        votable_type IN (
            'problem',
            'solution',
            'comment',
            'showcase'
        )
    );

ALTER TABLE public.votes
    ADD CONSTRAINT votes_value_check
    CHECK (vote_value IN (-1, 1));

CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_user_votable
    ON public.votes (user_id, votable_type, votable_id);

CREATE INDEX IF NOT EXISTS idx_votes_votable
    ON public.votes (votable_type, votable_id);

CREATE INDEX IF NOT EXISTS idx_votes_user_created
    ON public.votes (user_id, created_at DESC);
