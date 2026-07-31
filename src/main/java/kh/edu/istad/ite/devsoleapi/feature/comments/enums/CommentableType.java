package kh.edu.istad.ite.devsoleapi.feature.comments.enums;

import jakarta.persistence.EnumeratedValue;

public enum CommentableType {
    REPORT("report"),
    SOLUTION("solution"),
    PROGRAM("program"),
    PROBLEM("problem"),
    SHOWCASE("showcase");

    @EnumeratedValue
    private final String databaseValue;

    CommentableType(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
