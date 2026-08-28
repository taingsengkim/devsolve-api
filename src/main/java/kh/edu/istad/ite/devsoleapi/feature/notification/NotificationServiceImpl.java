package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationPreferenceResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.UnreadNotificationCountResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.UpdateNotificationPreferencesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationResponseMapper notificationResponseMapper;
    private final NotificationPreferenceRepository preferenceRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMine(
            boolean unreadOnly,
            int pageNumber,
            int pageSize
    ) {
        Page<Notification> notifications = notificationRepository.findInbox(
                currentUserId(),
                unreadOnly,
                PageRequest.of(
                        pageNumber,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        );
        return notificationResponseMapper.toPage(notifications);
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
        return notificationResponseMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(currentUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getMyEmailPreferences() {
        return effectivePreferences(currentUserId());
    }

    @Override
    @Transactional
    public List<NotificationPreferenceResponse> updateMyEmailPreferences(
            UpdateNotificationPreferencesRequest request
    ) {
        UUID userId = currentUserId();
        Map<NotificationType, NotificationPreference> stored = storedBy(userId);

        request.preferences().forEach((type, emailEnabled) -> {
            if (type == null || emailEnabled == null) {
                return;
            }
            NotificationPreference preference = stored.get(type);
            if (preference == null) {
                preferenceRepository.save(new NotificationPreference(
                        userId,
                        type,
                        emailEnabled
                ));
            } else {
                preference.setEmailEnabled(emailEnabled);
            }
        });

        return effectivePreferences(userId);
    }

    /**
     * Every type with the answer that applies, whether or not the user has
     * ever chosen. A settings screen showing only stored rows would show a new
     * account an empty page and imply nothing is emailed.
     */
    private List<NotificationPreferenceResponse> effectivePreferences(
            UUID userId
    ) {
        Map<NotificationType, NotificationPreference> stored = storedBy(userId);
        return Arrays.stream(NotificationType.values())
                .map(type -> new NotificationPreferenceResponse(
                        type,
                        stored.containsKey(type)
                                ? stored.get(type).isEmailEnabled()
                                : type.emailedByDefault()
                ))
                .toList();
    }

    private Map<NotificationType, NotificationPreference> storedBy(
            UUID userId
    ) {
        Map<NotificationType, NotificationPreference> byType =
                new EnumMap<>(NotificationType.class);
        for (NotificationPreference preference
                : preferenceRepository.findByUserId(userId)) {
            byType.put(preference.getType(), preference);
        }
        return byType;
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
