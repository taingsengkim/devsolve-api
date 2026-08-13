package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {

    @Query("""
            SELECT moderationAction
            FROM ModerationAction moderationAction
            WHERE (
                    :targetType IS NULL
                    OR moderationAction.targetType = :targetType
              )
              AND (
                    :targetId IS NULL
                    OR moderationAction.targetId = :targetId
              )
              AND (
                    :actionType IS NULL
                    OR moderationAction.action = :actionType
              )
            """)
    Page<ModerationAction> searchHistory(
            @Param("targetType")
            ModerationTargetType targetType,
            @Param("targetId") UUID targetId,
            @Param("actionType")
            ModerationActionType actionType,
            Pageable pageable
    );

    /**
     * Expiry timestamps for a user's moderation actions of one type, newest
     * first. Used to decide whether a suspension has already lapsed; pass a
     * one-element {@link Pageable} to read only the most recent.
     */
    @Query("""
            SELECT moderationAction.expiresAt
            FROM ModerationAction moderationAction
            WHERE moderationAction.targetId = :targetId
              AND moderationAction.targetType = :targetType
              AND moderationAction.action = :actionType
            ORDER BY moderationAction.createdAt DESC
            """)
    List<LocalDateTime> findExpiryTimestamps(
            @Param("targetId") UUID targetId,
            @Param("targetType")
            ModerationTargetType targetType,
            @Param("actionType")
            ModerationActionType actionType,
            Pageable pageable
    );
}
