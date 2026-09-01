package kh.edu.istad.ite.devsoleapi.feature.reports.enums;

import jakarta.persistence.EnumeratedValue;

public enum ReportState {
    NEW("new"),
    TRIAGING("triaging"),
    NEEDS_MORE_INFO("needs_more_info"),
    VALID_CONFIRMED("valid_confirmed"),
    /**
     * The organization resolved the report, and the researcher has been asked
     * to re-run their proof of concept against the deployed fix.
     *
     * <p>Reached from {@link #RESOLVED} rather than on the way to it. Resolving
     * is the organization's claim that it fixed the vulnerability; a retest is
     * the person who found it checking that claim. Confirming leaves the report
     * resolved, and still being able to reproduce it reopens the report to
     * {@link #VALID_CONFIRMED}.
     *
     * <p>Distinct from {@link #NEEDS_MORE_INFO}, which means triage cannot yet
     * judge the report as written. Here the finding is agreed and understood —
     * what is outstanding is whether the fix holds.
     *
     * <p>Declared here, before RESOLVED, only to match where schema.sql adds it
     * to {@code report_state_enum}. That position is what Postgres sorts the
     * column by, and moving one without the other would have the two disagree.
     */
    RETESTING("retesting"),
    RESOLVED("resolved"),
    REJECTED("rejected"),
    DUPLICATE("duplicate");

    @EnumeratedValue
    private final String databaseValue;

    ReportState(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
