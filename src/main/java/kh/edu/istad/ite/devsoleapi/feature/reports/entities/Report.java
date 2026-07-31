package kh.edu.istad.ite.devsoleapi.feature.reports.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private UserProfile reporter;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(
            name = "vulnerability_information",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String vulnerabilityInformation;

    @Column(name = "impact", columnDefinition = "TEXT")
    private String impact;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "reported_severity",
            nullable = false,
            columnDefinition = "severity_enum"
    )
    private Severity reportedSeverity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "triage_severity", columnDefinition = "severity_enum")
    private Severity triageSeverity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "severity", columnDefinition = "severity_enum")
    private Severity severity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weakness_id")
    private Weakness weakness;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private ProgramAsset asset;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "state",
            nullable = false,
            columnDefinition = "report_state_enum"
    )
    @Builder.Default
    private ReportState state = ReportState.NEW;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "disclosure_status",
            nullable = false,
            columnDefinition = "disclosure_status_enum"
    )
    @Builder.Default
    private DisclosureStatus disclosureStatus =
            DisclosureStatus.NOT_DISCLOSED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private Report duplicateOf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triaged_by")
    private UserProfile triagedBy;

    @Column(name = "triaged_at")
    private LocalDateTime triagedAt;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @OneToMany(
            mappedBy = "report",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @BatchSize(size = 20)
    @Builder.Default
    private List<ReportAttachment> attachments = new ArrayList<>();

    @OneToMany(
            mappedBy = "report",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @BatchSize(size = 20)
    @Builder.Default
    private List<ReportReward> rewards = new ArrayList<>();

    @OneToMany(mappedBy = "report")
    @BatchSize(size = 20)
    @Builder.Default
    private List<Dispute> disputes = new ArrayList<>();
}
