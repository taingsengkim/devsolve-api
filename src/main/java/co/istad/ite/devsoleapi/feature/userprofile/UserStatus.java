package co.istad.ite.devsoleapi.feature.userprofile;

import jakarta.persistence.EnumeratedValue;

public enum UserStatus {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    REMOVED("removed");

    @EnumeratedValue
    private final String databaseValue;

    UserStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
