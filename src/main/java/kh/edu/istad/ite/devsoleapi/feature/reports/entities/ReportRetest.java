package kh.edu.istad.ite.devsoleapi.feature.reports.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.RetestVerdict;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One round of "we deployed a fix, please check it".
 *
 * <p>A row per attempt rather than a pair of columns on the report, because a
 * fix that does not hold is normal: a failed retest reopens the report and the
 * organization fixes and resolves it again. Overwriting the previous attempt
 * would erase the evidence that the first fix failed, which is exactly what a
 * researcher arguing about a bounty, and an organization reviewing its own
 * remediation, need to be able to point at.
 *
 * <p>A retest is open while {@link #completedAt} is null. At most one attempt
 * on a report is open at a time.
 */
@Entity
@Table(name = "report_retests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportRetest extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    @JsonIgnore
    private Report report;

    /** 1 for the first request on a report, then 2, 3 — never reused. */
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "environment",
            columnDefinition = "report_environment_enum"
    )
    private ReportEnvironment environment;

    /**
     * Where to re-run the proof of concept. Often not the endpoint on the
     * report itself — the whole point of a staging retest is that the fix is
     * somewhere else first.
     */
    @Column(name = "target_endpoint", length = 1000)
    private String targetEndpoint;

    @Column(name = "request_notes", columnDefinition = "TEXT")
    private String requestNotes;

    /**
     * A bonus the organization commits to when it asks. Held here rather than
     * paid immediately: it is owed for the verification work, and that work has
     * not happened yet. Paid out as a {@link ReportReward} when the researcher
     * comes back with a verdict — either verdict.
     *
     * <p>Paying only on VERIFIED_FIXED would be paying a researcher to agree
     * that the fix worked, which is the one answer they should have no stake
     * in. The bonus buys the retest, not the result.
     */
    @Column(name = "bounty_reward", precision = 10, scale = 2)
    private BigDecimal bountyReward;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private UserProfile requestedBy;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    /**
     * When the researcher's window to answer runs out. Past this, the attempt
     * lapses and the report goes back to where it was before it was asked for.
     *
     * <p>Stamped on the row rather than derived from {@link #requestedAt} and a
     * constant, so that both sides can be shown the same deadline and changing
     * the window later does not silently move the deadline on attempts that
     * were already outstanding.
     *
     * <p>Null on attempts that predate the window, which the expiry sweep reads
     * as "no deadline" and leaves alone.
     */
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /**
     * Null while the retest is open, and null on a closed one too when triage
     * moved the report on before the researcher answered — {@link #completedAt}
     * is what says whether it is still outstanding.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "verdict", columnDefinition = "retest_verdict_enum")
    private RetestVerdict verdict;

    @Column(name = "result_notes", columnDefinition = "TEXT")
    private String resultNotes;

    /**
     * Attachments on the report that this attempt points at as its evidence —
     * the screenshot of the 403, the log of the blocked payload. Ids rather
     * than a join table: they are only ever read back whole with the retest,
     * the same reason a report's reference links are jsonb.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_ids", columnDefinition = "jsonb")
    private List<UUID> attachmentIds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private UserProfile completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public boolean isOpen() {
        return completedAt == null;
    }

    /**
     * Open, and the window to answer has run out. An attempt with no
     * {@link #dueAt} is never overdue — see the field.
     */
    public boolean isOverdue(LocalDateTime asOf) {
        return isOpen() && dueAt != null && dueAt.isBefore(asOf);
    }
}
