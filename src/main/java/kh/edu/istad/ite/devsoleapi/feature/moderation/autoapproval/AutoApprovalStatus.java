package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

/**
 * Where a post stands with the automatic check, as one word for a client to
 * branch on.
 *
 * <p>Derived from a stored {@link ContentAutoReview} rather than stored beside
 * it: the pair of columns the check actually writes — approved, and a hold
 * category when it did not — already says all three of these, and a fourth
 * column repeating them is a fourth column that can disagree with them.
 */
public enum AutoApprovalStatus {

    /** The check cleared it, and it was published without a moderator. */
    APPROVED,

    /**
     * The check read it and left it for a moderator. {@code hold} says which
     * of the three reasons, and there is something to tell the author.
     */
    HELD,

    /**
     * The check did not run — switched off, no model configured, out of quota,
     * or an answer that could not be read. Says nothing about the post; it is
     * waiting on a moderator exactly as it would have before any of this
     * existed.
     */
    NOT_CHECKED;

    public static AutoApprovalStatus of(ContentAutoReview review) {
        if (review.isApproved()) {
            return APPROVED;
        }
        return review.getHold() != null
                && review.getHold().isAboutTheSubmission()
                ? HELD
                : NOT_CHECKED;
    }
}
