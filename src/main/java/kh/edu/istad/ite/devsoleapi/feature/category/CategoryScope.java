package kh.edu.istad.ite.devsoleapi.feature.category;

import jakarta.persistence.EnumeratedValue;

public enum CategoryScope {
    PROBLEM("problem"),
    SHOWCASE("showcase");

    @EnumeratedValue
    private final String databaseValue;

    CategoryScope(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
