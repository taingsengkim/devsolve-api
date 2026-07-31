package kh.edu.istad.ite.devsoleapi.feature.notification.dto;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String content,
        NotificationType notifiableType,
        UUID notifiableId,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
