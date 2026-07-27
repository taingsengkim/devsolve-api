package kh.edu.istad.ite.devsoleapi.feature.organization.enums;

import jakarta.persistence.EnumeratedValue;

public enum OrgRole {
    MANAGER("manager"),
    MEMBER("member"),
    VIEWER("viewer");

    @EnumeratedValue
    private final String databaseValue;

    OrgRole(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
