package kh.edu.istad.ite.devsoleapi.feature.program.enums;

import jakarta.persistence.EnumeratedValue;

public enum EngagementType {
    BOUNTY("bounty"),
    RESPONSE("response");

    @EnumeratedValue
    private final String databaseValue;

    EngagementType(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
