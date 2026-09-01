package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationDispatcher;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.CompanyIdentityService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tells the people who need to know that a dangerous upload was refused.
 *
 * <p>Deliberately not a {@code NotificationEvent}. Those are delivered after
 * the publishing transaction commits, and the transaction that carried this
 * upload is about to be rolled back by the 422 — so an event would be
 * published into a transaction that never commits and silently never arrive.
 * {@link NotificationDispatcher} already runs REQUIRES_NEW, which is exactly
 * the property this needs: the alert survives the rollback that discards the
 * file.
 *
 * <p>Nothing here is allowed to turn a refusal into a 500. A malicious file
 * must be rejected whether or not anybody could be told about it, so every
 * failure is logged and swallowed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VirusTotalAlertService {

    private static final String PLATFORM_ADMIN_ROLE = "ADMIN";

    private final NotificationDispatcher notificationDispatcher;
    private final CompanyIdentityService companyIdentityService;
    private final OrganizationAuthorizationService organizationAuthorization;

    public void malicious(
            AttachmentValidator.ValidatedAttachment attachment,
            VirusTotalScanResponse result,
            AttachmentScanContext context
    ) {
        try {
            alert(attachment, result, context);
        } catch (RuntimeException exception) {
            // The upload is already refused; this is only the telling.
            log.error(
                    "Could not raise the alert for a {} upload of \"{}\""
                            + " (analysis {})",
                    result.verdict(),
                    attachment.originalFileName(),
                    result.analysisId(),
                    exception
            );
        }
    }

    private void alert(
            AttachmentValidator.ValidatedAttachment attachment,
            VirusTotalScanResponse result,
            AttachmentScanContext context
    ) {
        // Logged unconditionally, before any lookup that could fail or return
        // nobody. An alert nobody was available to receive still has to leave
        // a trace somewhere an operator can find it.
        log.warn(
                "Refused a {} upload: \"{}\" ({} bytes) by user {} to {}"
                        + " -- VirusTotal analysis {}, stats {}",
                result.verdict(),
                attachment.originalFileName(),
                attachment.sizeBytes(),
                currentUserId(),
                context.location() == null ? "an unknown target"
                        : context.location(),
                result.analysisId(),
                result.stats()
        );

        if (!context.isReportable()) {
            return;
        }

        Set<UUID> recipients = new LinkedHashSet<>(
                companyIdentityService.findUserIdsByRealmRole(
                        PLATFORM_ADMIN_ROLE
                )
        );

        if (context.organizationId() != null) {
            recipients.addAll(
                    organizationAuthorization.findUserIdsWithPermission(
                            context.organizationId(),
                            OrganizationPermission.TRIAGE_REPORTS
                    )
            );
        }

        if (recipients.isEmpty()) {
            return;
        }

        notificationDispatcher.dispatchToMany(
                recipients,
                title(result),
                content(attachment, result, context),
                NotificationType.SECURITY,
                context.notifiableId(),
                // One refusal is one happening however many times a retrying
                // client repeats it: the analysis is the same either way.
                "virustotal:" + result.analysisId()
        );
    }

    private String title(VirusTotalScanResponse result) {
        return result.verdict() == VirusTotalScanResponse.Verdict.MALICIOUS
                ? "Malicious file upload blocked"
                : "Suspicious file upload blocked";
    }

    private String content(
            AttachmentValidator.ValidatedAttachment attachment,
            VirusTotalScanResponse result,
            AttachmentScanContext context
    ) {
        return "An upload of \"" + attachment.originalFileName()
                + "\" to " + context.location()
                + " was refused. VirusTotal returned "
                + result.verdict() + " (" + engineSummary(result.stats())
                + "). The file was not stored. VirusTotal analysis "
                + result.analysisId() + ".";
    }

    /**
     * The counts a person actually reads off a verdict, in the order they
     * would read them. Absent categories are left out rather than printed as
     * zero — VirusTotal omits the ones no engine returned.
     */
    private String engineSummary(Map<String, Integer> stats) {
        StringBuilder summary = new StringBuilder();
        for (String category : new String[]{
                "malicious", "suspicious", "harmless", "undetected"
        }) {
            Integer count = stats.get(category);
            if (count == null) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(count).append(' ').append(category);
        }
        return summary.isEmpty() ? "no engine breakdown" : summary.toString();
    }

    /**
     * Best effort: the guard also runs on paths reached outside a request, and
     * an alert must not fail because nobody was authenticated.
     */
    private String currentUserId() {
        try {
            return AuthUtils.extractUserId();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }
}
