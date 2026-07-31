CREATE INDEX IF NOT EXISTS idx_solutions_problem_status_created
    ON public.solutions (
        problem_id,
        review_status,
        created_at
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_solutions_author_updated
    ON public.solutions (
        author_id,
        updated_at DESC
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_solutions_review_queue
    ON public.solutions (
        review_status,
        created_at
    )
    WHERE deleted_at IS NULL;
