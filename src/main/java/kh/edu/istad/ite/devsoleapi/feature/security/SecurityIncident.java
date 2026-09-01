package kh.edu.istad.ite.devsoleapi.feature.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.VirusTotalScanResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One upload the platform refused because a scanner called it dangerous.
 *
 * <p>An audit row, not a projection: the uploader's handle and email and the
 * organization's name are copied in rather than joined at read time. An
 * incident has to stay readable after the account that caused it is deleted or
 * renamed — that is most of what an audit trail is for, and a join would
 * quietly empty the table's most important rows exactly when somebody went
 * looking for them.
 *
 * <p>The file itself is never stored. The SHA-256 is what identifies it, and
 * is the value an operator pastes into VirusTotal or a threat feed to see what
 * it was.
 */
@Entity
@Table(
        name = "security_incidents",
        indexes = {
                @Index(
                        name = "idx_security_incidents_blocked_at",
                        columnList = "blocked_at"
                ),
                @Index(
                        name = "idx_security_incidents_organization_id",
                        columnList = "organization_id"
                ),
                @Index(
                        name = "idx_security_incidents_uploader_user_id",
                        columnList = "uploader_user_id"
                ),
                @Index(
                        name = "idx_security_incidents_sha256",
                        columnList = "sha256_hash"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * Deliberately a plain UUID rather than a foreign key to user_profiles.
     * An incident outlives the account: a key with ON DELETE CASCADE would
     * erase the record of what somebody did by deleting them, and one without
     * it would block the deletion.
     */
    @Column(name = "uploader_user_id", nullable = false)
    private UUID uploaderUserId;

    @Column(name = "uploader_username", length = 255)
    private String uploaderUsername;

    @Column(name = "uploader_email", length = 255)
    private String uploaderEmail;

    /** Null when the upload belonged to no company — a problem or solution. */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "organization_name", length = 255)
    private String organizationName;

    /** What the file was being attached to, when that was a report. */
    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    /**
     * Stored as a string rather than a Postgres named enum: a new verdict is
     * then an application change, with no CREATE TYPE that has to land on
     * every environment before the column can hold it.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false, length = 20)
    private VirusTotalScanResponse.Verdict verdict;

    @Column(name = "malicious_engines_count", nullable = false)
    private int maliciousEnginesCount;

    @Column(name = "suspicious_engines_count", nullable = false)
    private int suspiciousEnginesCount;

    /** Every engine that returned anything, across all categories. */
    @Column(name = "total_engines_count", nullable = false)
    private int totalEnginesCount;

    @Column(name = "analysis_id", length = 255)
    private String analysisId;

    @Column(name = "blocked_at", nullable = false)
    private LocalDateTime blockedAt;
}
