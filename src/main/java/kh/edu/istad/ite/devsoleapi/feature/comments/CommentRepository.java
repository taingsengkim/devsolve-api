package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Two kinds of "gone" run through these queries and they are not the same.
 *
 * <p>{@code deleted_at} means the row is finished: nothing returns it. A
 * comment only reaches that state when nothing hangs off it.
 *
 * <p>{@code removed_at} means the text is gone but the comment still holds its
 * place in the thread, because replies underneath it belong to other people.
 * Those rows are still returned — the mapper blanks the content — so read
 * queries filter on {@code deleted_at} alone, and only the counters that mean
 * "how much was written here" also exclude {@code removed_at}.
 */
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Comments a reader would count on a target: public, still present, and
     * not a tombstone. Tombstones are rendered but they are not content, and
     * a listing claiming "12 comments" that resolves to nine and three empty
     * boxes reads as a bug.
     */
    @Query("""
            select count(comment)
            from Comment comment
            where comment.commentableType = :commentableType
              and comment.commentableId = :commentableId
              and comment.deletedAt is null
              and comment.removedAt is null
              and comment.internal = false
            """)
    long countVisible(
            @Param("commentableType") CommentableType commentableType,
            @Param("commentableId") UUID commentableId
    );

    /**
     * Visible comment counts for a whole page in one round trip. Ids with no
     * comments are absent from the result.
     */
    @Query("""
            select comment.commentableId as id, count(comment) as total
            from Comment comment
            where comment.commentableType = :commentableType
              and comment.commentableId in :commentableIds
              and comment.deletedAt is null
              and comment.removedAt is null
              and comment.internal = false
            group by comment.commentableId
            """)
    List<IdCountProjection> countAllByCommentableIds(
            @Param("commentableType") CommentableType commentableType,
            @Param("commentableIds") Collection<UUID> commentableIds
    );

    Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select comment
            from Comment comment
            where comment.commentableType = :commentableType
              and comment.commentableId = :commentableId
              and comment.parentComment is null
              and comment.deletedAt is null
              and (
                    :includeInternal = true
                    or comment.internal = false
              )
            """)
    Page<Comment> findRootComments(
            @Param("commentableType") CommentableType commentableType,
            @Param("commentableId") UUID commentableId,
            @Param("includeInternal") boolean includeInternal,
            Pageable pageable
    );

    @Query("""
            select comment
            from Comment comment
            where comment.commentableType = :commentableType
              and comment.commentableId = :commentableId
              and comment.parentComment.id = :parentCommentId
              and comment.deletedAt is null
              and (
                    :includeInternal = true
                    or comment.internal = false
              )
            """)
    Page<Comment> findReplies(
            @Param("commentableType") CommentableType commentableType,
            @Param("commentableId") UUID commentableId,
            @Param("parentCommentId") UUID parentCommentId,
            @Param("includeInternal") boolean includeInternal,
            Pageable pageable
    );

    /**
     * Root comments best-first.
     *
     * <p>Scores live in the votes table, so the ordering is a correlated
     * subquery rather than anything the comment row knows. Kept in JPQL
     * despite that: dropping to SQL would mean naming the stored form of
     * {@code commentable_type} in the query, and Hibernate is the only thing
     * that should have an opinion about how an enum is written down.
     *
     * <p>Comments nobody has voted on score zero rather than dropping out, and
     * ties fall back to recency and then id, so the order is total and a page
     * boundary cannot show the same comment twice. The caller's
     * {@link Pageable} supplies paging only; its sort is ignored.
     */
    @Query("""
            select comment
            from Comment comment
            where comment.commentableType = :commentableType
              and comment.commentableId = :commentableId
              and comment.parentComment is null
              and comment.deletedAt is null
              and (
                    :includeInternal = true
                    or comment.internal = false
              )
            order by (
                select coalesce(sum(vote.voteValue), 0)
                from Vote vote
                where vote.votableType = :voteType
                  and vote.votableId = comment.id
            ) desc, comment.createdAt desc, comment.id desc
            """)
    Page<Comment> findRootCommentsByScore(
            @Param("commentableType") CommentableType commentableType,
            @Param("commentableId") UUID commentableId,
            @Param("includeInternal") boolean includeInternal,
            @Param("voteType") VoteType voteType,
            Pageable pageable
    );

    @Query("""
            select comment
            from Comment comment
            where comment.commentableType = :commentableType
              and comment.commentableId = :commentableId
              and comment.parentComment.id = :parentCommentId
              and comment.deletedAt is null
              and (
                    :includeInternal = true
                    or comment.internal = false
              )
            order by (
                select coalesce(sum(vote.voteValue), 0)
                from Vote vote
                where vote.votableType = :voteType
                  and vote.votableId = comment.id
            ) desc, comment.createdAt desc, comment.id desc
            """)
    Page<Comment> findRepliesByScore(
            @Param("commentableType") CommentableType commentableType,
            @Param("commentableId") UUID commentableId,
            @Param("parentCommentId") UUID parentCommentId,
            @Param("includeInternal") boolean includeInternal,
            @Param("voteType") VoteType voteType,
            Pageable pageable
    );

    /**
     * The first few replies under each of a page of root comments, in one
     * query. Without this the thread view is a request per root comment, which
     * is the waterfall the endpoint exists to remove.
     */
    @Query(
            value = """
                    SELECT ranked.id,
                           ranked.commentable_type,
                           ranked.commentable_id,
                           ranked.parent_comment_id,
                           ranked.author_id,
                           ranked.content,
                           ranked.is_internal,
                           ranked.edited_at,
                           ranked.removed_at,
                           ranked.removed_by,
                           ranked.removal_reason,
                           ranked.created_at,
                           ranked.updated_at,
                           ranked.deleted_at
                    FROM (
                        SELECT comment.*,
                               ROW_NUMBER() OVER (
                                   PARTITION BY comment.parent_comment_id
                                   ORDER BY comment.created_at ASC, comment.id ASC
                               ) AS position
                        FROM comments comment
                        WHERE comment.parent_comment_id IN (:parentIds)
                          AND comment.deleted_at IS NULL
                          AND (:includeInternal = TRUE OR comment.is_internal = FALSE)
                    ) ranked
                    WHERE ranked.position <= :replyLimit
                    ORDER BY ranked.parent_comment_id, ranked.position
                    """,
            nativeQuery = true
    )
    List<Comment> findLeadingReplies(
            @Param("parentIds") Collection<UUID> parentIds,
            @Param("includeInternal") boolean includeInternal,
            @Param("replyLimit") int replyLimit
    );

    @Query("""
            select comment
            from Comment comment
            where comment.authorId = :authorId
              and comment.deletedAt is null
              and comment.removedAt is null
              and comment.internal = false
              and (
                    :commentableType is null
                    or comment.commentableType = :commentableType
              )
            """)
    Page<Comment> findMine(
            @Param("authorId") UUID authorId,
            @Param("commentableType") CommentableType commentableType,
            Pageable pageable
    );

    /**
     * Tombstones are counted here on purpose: the reply is still rendered, so
     * "3 replies" over a list of three boxes stays true even when one of them
     * has been removed.
     */
    @Query("""
            select reply.parentComment.id, count(reply.id)
            from Comment reply
            where reply.parentComment.id in :parentIds
              and reply.deletedAt is null
              and (
                    :includeInternal = true
                    or reply.internal = false
              )
            group by reply.parentComment.id
            """)
    List<Object[]> countActiveReplies(
            @Param("parentIds") Collection<UUID> parentIds,
            @Param("includeInternal") boolean includeInternal
    );

    /**
     * Whether anything is still hanging off this comment, which decides
     * whether deleting it can take the row with it or has to leave a
     * tombstone. Internal replies count: they are somebody's writing too.
     */
    @Query("""
            select count(reply)
            from Comment reply
            where reply.parentComment.id = :parentCommentId
              and reply.deletedAt is null
            """)
    long countLiveChildren(@Param("parentCommentId") UUID parentCommentId);

    /**
     * A double-submitted form, a retried request, or the crudest kind of spam
     * all look the same from here: the same person posting the same text to
     * the same place moments apart.
     */
    @Query("""
            select count(comment) > 0
            from Comment comment
            where comment.authorId = :authorId
              and comment.commentableType = :commentableType
              and comment.commentableId = :commentableId
              and comment.content = :content
              and comment.deletedAt is null
              and comment.createdAt >= :since
            """)
    boolean existsRecentDuplicate(
            @Param("authorId") UUID authorId,
            @Param("commentableType") CommentableType commentableType,
            @Param("commentableId") UUID commentableId,
            @Param("content") String content,
            @Param("since") LocalDateTime since
    );

    @Query("""
            select count(comment)
            from Comment comment
            where comment.authorId = :authorId
              and comment.createdAt >= :since
            """)
    long countByAuthorSince(
            @Param("authorId") UUID authorId,
            @Param("since") LocalDateTime since
    );

    /**
     * Everybody who has written in a thread and is still standing behind it.
     * Authors of tombstoned comments are left out: they took their words back,
     * which is a reasonable thing to read as wanting out of the conversation.
     */
    @Query("""
            select distinct comment.authorId
            from Comment comment
            where comment.deletedAt is null
              and comment.removedAt is null
              and (
                    comment.id = :rootCommentId
                    or comment.parentComment.id = :rootCommentId
              )
            """)
    List<UUID> findThreadParticipants(
            @Param("rootCommentId") UUID rootCommentId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Comment comment
            set comment.deletedAt = :deletedAt,
                comment.updatedAt = :deletedAt
            where comment.id = :commentId
              and comment.deletedAt is null
            """)
    int softDelete(
            @Param("commentId") UUID commentId,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
