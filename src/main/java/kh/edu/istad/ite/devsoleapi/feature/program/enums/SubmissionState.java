package kh.edu.istad.ite.devsoleapi.feature.program.enums;

import jakarta.persistence.EnumeratedValue;

/**
 * Where a program sits in the admin review pipeline, which is a separate
 * question from {@link ProgramState} — the lifecycle the organization drives.
 *
 * <p>{@code NOT_SUBMITTED} is the state a program is authored in: the
 * organization is still editing and has not handed it to an administrator.
 * Without it a draft would have to claim {@code PENDING_REVIEW}, putting
 * unfinished work in the review queue and paging every admin about it.
 */
public enum SubmissionState {
    NOT_SUBMITTED("not_submitted"),
    PENDING_REVIEW("pending_review"),
    APPROVED("approved"),
    REJECTED("rejected");

    @EnumeratedValue
    private final String databaseValue;

    SubmissionState(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
