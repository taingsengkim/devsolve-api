package kh.edu.istad.ite.devsoleapi.feature.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationPreferenceResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.UnreadNotificationCountResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.UpdateNotificationPreferencesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Page<NotificationResponse> getMine(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be at least 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize
    ) {
        return notificationService.getMine(
                unreadOnly,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse getUnreadCount() {
        return notificationService.getUnreadCount();
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            @PathVariable UUID notificationId
    ) {
        return notificationService.markRead(notificationId);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        notificationService.markAllRead();
    }

    /**
     * Every notification type with whether it currently reaches the caller by
     * email, defaults included — this is the settings screen's whole payload.
     */
    @GetMapping("/preferences")
    public List<NotificationPreferenceResponse> getMyEmailPreferences() {
        return notificationService.getMyEmailPreferences();
    }

    @PutMapping("/preferences")
    public List<NotificationPreferenceResponse> updateMyEmailPreferences(
            @Valid @RequestBody UpdateNotificationPreferencesRequest request
    ) {
        return notificationService.updateMyEmailPreferences(request);
    }
}
