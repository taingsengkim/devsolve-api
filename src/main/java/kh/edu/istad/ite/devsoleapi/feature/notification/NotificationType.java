package kh.edu.istad.ite.devsoleapi.feature.notification;

import jakarta.persistence.EnumeratedValue;

public enum NotificationType {
    COMMENT("comment"),
    REPORT("report"),
    PROGRAM("program"),
    SOLUTION("solution"),
    PROBLEM("problem"),
    KYC("kyc"),
    ORGANIZATION("organization"),
    INVITATION("invitation"),
    DISPUTE("dispute"),
    RECOGNITION("recognition"),
    SHOWCASE("showcase");

    @EnumeratedValue
    private final String databaseValue;

    NotificationType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
