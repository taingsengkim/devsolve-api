package kh.edu.istad.ite.devsoleapi.feature.follow;

import jakarta.persistence.EnumeratedValue;

public enum FollowType {
    ORGANIZATION("organization"),
    USER("user"),
    PROBLEM("problem"),
    PROGRAM("program"),
    SHOWCASE("showcase");

    @EnumeratedValue
    private final String databaseValue;

    FollowType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
