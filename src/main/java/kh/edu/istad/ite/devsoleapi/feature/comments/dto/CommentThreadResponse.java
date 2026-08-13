package kh.edu.istad.ite.devsoleapi.feature.comments.dto;

import java.util.List;

/**
 * A root comment together with the opening of its reply thread.
 *
 * <p>Rendering a discussion from the paged endpoints costs one request for the
 * roots and then one per root for its replies, so the page arrives in two
 * visible stages. This returns the whole first screen at once; the paged reply
 * endpoint is still there for "show more".
 *
 * @param hasMoreReplies whether {@link #replyCount()} exceeds what is in
 *                       {@link #replies()}, so the client can decide about a
 *                       "show more" control without comparing sizes itself.
 */
public record CommentThreadResponse(
        CommentResponse comment,
        List<CommentResponse> replies,
        long replyCount,
        boolean hasMoreReplies
) {
}
