package kh.edu.istad.ite.devsoleapi.common.cache;

/**
 * The names of the Redis caches.
 *
 * <p>Constants rather than string literals because a cache name is a contract
 * between the {@code @Cacheable} that fills it, the {@code @CacheEvict} that
 * clears it and the configuration that gives it a TTL — and misspelling it in
 * any one of them fails silently.
 */
public final class CacheNames {

    /**
     * Every category listing, keyed by which listing and which scope. Dropped
     * whole on any write: it holds a handful of entries, and a category edit
     * invalidates all of them anyway.
     */
    public static final String CATEGORIES = "categories";

    /**
     * One page of the leaderboard. Nothing evicts it: reputation moves on
     * votes, accepted reports and recognitions, so a short TTL is more honest
     * than trying to catch every path.
     */
    public static final String LEADERBOARD = "leaderboard";

    /**
     * The tags and steps of one showcase, keyed by showcase id. Holds nothing
     * viewer-specific and nothing off the showcase row itself, so it is the
     * same bytes for everyone and cannot go stale against a moderation change.
     */
    public static final String SHOWCASE_DETAIL = "showcase-detail";

    /**
     * One page of the unfiltered showcase listing. Searched and filtered pages
     * are deliberately not cached: each filter combination is its own key, read
     * once, so they would fill Redis without ever being hit again.
     *
     * <p>Evicted whole: the key is {@code sort:page:size}, so a showcase
     * joining or leaving shifts an unknown set of pages across every sort.
     */
    public static final String SHOWCASE_LISTING = "showcase-listing";

    /**
     * The same pages under a vote- or view-ordered sort. Split out because
     * those orderings move on reads and votes, which nothing evicts, so they
     * need a short TTL while the rest of the listing can hold for a day.
     */
    public static final String SHOWCASE_LISTING_RANKED =
            "showcase-listing-ranked";

    private CacheNames() {
    }
}
