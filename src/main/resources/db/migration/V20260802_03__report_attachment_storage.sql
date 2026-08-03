DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'report_attachments'
          AND column_name = 'file_url'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'report_attachments'
          AND column_name = 'storage_key'
    ) THEN
        ALTER TABLE public.report_attachments
            RENAME COLUMN file_url TO storage_key;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_attachments_storage_key
    ON public.report_attachments (storage_key);

CREATE INDEX IF NOT EXISTS idx_report_attachments_report_created
    ON public.report_attachments (report_id, created_at);
