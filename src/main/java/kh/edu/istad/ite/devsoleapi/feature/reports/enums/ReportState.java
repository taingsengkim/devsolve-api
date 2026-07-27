package kh.edu.istad.ite.devsoleapi.feature.reports.enums;

import jakarta.persistence.EnumeratedValue;

public enum ReportState {
    NEW("new"),
    TRIAGING("triaging"),
    NEEDS_MORE_INFO("needs_more_info"),
    VALID_CONFIRMED("valid_confirmed"),
    RESOLVED("resolved"),
    REJECTED("rejected"),
    DUPLICATE("duplicate");

    @EnumeratedValue
    private final String databaseValue;

    ReportState(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
