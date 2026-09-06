package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

/**
 * The orders a moderator works the queue in.
 *
 * <p>A closed set rather than a free-text {@code sort=field,direction}: the two
 * useful orderings are not columns of the flag table — one counts sibling
 * reports and the other reads the oldest timestamp of a group — so there is no
 * field name to hand over anyway, and a queue is not a place to let a caller
 * name columns in raw SQL.
 */
public enum FlagQueueSort {

    /** Newest report first. What a moderator watching the queue wants. */
    NEWEST,

    /** Longest waiting first. What clearing a backlog wants. */
    OLDEST,

    /**
     * Most reported first. The nearest thing to urgency the platform records:
     * five people reporting one post is a stronger signal than any single
     * report, whoever wrote it.
     */
    MOST_REPORTED
}
