CREATE INDEX IF NOT EXISTS idx_reports_program_reporter
    ON public.reports (program_id, reporter_id);
