ALTER TABLE public.programs
    ADD COLUMN IF NOT EXISTS proof_of_concept_requirements TEXT;
