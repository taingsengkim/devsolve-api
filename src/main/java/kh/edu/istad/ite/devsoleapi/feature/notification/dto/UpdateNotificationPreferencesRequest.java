package kh.edu.istad.ite.devsoleapi.feature.notification.dto;

import jakarta.validation.constraints.NotEmpty;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;

import java.util.Map;

/**
 * A partial update: only the types named are changed, everything else keeps
 * whatever it had. A settings screen can send one toggle without having to
 * resend the whole page, and two screens open at once cannot silently undo
 * each other's other switches.
 */
public record UpdateNotificationPreferencesRequest(
        @NotEmpty(message = "At least one notification preference is required")
        Map<NotificationType, Boolean> preferences
) {
}
