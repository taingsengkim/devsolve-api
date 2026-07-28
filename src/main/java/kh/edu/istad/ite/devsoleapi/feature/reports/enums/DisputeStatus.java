package kh.edu.istad.ite.devsoleapi.feature.reports.enums;

import jakarta.persistence.EnumeratedValue;

public enum DisputeStatus {
    OPEN("open"),
    UNDER_REVIEW("under_review"),
    RESOLVED("resolved"),
    DISMISSED("dismissed");

    @EnumeratedValue
    private final String databaseValue;

    DisputeStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
