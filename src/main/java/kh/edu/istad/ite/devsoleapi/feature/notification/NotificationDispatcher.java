package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;
    private final NotificationResponseMapper notificationResponseMapper;

    /**
     * Delivering the same event twice is a no-op, not a failure. The
     * (user_id, event_key) unique constraint is what guarantees a user is
     * never told the same thing twice, but on its own it turns the second
     * attempt into an exception that rolls back whatever transaction the
     * caller was in — so a retried listener, or an admin re-approving
     * something already approved, would take the business operation down with
     * it. The event key is the caller's promise that two dispatches describe
     * the same event; this honours it quietly.
     *
     * @return the notification, or null if this event was already delivered
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification dispatch(UUID userId, String title, String content, NotificationType notifiableType, UUID notifiableId, String eventKey) {
        if (eventKey != null
                && notificationRepository.existsByUserIdAndEventKey(userId, eventKey)) {
            log.debug("Skipping already delivered notification {}", eventKey);
            return null;
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .notifiableType(notifiableType)
                .notifiableId(notifiableId)
                .eventKey(eventKey)
                .read(false)
                .build();

        notification = notificationRepository.save(notification);
        NotificationResponse response = notificationResponseMapper.toResponse(
                notification
        );
        sseEmitterService.push(userId, response);

        return notification;
    }

    /**
     * Always its own transaction. Every caller now reaches this from an
     * after-commit callback, where the publishing transaction has finished but
     * its resources are still bound: a REQUIRED method joins that completed
     * transaction instead of starting one, and everything written inside it is
     * discarded without an error. Notifications simply never appeared. Only
     * the organization lifecycle path escaped, because it already declared
     * REQUIRES_NEW of its own.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchToMany(Collection<UUID> userIds, String title, String content, NotificationType notifiableType, UUID notifiableId, String eventKeyPrefix) {
        List<Notification> notificationsToSave = new ArrayList<>();
        for (UUID userId : userIds) {
            String eventKey = eventKeyPrefix + ":" + userId;
            // Filtered per recipient rather than for the batch: a previous
            // delivery may have reached some of these users and not others,
            // and one already-notified user must not cost the rest theirs.
            if (notificationRepository.existsByUserIdAndEventKey(userId, eventKey)) {
                continue;
            }
            Notification notification = Notification.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .notifiableType(notifiableType)
                    .notifiableId(notifiableId)
                    .eventKey(eventKey)
                    .read(false)
                    .build();
            notificationsToSave.add(notification);
        }

        if (notificationsToSave.isEmpty()) {
            return;
        }

        List<Notification> savedNotifications = notificationRepository.saveAll(
                notificationsToSave
        );
        List<NotificationResponse> responses = notificationResponseMapper
                .toResponses(savedNotifications);
        for (int index = 0; index < savedNotifications.size(); index++) {
            sseEmitterService.push(
                    savedNotifications.get(index).getUserId(),
                    responses.get(index)
            );
        }
    }
}
