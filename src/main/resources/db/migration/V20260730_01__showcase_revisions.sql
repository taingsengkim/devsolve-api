CREATE TABLE IF NOT EXISTS public.showcase_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    showcase_id UUID NOT NULL UNIQUE,
    category_id UUID,
    title VARCHAR(255) NOT NULL,
    overview TEXT NOT NULL,
    cover_image_url VARCHAR(500),
    live_url VARCHAR(500),
    repo_url VARCHAR(500),
    video_url VARCHAR(500),
    review_status VARCHAR(20) NOT NULL,
    submitted_by UUID NOT NULL,
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_showcase_revisions_review_status
        CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_showcase_revisions_showcase
        FOREIGN KEY (showcase_id)
        REFERENCES public.showcases (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_showcase_revisions_category
        FOREIGN KEY (category_id)
        REFERENCES public.categories (id),
    CONSTRAINT fk_showcase_revisions_submitted_by
        FOREIGN KEY (submitted_by)
        REFERENCES public.user_profiles (id),
    CONSTRAINT fk_showcase_revisions_reviewed_by
        FOREIGN KEY (reviewed_by)
        REFERENCES public.user_profiles (id)
);

CREATE INDEX IF NOT EXISTS idx_showcase_revisions_review_status
    ON public.showcase_revisions (review_status);
