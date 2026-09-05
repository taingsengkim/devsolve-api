package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

/**
 * What the person asking has already done with this showcase, and what they may
 * do next.
 *
 * <p>Every field here depends on who is holding the request, so none of it can
 * be cached and all of it is computed per request. A signed-out reader gets the
 * all-false shape from {@link #anonymous()} rather than a null block, so the
 * frontend renders one set of controls either way and simply finds them
 * inactive.
 *
 * @param vote            {@code "UP"}, {@code "DOWN"}, or null when the viewer
 *                        has not voted. A string rather than the vote's stored
 *                        {@code short}, so a client never has to know that 1
 *                        and -1 are the two legal values
 * @param editUnderReview the author has edits waiting on a moderator, so the
 *                        page they are looking at is not the newest version
 *                        they wrote. Only ever true for the author: nobody else
 *                        is told an edit is pending
 */
public record ShowcaseViewerResponse(
        String vote,
        boolean bookmarked,
        boolean following,
        boolean followingAuthor,
        boolean owner,
        boolean canEdit,
        boolean canDelete,
        boolean editUnderReview
) {

    public static ShowcaseViewerResponse anonymous() {
        return new ShowcaseViewerResponse(
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }
}
