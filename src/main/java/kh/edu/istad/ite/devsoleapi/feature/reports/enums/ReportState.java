package kh.edu.istad.ite.devsoleapi.feature.reports.enums;

import jakarta.persistence.EnumeratedValue;

public enum ReportState {
    NEW("new"),
    TRIAGING("triaging"),
    NEEDS_MORE_INFO("needs_more_info"),
    VALID_CONFIRMED("valid_confirmed"),
    /**
     * A fix is deployed and the researcher has been asked to re-run their proof
     * of concept against it.
     *
     * <p>Distinct from {@link #NEEDS_MORE_INFO}, which means triage cannot yet
     * judge the report as written. Here the finding is agreed and understood —
     * what is outstanding is confirmation from the person who found it that the
     * fix actually holds.
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
