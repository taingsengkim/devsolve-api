package kh.edu.istad.ite.devsoleapi.feature.reports.enums;

import jakarta.persistence.EnumeratedValue;

/**
 * Where the reporter observed the finding. A bug reproduced against production
 * and the same bug reproduced against a local build are not worth the same
 * money, and triage cannot tell them apart from the write-up alone.
 */
public enum ReportEnvironment {
    PRODUCTION("production"),
    STAGING("staging"),
    DEVELOPMENT("development"),
    TESTING("testing"),
    LOCAL("local");

    @EnumeratedValue
    private final String databaseValue;

    ReportEnvironment(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
