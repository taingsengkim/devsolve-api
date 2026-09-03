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
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportActivityType;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One thing that happened to a report, in the order it happened.
 *
 * <p>The report row only ever holds where things ended up — the current state,
 * who triaged it last, when it was resolved. That is enough to render a report
 * and nowhere near enough to answer how it got there, which is the question
 * both sides ask the moment they disagree. A report that was confirmed, paid,
 * reopened by a failed retest and resolved again reads, from the row alone, as
 * a report that was simply resolved.
 *
 * <p>Append-only. Nothing here is ever updated or deleted while the report
 * exists: a timeline somebody can edit afterwards is not evidence, and the
 * dispute flow this feeds is exactly where that matters.
 *
 * <p>Visible to anyone who can view the report. Internal discussion has its own
 * home on comments, with its own visibility rule — nothing written here should
 * be anything the reporter may not read.
 */
@Entity
@Table(name = "report_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    @JsonIgnore
    private Report report;

    /**
     * Null when the platform itself acted rather than a person — a retest
     * lapsing on its deadline is the only such event today. Rendered as "the
     * system" rather than hidden: a report that reopened itself needs to say
     * so.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private UserProfile actor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "activity_type",
            nullable = false,
            columnDefinition = "report_activity_type_enum"
    )
    private ReportActivityType activityType;

    /**
     * Where the report moved from and to. Both null on an event that is not a
     * transition — a reward, a disclosure change — so a client can tell a move
     * from something that happened while the report stood still.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "from_state", columnDefinition = "report_state_enum")
    private ReportState fromState;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "to_state", columnDefinition = "report_state_enum")
    private ReportState toState;

    /** The severity this event settled on, where it settled one. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "severity", columnDefinition = "severity_enum")
    private Severity severity;

    /**
     * One short line of context written by the platform, not by a person —
     * "still vulnerable", "no answer within 14 days". Free text a person typed
     * belongs on a comment, where it can be replied to.
     */
    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
