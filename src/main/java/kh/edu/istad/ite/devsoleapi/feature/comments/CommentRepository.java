package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
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

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    long countByCommentableTypeAndCommentableIdAndDeletedAtIsNullAndInternalFalse(
            CommentableType commentableType,
            UUID commentableId
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

    @Query("""
            select comment
            from Comment comment
            where comment.authorId = :authorId
              and comment.deletedAt is null
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            WITH RECURSIVE comment_tree AS (
                SELECT id
                FROM comments
                WHERE id = :commentId
                  AND deleted_at IS NULL

                UNION ALL

                SELECT child.id
                FROM comments child
                JOIN comment_tree parent
                  ON child.parent_comment_id = parent.id
                WHERE child.deleted_at IS NULL
            )
            UPDATE comments
            SET deleted_at = :deletedAt,
                updated_at = :deletedAt
            WHERE id IN (SELECT id FROM comment_tree)
            """, nativeQuery = true)
    int softDeleteThread(
            @Param("commentId") UUID commentId,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
