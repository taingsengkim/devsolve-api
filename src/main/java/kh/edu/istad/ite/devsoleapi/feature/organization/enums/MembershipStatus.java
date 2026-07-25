package kh.edu.istad.ite.devsoleapi.feature.organization.enums;

import jakarta.persistence.EnumeratedValue;

public enum MembershipStatus {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    REMOVED("removed");

    @EnumeratedValue
    private final String databaseValue;

    MembershipStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
