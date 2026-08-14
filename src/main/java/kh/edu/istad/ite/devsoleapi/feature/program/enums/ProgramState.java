package kh.edu.istad.ite.devsoleapi.feature.program.enums;

import jakarta.persistence.EnumeratedValue;

/**
 * The organization-facing lifecycle of a program. {@link #DRAFT} represents
 * authoring or explicit relaunch work and, while not deleted, cannot pair with
 * {@link SubmissionState#PENDING_REVIEW}; submitting promotes it to
 * {@link #ACTIVE} while admin approval remains a separate requirement.
 */
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
