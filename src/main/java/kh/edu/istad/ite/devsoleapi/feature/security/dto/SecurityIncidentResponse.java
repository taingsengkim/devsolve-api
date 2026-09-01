package kh.edu.istad.ite.devsoleapi.feature.security.dto;

import kh.edu.istad.ite.devsoleapi.feature.virustotal.VirusTotalScanResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * One refused upload, as the security table renders it.
 *
 * <p>{@code blockedAt} goes on the wire as ISO-8601 in UTC with an explicit
 * {@code Z}. The column behind it is a local timestamp written by the
 * application, so it is converted through the server's own zone rather than
 * being relabelled as UTC — an offset-less timestamp is what silently shifts
 * every "14 minutes ago" for a reader in another zone.
 */
public record SecurityIncidentResponse(

        UUID id,

        Uploader uploader,

        /** Null when the upload belonged to no company. */
        Organization organization,

        UUID reportId,

        String filename,

        long fileSizeBytes,

        String sha256Hash,

        VirusTotalScanResponse.Verdict verdict,

        Stats stats,

        Instant blockedAt
) {

    /**
     * The email is here because an administrator acting on an incident needs
     * to identify the account, and a handle can be changed. Both are the
     * values as they were when the upload was refused.
     */
    public record Uploader(
            UUID id,
            String username,
            String email
    ) {}

    public record Organization(
            UUID id,
            String name
    ) {}

    public record Stats(
            int malicious,
            int suspicious,
            int total
    ) {}
}
