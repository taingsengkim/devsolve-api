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
     * One page of the leaderboard, keyed by ranking window as well as by page
     * and size — the windows are different boards, not different views of one.
     * Nothing evicts it: reputation moves on votes, accepted reports and
     * recognitions, so a short TTL is more honest than trying to catch every
     * path.
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

    /**
     * One page of the unfiltered public program listing, keyed by ordering and
     * page. Filtered pages are left uncached for the same reason as the
     * showcase listing, and there are eleven filters to combine here.
     */
    public static final String PROGRAM_LISTING = "program-listing";

    /**
     * A whole public program response, keyed by program id. Unlike
     * {@link #SHOWCASE_DETAIL} this holds the counts too: the expensive part of
     * a program detail is the aggregate over reports, so caching only the
     * stable half would cache the cheap queries and leave the costly ones.
     * Every program write evicts; the TTL bounds the counts alone.
     */
    public static final String PROGRAM_DETAIL = "program-detail";

    /**
     * One page of the unfiltered problem feed, holding responses with their
     * viewer state left blank.
     *
     * <p>A problem response carries who voted, who bookmarked and who may edit
     * it. None of that can go in a shared cache, so what is cached here is the
     * search and the associations — author, category, tags, technologies,
     * attachments — and {@code ProblemResponseEnricher} fills the rest in per
     * request. The counts come from the enricher too: it has to run anyway for
     * the viewer flags, and reads them in the same queries.
     */
    public static final String PROBLEM_LISTING = "problem-listing";

    /** One problem's viewer-independent half, keyed by problem id. */
    public static final String PROBLEM_DETAIL = "problem-detail";

    /**
     * What a model decided about one problem draft, keyed by a digest of the
     * draft and the exact candidates it was shown.
     *
     * <p>The only cache here that exists to save money rather than time. An
     * author checking a draft, editing a word and checking again is the normal
     * way to use the endpoint behind it, and each of those is a metered call.
     *
     * <p>Nothing evicts it and nothing needs to: the key already covers
     * everything the answer depends on, so a change on either side is a new
     * key rather than a stale entry.
     */
    public static final String PROBLEM_DUPLICATE_REVIEW =
            "problem-duplicate-review";

    private CacheNames() {
    }
}
