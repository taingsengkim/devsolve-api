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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;
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

import java.math.BigDecimal;
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

    @Column(name = "steps_to_reproduce", columnDefinition = "TEXT")
    private String stepsToReproduce;

    @Column(name = "proof_of_concept", columnDefinition = "TEXT")
    private String proofOfConcept;

    @Column(name = "remediation_recommendation", columnDefinition = "TEXT")
    private String remediationRecommendation;

    /**
     * The exact place the finding lives — a URL, an API route, a package
     * name. {@link #asset} says which in-scope target it belongs to; this
     * says where inside it, which is what a triager needs to reproduce.
     */
    @Column(name = "target_endpoint", length = 1000)
    private String targetEndpoint;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "environment",
            columnDefinition = "report_environment_enum"
    )
    private ReportEnvironment environment;

    @Column(name = "discovered_at")
    private LocalDateTime discoveredAt;

    /**
     * Supporting links — a CVE, an OWASP page, a public write-up of the same
     * class of bug. Stored as jsonb rather than a child table because they are
     * only ever read back whole, with the report.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_links", columnDefinition = "jsonb")
    private List<String> referenceLinks;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "reported_severity",
            nullable = false,
            columnDefinition = "severity_enum"
    )
    private Severity reportedSeverity;

    /**
     * The reporter's CVSS v3.1 vector and the score it produces. Optional: a
     * severity claim alone is still a valid report. When both are given the
     * score has to sit in the band it claims, so a CRITICAL rating cannot be
     * attached to a 2.1.
     */
    @Column(name = "cvss_vector", length = 255)
    private String cvssVector;

    @Column(name = "cvss_score", precision = 3, scale = 1)
    private BigDecimal cvssScore;

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

    /**
     * What resolving this finding was worth to its reporter, and when that was
     * settled. Both null until the organization first resolves the report.
     *
     * <p>Stamped once and never again. A failed retest reopens a resolved
     * report, and the fix that follows resolves it a second time — but that is
     * one finding being paid for, not two, and nothing on this platform ever
     * subtracts reputation, so a second award could not be taken back. This
     * stamp is what makes every later resolution a no-op.
     *
     * <p>The points are recorded rather than recomputed from {@link #severity}
     * on demand: an administrator can still settle a severity dispute
     * afterwards, and the standing the researcher was actually given is the
     * number this has to answer for.
     */
    @Column(name = "reputation_points")
    private Integer reputationPoints;

    @Column(name = "reputation_awarded_at")
    private LocalDateTime reputationAwardedAt;

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

    /**
     * Every round of fix verification this report has been through, oldest
     * first. Read-only from here — a retest is written through
     * {@code ReportRetestRepository} so that its attempt number is allocated
     * against what the table already holds rather than against a collection
     * that may not be loaded.
     */
    @OneToMany(mappedBy = "report")
    @OrderBy("attemptNumber ASC")
    @BatchSize(size = 20)
    @Builder.Default
    private List<ReportRetest> retests = new ArrayList<>();
}
