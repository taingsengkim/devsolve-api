package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import jakarta.persistence.EnumeratedValue;

public enum BookmarkType {
    PROGRAM("program"),
    PROBLEM("problem"),
    SOLUTION("solution"),
    SHOWCASE("showcase");

    @EnumeratedValue
    private final String databaseValue;

    BookmarkType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
