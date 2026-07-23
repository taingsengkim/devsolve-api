
package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import co.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import co.istad.ite.devsoleapi.feature.reports.enums.Severity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "reporter_id", nullable = false, length = 255)
    private String reporterId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "vulnerability_information", nullable = false, columnDefinition = "TEXT")
    private String vulnerabilityInformation;

    @Column(name = "impact", columnDefinition = "TEXT")
    private String impact;

    @Enumerated(EnumType.STRING)
    @Column(name = "reported_severity", nullable = false)
    private Severity reportedSeverity;

    @Enumerated(EnumType.STRING)
    @Column(name = "triage_severity")
    private Severity triageSeverity;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private Severity severity;

    @Column(name = "weakness_id")
    private UUID weaknessId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ReportState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "disclosure_status", nullable = false)
    private DisclosureStatus disclosureStatus;

    @Column(name = "triaged_by", length = 255)
    private String triagedBy;

    @Column(name = "triaged_at")
    private LocalDateTime triagedAt;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}