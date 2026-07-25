package kh.edu.istad.ite.devsoleapi.feature.organization.enums;

import jakarta.persistence.EnumeratedValue;

public enum OrganizationStatus {
    PENDING("pending"),
    ACTIVE("active"),
    REJECTED("rejected");

    @EnumeratedValue
    private final String databaseValue;

    OrganizationStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
