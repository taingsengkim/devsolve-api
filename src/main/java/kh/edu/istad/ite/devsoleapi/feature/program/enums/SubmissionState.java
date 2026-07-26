package kh.edu.istad.ite.devsoleapi.feature.program.enums;

import jakarta.persistence.EnumeratedValue;

public enum SubmissionState {
    PENDING_REVIEW("pending_review"),
    APPROVED("approved"),
    REJECTED("rejected");

    @EnumeratedValue
    private final String databaseValue;

    SubmissionState(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
