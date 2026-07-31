package kh.edu.istad.ite.devsoleapi.feature.vote;

import jakarta.persistence.EnumeratedValue;

public enum VoteType {
    PROBLEM("problem"),
    SOLUTION("solution"),
    COMMENT("comment"),
    SHOWCASE("showcase");

    @EnumeratedValue
    private final String databaseValue;

    VoteType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
