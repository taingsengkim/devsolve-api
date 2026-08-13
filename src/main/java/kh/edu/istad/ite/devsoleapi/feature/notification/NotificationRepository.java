package kh.edu.istad.ite.devsoleapi.feature.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    @Query("""
            select notification
            from Notification notification
            where notification.userId = :userId
              and (:unreadOnly = false or notification.read = false)
            """)
    Page<Notification> findInbox(
            @Param("userId") UUID userId,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable
    );

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Notification notification
            set notification.read = true,
                notification.readAt = CURRENT_TIMESTAMP
            where notification.id = :id
              and notification.userId = :userId
              and notification.read = false
            """)
    int markRead(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
            set notification.read = true,
                notification.readAt = CURRENT_TIMESTAMP
            where notification.userId = :userId
              and notification.read = false
            """)
    int markAllRead(@Param("userId") UUID userId);

    /**
     * Returns the IDs of all followers who would receive a bulk notification
     * for the given followable entity, excluding the actor themselves.
     * Used to push SSE events after a dispatchToFollowers call.
     */
    @Query(value = """
            SELECT follow_record.follower_id
            FROM public.follows follow_record
            WHERE follow_record.followable_type = :followableType
              AND follow_record.followable_id = :followableId
              AND follow_record.follower_id <> :actorId
            """, nativeQuery = true)
    List<UUID> findFollowerIds(
            @Param("followableType") String followableType,
            @Param("followableId") UUID followableId,
            @Param("actorId") UUID actorId
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO public.notifications (
                id,
                user_id,
                title,
                content,
                notifiable_type,
                notifiable_id,
                is_read,
                read_at,
                event_key,
                created_at
            )
            SELECT
                gen_random_uuid(),
                follow_record.follower_id,
                :title,
                :content,
                :notifiableType,
                :notifiableId,
                FALSE,
                NULL,
                :eventKey,
                CURRENT_TIMESTAMP
            FROM public.follows follow_record
            WHERE follow_record.followable_type = :followableType
              AND follow_record.followable_id = :followableId
              AND follow_record.follower_id <> :actorId
            ON CONFLICT (user_id, event_key) DO NOTHING
            """, nativeQuery = true)
    int dispatchToFollowers(
            @Param("followableType") String followableType,
            @Param("followableId") UUID followableId,
            @Param("actorId") UUID actorId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("notifiableType") String notifiableType,
            @Param("notifiableId") UUID notifiableId,
            @Param("eventKey") String eventKey
    );
}
