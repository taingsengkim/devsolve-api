package kh.edu.istad.ite.devsoleapi.feature.reports.enums;

import jakarta.persistence.EnumeratedValue;

/**
 * What kind of thing happened to a report.
 *
 * <p>Kept coarse. A timeline is read, not queried, and every extra type is one
 * more thing a client has to know how to render — so a type earns its place by
 * being something a reader would look for by name, not by being a distinct line
 * of code that wrote it.
 *
 * <p>Declared in the order {@code schema.sql} adds them to
 * {@code report_activity_type_enum}. Postgres sorts the column by that order,
 * so moving one here without moving it there would have the two disagree.
 */
public enum ReportActivityType {

    SUBMITTED("submitted"),
    STATE_CHANGED("state_changed"),
    SEVERITY_CHANGED("severity_changed"),
    REWARD_GRANTED("reward_granted"),
    RETEST_REQUESTED("retest_requested"),
    RETEST_SUBMITTED("retest_submitted"),
    RETEST_EXPIRED("retest_expired"),
    DISCLOSURE_CHANGED("disclosure_changed");

    @EnumeratedValue
    private final String databaseValue;

    ReportActivityType(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
