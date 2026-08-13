package kh.edu.istad.ite.devsoleapi.feature.comments.dto;

import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentRemovalReason;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CommentResponse {

    private UUID id;

    private CommentableType commentableType;

    private UUID commentableId;

    private UUID parentCommentId;

    private UUID authorId;

    private String authorName;

    private String authorAvatarUrl;

    /**
     * Null once the comment is removed. The row survives so replies keep their
     * place, but the text does not come back, and sending it with a "please
     * do not show this" flag beside it would put the whole guarantee in the
     * client's hands.
     */
    private String content;

    private boolean internal;

    private long replyCount;

    /**
     * Net score and its two halves, loaded with the page rather than left for
     * the client to fetch per comment.
     */
    private long voteScore;

    private long upvoteCount;

    private long downvoteCount;

    /**
     * The reader's own vote: 1, -1, or null when they have not voted or are
     * not signed in.
     */
    private Short myVote;

    private boolean edited;

    private LocalDateTime editedAt;

    private boolean removed;

    private CommentRemovalReason removalReason;

    /**
     * Whether this reader may edit or delete this comment. Computed here
     * because the client would otherwise have to re-derive rules it cannot
     * see — author identity, admin role, whether the comment is already a
     * tombstone — and would get them subtly wrong.
     */
    private boolean canEdit;

    private boolean canDelete;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
