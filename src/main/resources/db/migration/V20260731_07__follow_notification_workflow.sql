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
          AND table_record.relname = 'follows'
          AND constraint_record.contype = 'c'
          AND pg_get_constraintdef(constraint_record.oid)
                ILIKE '%followable_type%'
    LOOP
        EXECUTE format(
                'ALTER TABLE public.follows DROP CONSTRAINT %I',
                constraint_name
        );
    END LOOP;
END
$$;

UPDATE public.follows
SET followable_type = LOWER(followable_type)
WHERE followable_type <> LOWER(followable_type);

DELETE FROM public.follows
WHERE followable_type NOT IN (
    'organization',
    'user',
    'problem',
    'program',
    'showcase'
);

DELETE FROM public.follows duplicate
USING public.follows retained
WHERE duplicate.follower_id = retained.follower_id
  AND duplicate.followable_type = retained.followable_type
  AND duplicate.followable_id = retained.followable_id
  AND duplicate.id > retained.id;

ALTER TABLE public.follows
    ADD CONSTRAINT follows_followable_type_check
    CHECK (
        followable_type IN (
            'organization',
            'user',
            'problem',
            'program',
            'showcase'
        )
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_follows_follower_followable
    ON public.follows (
        follower_id,
        followable_type,
        followable_id
    );

CREATE INDEX IF NOT EXISTS idx_follows_followable
    ON public.follows (followable_type, followable_id);

CREATE INDEX IF NOT EXISTS idx_follows_follower_created
    ON public.follows (follower_id, created_at DESC);

ALTER TABLE public.notifications
    ADD COLUMN IF NOT EXISTS event_key VARCHAR(255);

UPDATE public.notifications
SET event_key = 'legacy:' || id::TEXT
WHERE event_key IS NULL;

ALTER TABLE public.notifications
    ALTER COLUMN event_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_event
    ON public.notifications (user_id, event_key);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created_at
    ON public.notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_notifiable
    ON public.notifications (notifiable_type, notifiable_id);

DROP INDEX IF EXISTS public.idx_notifications_user_unread;

CREATE INDEX idx_notifications_user_unread
    ON public.notifications (user_id, created_at DESC)
    WHERE is_read = FALSE;
