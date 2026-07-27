package kh.edu.istad.ite.devsoleapi.feature.reports.enums;

import jakarta.persistence.EnumeratedValue;

public enum DisclosureStatus {
    NOT_DISCLOSED("not_disclosed"),
    PENDING_DISCLOSURE("pending_disclosure"),
    DISCLOSED("disclosed");

    @EnumeratedValue
    private final String databaseValue;

    DisclosureStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
