package kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums;

import jakarta.persistence.EnumeratedValue;

public enum ResearcherAccessStatus {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    REVOKED("revoked");

    @EnumeratedValue
    private final String databaseValue;

    ResearcherAccessStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
