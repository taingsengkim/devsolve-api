package kh.edu.istad.ite.devsoleapi.feature.reports.enums;

import jakarta.persistence.EnumeratedValue;

public enum DisputeStatus {
    OPEN("open"),
    UNDER_REVIEW("under_review"),
    RESOLVED("resolved"),
    DISMISSED("dismissed"),

    /**
     * Triage rated the finding differently from the reporter, and the reporter
     * has been asked whether they accept it.
     *
     * <p>Before {@link #OPEN}, not after. Most disagreements are somebody
     * reading the impact differently rather than a fight, and sending every one
     * of them straight to an administrator makes the platform arbitrate
     * arguments the two sides had not had yet. Accepting settles it here;
     * refusing is what opens the dispute proper.
     *
     * <p>Declared last to match where {@code schema.sql} appends it to
     * {@code dispute_status_enum} — Postgres sorts the column by that order.
     */
    AWAITING_REPORTER("awaiting_reporter");

    @EnumeratedValue
    private final String databaseValue;

    DisputeStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
