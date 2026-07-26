package kh.edu.istad.ite.devsoleapi.feature.program.enums;

import jakarta.persistence.EnumeratedValue;

public enum ProgramState {
    DRAFT("draft"),
    ACTIVE("active"),
    PAUSED("paused"),
    CLOSED("closed");

    @EnumeratedValue
    private final String databaseValue;

    ProgramState(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
