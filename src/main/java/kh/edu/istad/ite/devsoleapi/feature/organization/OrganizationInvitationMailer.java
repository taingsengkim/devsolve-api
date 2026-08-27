package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.common.mail.MailMessage;
import kh.edu.istad.ite.devsoleapi.common.mail.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Emails an organization team invitation to the person invited.
 *
 * <p>The in-app notification published alongside this only reaches someone who
 * happens to open DevSolve; the email reaches them where they are. Both carry
 * the same invitation token, and accepting through either one is what turns
 * the pending membership into a real one.
 */
@Component
@Slf4j
public class OrganizationInvitationMailer {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm", Locale.ENGLISH);

    private final MailService mailService;
    private final String frontendBaseUrl;
    private final String invitationPath;

    public OrganizationInvitationMailer(
            MailService mailService,
            @Value("${app.frontend.base-url:}") String frontendBaseUrl,
            @Value("${app.frontend.invitation-path:/invitations}")
            String invitationPath
    ) {
        this.mailService = mailService;
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
        this.invitationPath = normalisePath(invitationPath);
    }

    /**
     * Runs after the invitation has committed, and off the request thread:
     * an SMTP server that takes ten seconds to answer must not make the
     * company wait ten seconds for its "invitation sent" response.
     */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvitation(OrganizationInvitationEmailEvent event) {
        try {
            mailService.send(compose(event));
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to email the invitation to organization {}",
                    event.organizationId(),
                    exception
            );
        }
    }

    MailMessage compose(OrganizationInvitationEmailEvent event) {
        String organizationName = orFallback(
                event.organizationName(),
                "an organization"
        );
        String inviterName = orFallback(event.inviterName(), "A team owner");
        String recipientName = orFallback(event.recipientName(), "there");
        String role = event.role() == null
                ? "member"
                : event.role().name().toLowerCase(Locale.ENGLISH);
        String acceptUrl = acceptUrl(event.invitationToken());
        String expiry = expiryLine(event.expiresAt());

        return new MailMessage(
                event.recipientEmail(),
                "You have been invited to join " + organizationName
                        + " on DevSolve",
                textBody(
                        recipientName,
                        inviterName,
                        organizationName,
                        role,
                        acceptUrl,
                        expiry
                ),
                htmlBody(
                        recipientName,
                        inviterName,
                        organizationName,
                        role,
                        acceptUrl,
                        expiry
                )
        );
    }

    /**
     * Where the invited person goes to accept. The frontend page behind it
     * signs them in and posts the token back, which is what makes them a
     * member; the API never turns a bare link click into a membership.
     *
     * <p>Null when {@code app.frontend.base-url} has been blanked out; the
     * email then still announces the invitation, and the invited user accepts
     * it from their DevSolve notifications instead.
     */
    String acceptUrl(String invitationToken) {
        if (frontendBaseUrl.isBlank() || invitationToken == null) {
            return null;
        }
        return frontendBaseUrl + invitationPath + "/" + invitationToken;
    }

    private String textBody(
            String recipientName,
            String inviterName,
            String organizationName,
            String role,
            String acceptUrl,
            String expiry
    ) {
        StringBuilder body = new StringBuilder()
                .append("Hi ").append(recipientName).append(",\n\n")
                .append(inviterName)
                .append(" invited you to join the ")
                .append(organizationName)
                .append(" team on DevSolve as a ")
                .append(role)
                .append(".\n\n");
        if (acceptUrl == null) {
            body.append("Accept the invitation from your DevSolve ")
                    .append("notifications to become a member of ")
                    .append(organizationName)
                    .append(".\n");
        } else {
            body.append("Accept the invitation to become a member of ")
                    .append(organizationName)
                    .append(":\n")
                    .append(acceptUrl)
                    .append('\n');
        }
        if (!expiry.isBlank()) {
            body.append('\n').append(expiry).append('\n');
        }
        return body
                .append("\nIf you were not expecting this invitation you can ")
                .append("ignore this email — nothing happens until you ")
                .append("accept.\n\n")
                .append("— DevSolve\n")
                .toString();
    }

    private String htmlBody(
            String recipientName,
            String inviterName,
            String organizationName,
            String role,
            String acceptUrl,
            String expiry
    ) {
        String safeOrganization = escape(organizationName);
        String button = acceptUrl == null
                ? ""
                : """
                <p style="margin:32px 0;">
                  <a href="%s" style="background:#4f46e5;border-radius:8px;\
                color:#ffffff;display:inline-block;font-weight:600;\
                padding:12px 24px;text-decoration:none;">Accept invitation</a>
                </p>
                <p style="color:#6b7280;font-size:13px;line-height:20px;">
                  Or paste this link into your browser:<br>
                  <a href="%s" style="color:#4f46e5;">%s</a>
                </p>
                """.formatted(
                        escape(acceptUrl),
                        escape(acceptUrl),
                        escape(acceptUrl)
                );
        String expiryNote = expiry.isBlank()
                ? ""
                : """
                <p style="color:#6b7280;font-size:13px;margin:0 0 8px;">%s</p>
                """.formatted(escape(expiry));

        return """
                <!-- Inline styles only: mail clients drop <style> blocks. -->
                <div style="background:#f3f4f6;padding:24px;">
                  <div style="background:#ffffff;border-radius:12px;\
                color:#111827;font-family:Arial,Helvetica,sans-serif;\
                font-size:15px;line-height:24px;margin:0 auto;max-width:560px;\
                padding:32px;">
                    <h1 style="font-size:20px;margin:0 0 16px;">\
                You have been invited to %s</h1>
                    <p style="margin:0 0 16px;">Hi %s,</p>
                    <p style="margin:0 0 16px;">
                      <strong>%s</strong> invited you to join the
                      <strong>%s</strong> team on DevSolve as a <strong>%s\
                </strong>.
                    </p>
                    <p style="margin:0;">
                      Accepting makes you a member of %s and gives you the
                      access that comes with that role.
                    </p>
                    %s
                    %s
                    <p style="color:#6b7280;font-size:13px;margin:0;">
                      If you were not expecting this invitation you can ignore
                      this email — nothing happens until you accept.
                    </p>
                  </div>
                </div>
                """.formatted(
                        safeOrganization,
                        escape(recipientName),
                        escape(inviterName),
                        safeOrganization,
                        escape(role),
                        safeOrganization,
                        button,
                        expiryNote
                );
    }

    private String expiryLine(LocalDateTime expiresAt) {
        return expiresAt == null
                ? ""
                : "This invitation expires on " + EXPIRY_FORMAT.format(expiresAt)
                        + ".";
    }

    /**
     * Organization and member names are user input, so they cannot go into the
     * HTML body raw — an apostrophe in a company name would be enough to
     * break the markup, and a tag would do worse.
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

    private String orFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
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
