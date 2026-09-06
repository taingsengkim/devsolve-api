package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

/**
 * Where the reported content stands, in the one vocabulary the queue uses.
 *
 * <p>The five flaggable kinds each have their own status enum — a problem is
 * {@code PENDING_APPROVAL}, a showcase is {@code PENDING}, a comment has no
 * status column at all — and a moderator deciding what to do about a report is
 * asking one question of all of them: can people see this right now? Handing
 * the frontend five vocabularies would put that translation in the client,
 * five times over.
 *
 * <p>Reduced deliberately. A problem that is {@code RESOLVED} or {@code CLOSED}
 * still reads as {@link #PUBLISHED} here, because it is still on the site.
 */
public enum FlagTargetStatus {

    /** Live. Anybody can read it. */
    PUBLISHED,

    /** Waiting on a moderator, and not yet visible. */
    PENDING,

    /** Never submitted. Only its author has seen it. */
    DRAFT,

    /** A moderator turned it down when it was submitted. */
    REJECTED,

    /** Taken down, or tombstoned in place the way a replied-to comment is. */
    REMOVED,

    /** Soft-deleted, or the row is gone entirely and nothing is left to show. */
    DELETED
}
