package kh.edu.istad.ite.devsoleapi.feature.follow;

import kh.edu.istad.ite.devsoleapi.feature.notification.Notification;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationResponseMapper;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowNotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;
    private final NotificationResponseMapper notificationResponseMapper;

    /**
     * Bulk-inserts a notification for every follower of the given entity
     * (excluding the actor), then pushes an SSE event to any follower
     * who currently has an open connection.
     *
     * @return the number of notification rows actually inserted
     */
    @Transactional
    public int notifyFollowers(
            FollowType followedType,
            UUID followedId,
            UUID actorId,
            String title,
            String content,
            NotificationType notificationType,
            UUID notificationTargetId,
            String eventKey
    ) {
        // 1. Bulk-insert notifications via native SQL (ON CONFLICT DO NOTHING)
        int inserted = notificationRepository.dispatchToFollowers(
                followedType.databaseValue(),
                followedId,
                actorId,
                title,
                content,
                notificationType.databaseValue(),
                notificationTargetId,
                eventKey
        );

        // 2. Fetch the full set of follower IDs (same filter, regardless of
        //    whether the row was already present) and push SSE to connected ones.
        if (inserted > 0) {
            List<UUID> followerIds = notificationRepository.findFollowerIds(
                    followedType.databaseValue(),
                    followedId,
                    actorId
            );

            // Build a lightweight SSE payload (no DB ID since we used bulk insert)
            NotificationResponse payload = notificationResponseMapper
                    .toResponse(Notification.builder()
                            // The native bulk insert does not return row IDs.
                            .id(null)
                            .title(title)
                            .content(content)
                            .notifiableType(notificationType)
                            .notifiableId(notificationTargetId)
                            .read(false)
                            .createdAt(LocalDateTime.now())
                            .build()
            );

            sseEmitterService.pushToMany(followerIds, payload);
            log.debug(
                    "Dispatched follower notification '{}' to {} users, SSE pushed to connected subset",
                    eventKey, followerIds.size()
            );
        }

        return inserted;
    }
}
