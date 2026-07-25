package kh.edu.istad.ite.devsoleapi.feature.organization.enums;

import jakarta.persistence.EnumeratedValue;

public enum Industry {
    TECHNOLOGY("technology"),
    FINANCE("finance"),
    HEALTHCARE("healthcare"),
    ECOMMERCE("ecommerce"),
    GOVERNMENT("government"),
    EDUCATION("education"),
    OTHER("other");

    @EnumeratedValue
    private final String databaseValue;

    Industry(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
