package kh.edu.istad.ite.devsoleapi.feature.notification.dto;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;

/**
 * One row of the notification settings screen.
 *
 * @param emailEnabled the effective answer, whether it came from a choice the
 *                     user made or from the type's default. The screen should
 *                     not have to know which
 */
public record NotificationPreferenceResponse(
        NotificationType type,
        boolean emailEnabled
) {
}
