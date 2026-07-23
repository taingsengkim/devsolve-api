package co.istad.ite.devsoleapi.feature.reports.entities;

import co.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import co.istad.ite.devsoleapi.feature.reports.enums.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "raised_by", nullable = false, length = 255)
    private String raisedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            columnDefinition = "dispute_status_enum"
    )
    private DisputeStatus status = DisputeStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "resolved_severity",
            columnDefinition = "severity_enum"
    )
    private Severity resolvedSeverity;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;


    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();

        if (status == null) {
            status = DisputeStatus.OPEN;
        }
    }
}