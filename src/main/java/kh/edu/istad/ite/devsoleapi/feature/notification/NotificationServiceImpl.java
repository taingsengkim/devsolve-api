package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.UnreadNotificationCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMine(
            boolean unreadOnly,
            int pageNumber,
            int pageSize
    ) {
        return notificationRepository.findInbox(
                currentUserId(),
                unreadOnly,
                PageRequest.of(
                        pageNumber,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        ).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getUnreadCount() {
        return new UnreadNotificationCountResponse(
                notificationRepository.countByUserIdAndReadFalse(
                        currentUserId()
                )
        );
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID userId = currentUserId();
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found"
                ));
        if (!notification.isRead()) {
            notificationRepository.markRead(notificationId, userId);
            notification.setRead(true);
            notification.setReadAt(java.time.LocalDateTime.now());
        }
        return toResponse(notification);
    }

    @Override
    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(currentUserId());
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

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }
}
