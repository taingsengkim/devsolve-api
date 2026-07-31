CREATE INDEX IF NOT EXISTS ix_comments_target_thread_active
    ON public.comments (
        commentable_type,
        commentable_id,
        parent_comment_id,
        created_at DESC
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_comments_author_active
    ON public.comments (author_id, updated_at DESC)
    WHERE deleted_at IS NULL;
