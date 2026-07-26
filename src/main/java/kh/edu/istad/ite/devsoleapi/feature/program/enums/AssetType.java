package kh.edu.istad.ite.devsoleapi.feature.program.enums;

import jakarta.persistence.EnumeratedValue;

public enum AssetType {
    URL("url"),
    WILDCARD("wildcard"),
    IP_RANGE("ip_range"),
    MOBILE_APP("mobile_app"),
    API("api"),
    SOURCE_CODE("source_code"),
    HARDWARE("hardware"),
    OTHER("other");

    @EnumeratedValue
    private final String databaseValue;

    AssetType(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
