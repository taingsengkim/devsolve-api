package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;

import java.util.UUID;

/**
 * Where an upload was headed, so a rejected one can be reported to the people
 * it concerns.
 *
 * <p>{@link VirusTotalContentGuard} sees bytes and a verdict; it has no idea
 * whether those bytes were a report attachment belonging to a company or a file
 * on a community problem. That difference decides who hears about a malicious
 * upload, so the caller — which does know — passes it in.
 *
 * @param organizationId the company whose triage team should be told, or null
 *                       when the upload belongs to no company. Platform
 *                       administrators are told either way.
 * @param notifiableId   what the notification opens: the report, problem or
 *                       solution the file was being attached to
 * @param notifiableType which of those it is
 * @param location       a short human phrase for the alert text, such as
 *                       "a report" — read by a person, not parsed
 */
public record AttachmentScanContext(
        UUID organizationId,
        UUID notifiableId,
        NotificationType notifiableType,
        String location
) {

    /**
     * An upload with no context to report. The scan still runs and a malicious
     * file is still refused — only the alert is skipped, because there is
     * nothing to point it at.
     */
    public static final AttachmentScanContext NONE =
            new AttachmentScanContext(null, null, null, null);

    public boolean isReportable() {
        return notifiableId != null && notifiableType != null;
    }
}
