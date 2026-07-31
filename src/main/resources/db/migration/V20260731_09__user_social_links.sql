CREATE TABLE IF NOT EXISTS public.user_social_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    platform VARCHAR(20) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    CONSTRAINT user_social_links_platform_check
        CHECK (
            platform IN (
                'github',
                'linkedin',
                'website',
                'x',
                'facebook',
                'telegram',
                'other'
            )
        ),
    CONSTRAINT fk_user_social_links_user
        FOREIGN KEY (user_id)
        REFERENCES public.user_profiles(id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_social_links_user_platform
    ON public.user_social_links (user_id, platform);

CREATE INDEX IF NOT EXISTS idx_user_social_links_user
    ON public.user_social_links (user_id);
