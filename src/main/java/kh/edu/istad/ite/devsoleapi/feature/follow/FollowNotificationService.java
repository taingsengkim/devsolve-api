package kh.edu.istad.ite.devsoleapi.feature.follow;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowNotificationService {

    private final NotificationRepository notificationRepository;

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
        return notificationRepository.dispatchToFollowers(
                followedType.databaseValue(),
                followedId,
                actorId,
                title,
                content,
                notificationType.databaseValue(),
                notificationTargetId,
                eventKey
        );
    }
}
