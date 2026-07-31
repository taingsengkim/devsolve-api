package kh.edu.istad.ite.devsoleapi.feature.userprofile.domain;

import jakarta.persistence.EnumeratedValue;

public enum SocialPlatform {
    GITHUB("github"),
    LINKEDIN("linkedin"),
    WEBSITE("website"),
    X("x"),
    FACEBOOK("facebook"),
    TELEGRAM("telegram"),
    OTHER("other");

    @EnumeratedValue
    private final String databaseValue;

    SocialPlatform(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
