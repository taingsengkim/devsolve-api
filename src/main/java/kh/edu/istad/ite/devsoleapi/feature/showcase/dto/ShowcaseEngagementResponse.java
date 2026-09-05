package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

/**
 * What everyone else has done with a showcase.
 *
 * <p>Votes, bookmarks and followers were all reachable already — the showcase
 * is a valid target for each — but a detail page had no way to show them
 * without a request per counter, so a reader saw an upvote button that could
 * not say how many upvotes there were.
 *
 * <p>The up and down halves ride alongside the net score because they are not
 * recoverable from it: a score of zero is one thing when nobody voted and
 * another when fifty people disagreed.
 */
public record ShowcaseEngagementResponse(
        long voteScore,
        long upvoteCount,
        long downvoteCount,
        long bookmarkCount,
        long followerCount
) {

    public static ShowcaseEngagementResponse empty() {
        return new ShowcaseEngagementResponse(0L, 0L, 0L, 0L, 0L);
    }
}
