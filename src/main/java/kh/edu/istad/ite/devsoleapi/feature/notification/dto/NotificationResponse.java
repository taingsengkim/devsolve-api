package kh.edu.istad.ite.devsoleapi.feature.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String content,
        NotificationType notifiableType,
        UUID notifiableId,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        UUID authorId,
        /** The handle a profile link is built from, when there is a person. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String authorUsername,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String authorName,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String authorAvatarUrl,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
