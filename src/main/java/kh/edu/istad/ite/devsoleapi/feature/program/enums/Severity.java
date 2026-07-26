package kh.edu.istad.ite.devsoleapi.feature.program.enums;

import jakarta.persistence.EnumeratedValue;

public enum Severity {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    @EnumeratedValue
    private final String databaseValue;

    Severity(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
