package kh.edu.istad.ite.devsoleapi.feature.comments.enums;

/**
 * Why a comment is showing as a tombstone rather than its text.
 *
 * <p>The distinction is for the reader, not for us: "the author deleted this"
 * and "a moderator removed this" mean very different things to somebody
 * following a thread, and collapsing them into one grey box invites the
 * assumption that every gap was a moderator.
 */
public enum CommentRemovalReason {
    AUTHOR,
    MODERATOR
}
