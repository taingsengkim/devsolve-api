package kh.edu.istad.ite.devsoleapi.feature.reports.entities;

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
import jakarta.persistence.Table;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A report a reporter has started and not filed. Deliberately a separate table
 * rather than a DRAFT value on {@link Report}: title, vulnerability information
 * and reported severity are NOT NULL on a report, and a half-written draft has
 * none of them. Carrying drafts on the reports table would mean making those
 * three columns nullable for every genuine report, and auditing every query,
 * trigger and notification path to exclude a state they were never written to
 * expect — where anything missed leaks a draft into a triage queue.
 *
 * <p>Nothing here is validated beyond a length cap. Autosave has to accept a
 * form mid-keystroke, so the rules live at submit, where the draft is turned
 * into a real {@code CreateReportRequest} and put through the same validated
 * path a direct submission takes.
 */
@Entity
@Table(name = "report_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportDraft extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Which program this is being written for. Fixed at creation — a draft
     * cannot be moved between programs, because scope, assets and severity
     * expectations are all program-specific and none of them would survive
     * the move meaningfully.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private UserProfile reporter;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "vulnerability_information", columnDefinition = "TEXT")
    private String vulnerabilityInformation;

    @Column(name = "impact", columnDefinition = "TEXT")
    private String impact;

    @Column(name = "steps_to_reproduce", columnDefinition = "TEXT")
    private String stepsToReproduce;

    @Column(name = "proof_of_concept", columnDefinition = "TEXT")
    private String proofOfConcept;

    @Column(name = "remediation_recommendation", columnDefinition = "TEXT")
    private String remediationRecommendation;

    @Column(name = "target_endpoint", length = 1000)
    private String targetEndpoint;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "environment", columnDefinition = "report_environment_enum")
    private ReportEnvironment environment;

    @Column(name = "discovered_at")
    private LocalDateTime discoveredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_links", columnDefinition = "jsonb")
    private List<String> referenceLinks;

    /**
     * Nullable here where a report requires it: a reporter who has written the
     * finding but not yet decided how bad it is still has a draft worth
     * keeping.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reported_severity", columnDefinition = "severity_enum")
    private Severity reportedSeverity;

    @Column(name = "cvss_vector", length = 255)
    private String cvssVector;

    @Column(name = "cvss_score", precision = 3, scale = 1)
    private BigDecimal cvssScore;

    /**
     * Plain identifiers rather than foreign keys, unlike the report they will
     * become. A draft can outlive the weakness being retired or the asset
     * being taken out of scope, and a constraint here would either block that
     * or delete the draft with it. Nothing reads these until submit, which
     * resolves both through the normal lookups and returns a real error if
     * they have stopped being valid in the meantime.
     */
    @Column(name = "weakness_id")
    private UUID weaknessId;

    /** The reporter's own wording, when they named a class themselves. */
    @Column(name = "suggested_weakness", length = 255)
    private String suggestedWeakness;

    @Column(name = "asset_id")
    private UUID assetId;
}
