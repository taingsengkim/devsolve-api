ALTER TABLE public.programs
    ADD COLUMN IF NOT EXISTS rules_of_engagement JSONB;

ALTER TABLE public.programs
    ADD COLUMN IF NOT EXISTS exclusions JSONB;

UPDATE public.programs
SET rules_of_engagement = jsonb_build_object(
        'description',
        'Follow the program policy while testing in-scope assets.',
        'rules',
        jsonb_build_array('Review and follow the program policy before testing.')
    )
WHERE rules_of_engagement IS NULL;

UPDATE public.programs
SET exclusions = jsonb_build_object(
        'description',
        'Review the program policy for program-specific exclusions.',
        'rules',
        jsonb_build_array('Activities prohibited by the program policy are out of scope.')
    )
WHERE exclusions IS NULL;

ALTER TABLE public.programs
    ALTER COLUMN rules_of_engagement SET NOT NULL;

ALTER TABLE public.programs
    ALTER COLUMN exclusions SET NOT NULL;

ALTER TABLE public.programs
    ADD CONSTRAINT ck_program_rules_of_engagement_object
        CHECK (
            jsonb_typeof(rules_of_engagement) = 'object'
            AND jsonb_typeof(rules_of_engagement -> 'description') = 'string'
            AND btrim(rules_of_engagement ->> 'description') <> ''
            AND CASE
                WHEN jsonb_typeof(rules_of_engagement -> 'rules') = 'array'
                    THEN jsonb_array_length(
                            rules_of_engagement -> 'rules'
                    ) > 0
                ELSE FALSE
            END
        );

ALTER TABLE public.programs
    ADD CONSTRAINT ck_program_exclusions_object
        CHECK (
            jsonb_typeof(exclusions) = 'object'
            AND jsonb_typeof(exclusions -> 'description') = 'string'
            AND btrim(exclusions ->> 'description') <> ''
            AND CASE
                WHEN jsonb_typeof(exclusions -> 'rules') = 'array'
                    THEN jsonb_array_length(exclusions -> 'rules') > 0
                ELSE FALSE
            END
        );
