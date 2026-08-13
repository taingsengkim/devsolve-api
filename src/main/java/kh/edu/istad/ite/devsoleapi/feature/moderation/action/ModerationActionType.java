package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

public enum ModerationActionType {

    /** Recorded in history only; the account keeps its current status. */
    WARN,

    /** Temporary. Requires an expiry, after which the account self-reinstates. */
    SUSPEND,

    /** Permanent removal for policy violations. */
    REMOVE,

    /** Permanent removal for abuse or fraud. */
    BAN,

    /** Reverses SUSPEND, REMOVE or BAN and returns the account to ACTIVE. */
    REINSTATE
}
