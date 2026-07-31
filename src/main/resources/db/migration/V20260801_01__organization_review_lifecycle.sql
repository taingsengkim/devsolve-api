ALTER TABLE public.organizations
    ADD COLUMN IF NOT EXISTS reviewed_by UUID,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
    ADD COLUMN IF NOT EXISTS submission_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS verification_email_sent_at TIMESTAMP;

ALTER TABLE public.organizations
    DROP CONSTRAINT IF EXISTS ck_organizations_submission_version;

ALTER TABLE public.organizations
    ADD CONSTRAINT ck_organizations_submission_version
    CHECK (submission_version >= 1);

CREATE TABLE IF NOT EXISTS public.organization_review_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL
        REFERENCES public.organizations(id) ON DELETE CASCADE,
    submission_version INTEGER NOT NULL,
    decision VARCHAR(20) NOT NULL,
    reviewer_id UUID NOT NULL,
    reason TEXT,
    reviewed_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_organization_review_history_version
        CHECK (submission_version >= 1),
    CONSTRAINT ck_organization_review_history_decision
        CHECK (decision IN ('approved', 'rejected')),
    CONSTRAINT ck_organization_review_history_reason
        CHECK (decision <> 'rejected' OR NULLIF(BTRIM(reason), '') IS NOT NULL),
    CONSTRAINT uq_organization_review_history_submission
        UNIQUE (organization_id, submission_version)
);

CREATE INDEX IF NOT EXISTS idx_organization_review_history_reviewed
    ON public.organization_review_history (
        organization_id,
        reviewed_at DESC
    );
