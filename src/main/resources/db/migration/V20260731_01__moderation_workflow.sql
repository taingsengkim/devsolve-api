ALTER TABLE public.content_flags
    ADD COLUMN IF NOT EXISTS resolution_note TEXT;

CREATE INDEX IF NOT EXISTS idx_content_flags_status_created
    ON public.content_flags (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_content_flags_reporter_created
    ON public.content_flags (reporter_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_content_flags_target
    ON public.content_flags (flaggable_type, flaggable_id);

CREATE INDEX IF NOT EXISTS idx_moderation_actions_target_created
    ON public.moderation_actions (
        target_type,
        target_id,
        created_at DESC
    );

CREATE INDEX IF NOT EXISTS idx_moderation_actions_action_created
    ON public.moderation_actions (action, created_at DESC);
