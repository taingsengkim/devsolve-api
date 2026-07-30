CREATE TABLE IF NOT EXISTS public.showcase_step_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    revision_id UUID NOT NULL,
    source_step_id UUID,
    step_number INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    code_snippet TEXT,
    image_url VARCHAR(500),
    diagram_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_showcase_step_revisions_order
        UNIQUE (revision_id, step_number),
    CONSTRAINT uq_showcase_step_revisions_source
        UNIQUE (revision_id, source_step_id),
    CONSTRAINT fk_showcase_step_revisions_revision
        FOREIGN KEY (revision_id)
        REFERENCES public.showcase_revisions (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_showcase_step_revisions_revision
    ON public.showcase_step_revisions (revision_id);
