package kh.edu.istad.ite.devsoleapi.common.listing;

import java.time.LocalDateTime;

/**
 * How a public feed is ordered.
 *
 * <p>Shared by showcases and problems because the two listings are the same
 * product surface wearing different content, and users who learn "Top" on one
 * should not find it missing or renamed on the other.
 *
 * <p>{@link #NEWEST} stays the default everywhere. It is the only ordering
 * that cannot be gamed and the only one that is meaningful on a young feed
 * with barely any votes on it.
 */
public enum ListingSort {

    NEWEST,

    OLDEST,

    /**
     * Highest vote score first, over everything ever published. Stable, and
     * therefore slowly becomes a hall of fame rather than a feed.
     */
    TOP,

    /**
     * Highest vote score first, but only among recently published entries, so
     * something posted today can still reach the front. The cheap version of a
     * decay function: no arithmetic on timestamps, just a window.
     */
    TRENDING,

    MOST_VIEWED,

    TITLE;

    /**
     * How far back {@link #TRENDING} looks. A month is long enough that a
     * quiet week does not empty the feed, short enough that the list still
     * turns over.
     */
    private static final int TRENDING_WINDOW_DAYS = 30;

    public boolean isScoreOrdered() {
        return this == TOP || this == TRENDING;
    }

    /**
     * The earliest publication date this ordering will consider, or null when
     * it considers everything.
     */
    public LocalDateTime windowStart() {
        return this == TRENDING
                ? LocalDateTime.now().minusDays(TRENDING_WINDOW_DAYS)
                : null;
    }
}
