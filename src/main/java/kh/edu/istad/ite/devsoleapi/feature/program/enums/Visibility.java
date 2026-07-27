package kh.edu.istad.ite.devsoleapi.feature.program.enums;

import jakarta.persistence.EnumeratedValue;

public enum Visibility {
    PUBLIC("public"),
    PRIVATE("private"),
    INVITE_ONLY("invite_only");

    @EnumeratedValue
    private final String databaseValue;

    Visibility(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
