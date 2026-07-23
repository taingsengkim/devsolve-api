
package co.istad.ite.devsoleapi.feature.reports.entities;

import co.istad.ite.devsoleapi.feature.reports.enums.AssetType;
import co.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import co.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import co.istad.ite.devsoleapi.feature.reports.enums.Severity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    @Column(name = "cvss_score", precision = 3, scale = 1)
    private BigDecimal cvssScore;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private List<ReportAttachment> attachments;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private List<ReportReward> rewards;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    private AssetType assetType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ReportState state = ReportState.NEW;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "disclosure_status", nullable = false)
    private DisclosureStatus disclosureStatus = DisclosureStatus.NOT_DISCLOSED;

    @Column(name = "triaged_by", length = 255)
    private String triagedBy;

    @Column(name = "triaged_at")
    private Instant triagedAt;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (submittedAt == null) {
            submittedAt = now;
        }

        if (state == null) {
            state = ReportState.NEW;
        }

        if (disclosureStatus == null) {
            disclosureStatus = DisclosureStatus.NOT_DISCLOSED;
        }

        if (reportedSeverity == null && severity != null) {
            reportedSeverity = severity;
        }
    }
}