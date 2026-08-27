package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.common.mail.MailMessage;
import kh.edu.istad.ite.devsoleapi.common.mail.MailService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sends a notification to the recipient's inbox as well as their bell.
 *
 * <p>An in-app notification only reaches someone who opens DevSolve. A
 * researcher who files a report and closes the tab has no way to learn it was
 * triaged, and the company waiting on their reply has no way to learn they
 * never saw it. This is the second channel for the notifications worth one.
 *
 * <p>Everything here is best-effort and silent about failure. The
 * notification itself is already stored by the time this runs, so a refused
 * SMTP connection costs a convenience, not a record.
 */
@Component
@Slf4j
public class NotificationMailer {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final MailService mailService;
    private final String frontendBaseUrl;
    private final String notificationsPath;
    private final String preferencesPath;

    public NotificationMailer(
            NotificationPreferenceRepository preferenceRepository,
            NotificationRecipientRepository recipientRepository,
            MailService mailService,
            @Value("${app.frontend.base-url:}") String frontendBaseUrl,
            @Value("${app.frontend.notifications-path:/notifications}")
            String notificationsPath,
            @Value("${app.frontend.notification-preferences-path:"
                    + "/settings/notifications}")
            String preferencesPath
    ) {
        this.preferenceRepository = preferenceRepository;
        this.recipientRepository = recipientRepository;
        this.mailService = mailService;
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
        this.notificationsPath = normalisePath(notificationsPath);
        this.preferencesPath = normalisePath(preferencesPath);
    }

    /**
     * Runs on the mail pool, off whatever thread stored the notification.
     * Called only for notifications that were actually created — the
     * dispatcher drops repeats before this point, which is what stops a
     * retried delivery from emailing somebody twice.
     */
    @Async("mailTaskExecutor")
    public void email(Notification notification) {
        try {
            deliver(notification);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to email the {} notification to user {}",
                    notification.getNotifiableType(),
                    notification.getUserId(),
                    exception
            );
        }
    }

    private void deliver(Notification notification) {
        UUID userId = notification.getUserId();
        NotificationType type = notification.getNotifiableType();
        if (!emailEnabledFor(userId, type)) {
            return;
        }

        NotificationRecipientRepository.Recipient recipient =
                recipientRepository.findRecipientById(userId).orElse(null);
        if (recipient == null
                || recipient.getEmail() == null
                || recipient.getEmail().isBlank()) {
            return;
        }
        // A suspended or banned account is not somewhere to keep sending
        // platform mail, and a deleted one may have released the address.
        if (recipient.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        mailService.send(compose(notification, recipient));
    }

    boolean emailEnabledFor(UUID userId, NotificationType type) {
        return preferenceRepository
                .findByUserIdAndType(userId, type)
                .map(NotificationPreference::isEmailEnabled)
                .orElseGet(type::emailedByDefault);
    }

    MailMessage compose(
            Notification notification,
            NotificationRecipientRepository.Recipient recipient
    ) {
        String name = firstName(recipient.getFullName());
        String title = orEmpty(notification.getTitle());
        String content = orEmpty(notification.getContent());
        String inboxUrl = url(notificationsPath);
        String preferencesUrl = url(preferencesPath);

        return new MailMessage(
                recipient.getEmail(),
                title,
                textBody(name, title, content, inboxUrl, preferencesUrl),
                htmlBody(name, title, content, inboxUrl, preferencesUrl)
        );
    }

    private String textBody(
            String name,
            String title,
            String content,
            String inboxUrl,
            String preferencesUrl
    ) {
        StringBuilder body = new StringBuilder()
                .append("Hi ").append(name).append(",\n\n")
                .append(title).append("\n\n")
                .append(content).append('\n');
        if (inboxUrl != null) {
            body.append("\nOpen it on DevSolve:\n").append(inboxUrl)
                    .append('\n');
        }
        if (preferencesUrl != null) {
            body.append("\nChoose which emails you get:\n")
                    .append(preferencesUrl).append('\n');
        }
        return body.append("\n— DevSolve\n").toString();
    }

    private String htmlBody(
            String name,
            String title,
            String content,
            String inboxUrl,
            String preferencesUrl
    ) {
        String button = inboxUrl == null
                ? ""
                : """
                <p style="margin:28px 0;">
                  <a href="%s" style="background:#4f46e5;border-radius:8px;\
                color:#ffffff;display:inline-block;font-weight:600;\
                padding:12px 24px;text-decoration:none;">Open on DevSolve</a>
                </p>
                """.formatted(escape(inboxUrl));
        String footer = preferencesUrl == null
                ? ""
                : """
                <p style="color:#6b7280;font-size:13px;margin:0;">
                  You are getting this because of your DevSolve notification
                  settings. <a href="%s" style="color:#4f46e5;">Choose which
                  emails you get</a>.
                </p>
                """.formatted(escape(preferencesUrl));

        return """
                <div style="background:#f3f4f6;padding:24px;">
                  <div style="background:#ffffff;border-radius:12px;\
                color:#111827;font-family:Arial,Helvetica,sans-serif;\
                font-size:15px;line-height:24px;margin:0 auto;max-width:560px;\
                padding:32px;">
                    <h1 style="font-size:20px;margin:0 0 16px;">%s</h1>
                    <p style="margin:0 0 16px;">Hi %s,</p>
                    <p style="margin:0;">%s</p>
                    %s
                    %s
                  </div>
                </div>
                """.formatted(
                        escape(title),
                        escape(name),
                        escape(content),
                        button,
                        footer
                );
    }

    private String url(String path) {
        return frontendBaseUrl.isBlank() ? null : frontendBaseUrl + path;
    }

    /**
     * Notification content is assembled from names, titles and reasons people
     * typed, so none of it can go into the HTML body raw.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        return fullName.trim().split("\\s+")[0];
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalisePath(String value) {
        String trimmed = trimTrailingSlash(value);
        if (trimmed.isBlank()) {
            return "";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
