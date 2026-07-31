package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.UnreadNotificationCountResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface NotificationService {

    Page<NotificationResponse> getMine(
            boolean unreadOnly,
            int pageNumber,
            int pageSize
    );

    UnreadNotificationCountResponse getUnreadCount();

    NotificationResponse markRead(UUID notificationId);

    void markAllRead();
}
