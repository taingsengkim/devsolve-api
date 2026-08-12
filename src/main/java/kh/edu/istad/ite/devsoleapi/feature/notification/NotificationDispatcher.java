package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    @Transactional
    public Notification dispatch(UUID userId, String title, String content, NotificationType notifiableType, UUID notifiableId, String eventKey) {
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
        NotificationResponse response = toResponse(notification);
        sseEmitterService.push(userId, response);

        return notification;
    }

    @Transactional
    public void dispatchToMany(Collection<UUID> userIds, String title, String content, NotificationType notifiableType, UUID notifiableId, String eventKeyPrefix) {
        List<Notification> notificationsToSave = new ArrayList<>();
        for (UUID userId : userIds) {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .notifiableType(notifiableType)
                    .notifiableId(notifiableId)
                    .eventKey(eventKeyPrefix + ":" + userId)
                    .read(false)
                    .build();
            notificationsToSave.add(notification);
        }

        List<Notification> savedNotifications = notificationRepository.saveAll(notificationsToSave);
        for (Notification notification : savedNotifications) {
            NotificationResponse response = toResponse(notification);
            sseEmitterService.push(notification.getUserId(), response);
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getNotifiableType(),
                notification.getNotifiableId(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
