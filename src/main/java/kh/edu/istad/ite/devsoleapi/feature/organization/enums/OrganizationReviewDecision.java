package kh.edu.istad.ite.devsoleapi.feature.organization.enums;

import jakarta.persistence.EnumeratedValue;

public enum OrganizationReviewDecision {
    APPROVED("approved"),
    REJECTED("rejected");

    @EnumeratedValue
    private final String databaseValue;

    OrganizationReviewDecision(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
