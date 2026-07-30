CREATE TABLE IF NOT EXISTS public.showcase_review_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    showcase_id UUID NOT NULL,
    revision_id UUID,
    submission_type VARCHAR(20) NOT NULL,
    category_id UUID,
    title VARCHAR(255) NOT NULL,
    overview TEXT NOT NULL,
    cover_image_url VARCHAR(500),
    live_url VARCHAR(500),
    repo_url VARCHAR(500),
    video_url VARCHAR(500),
    review_status VARCHAR(20) NOT NULL,
    submitted_by UUID NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    reviewed_by UUID NOT NULL,
    reviewed_at TIMESTAMP NOT NULL,
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_showcase_review_history_submission_type
        CHECK (submission_type IN ('INITIAL', 'REVISION')),
    CONSTRAINT chk_showcase_review_history_review_status
        CHECK (review_status IN ('APPROVED', 'REJECTED')),
    CONSTRAINT fk_showcase_review_history_submitted_by
        FOREIGN KEY (submitted_by)
        REFERENCES public.user_profiles (id),
    CONSTRAINT fk_showcase_review_history_reviewed_by
        FOREIGN KEY (reviewed_by)
        REFERENCES public.user_profiles (id)
);

CREATE INDEX IF NOT EXISTS idx_showcase_review_history_showcase_reviewed
    ON public.showcase_review_history (
        showcase_id,
        reviewed_at DESC
    );
