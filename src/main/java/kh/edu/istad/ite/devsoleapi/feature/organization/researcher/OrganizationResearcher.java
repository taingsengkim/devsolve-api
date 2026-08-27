package kh.edu.istad.ite.devsoleapi.feature.organization.researcher;

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
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One researcher's standing with one company, covering every program that
 * company runs. At most one row per pair: it is reused as the relationship
 * changes rather than replaced, so a rejection followed by a fresh request
 * moves this row back to PENDING.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "organization_researchers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_organization_researchers_org_user",
                columnNames = {"organization_id", "user_id"}
        )
)
public class OrganizationResearcher extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile researcher;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "researcher_access_status_enum"
    )
    private ResearcherAccessStatus status = ResearcherAccessStatus.PENDING;

    /** Null on a row the company created by inviting someone. */
    @Column(name = "motivation", columnDefinition = "TEXT")
    private String motivation;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private UserProfile reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * Passes this relationship has been through, stepped by the first thing
     * that happens to the row. Notifications key on it so a second rejection
     * is not swallowed as a duplicate of the first.
     */
    @Column(name = "revision", nullable = false)
    private int revision = 0;

    public OrganizationResearcher(
            Organization organization,
            UserProfile researcher
    ) {
        this.organization = organization;
        this.researcher = researcher;
    }

    public void markRequested(String motivation) {
        this.status = ResearcherAccessStatus.PENDING;
        this.motivation = motivation;
        this.requestedAt = LocalDateTime.now();
        this.reviewNote = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.revision++;
    }

    public void approve(UserProfile reviewer, String note) {
        settle(ResearcherAccessStatus.APPROVED, reviewer, note);
    }

    public void reject(UserProfile reviewer, String note) {
        settle(ResearcherAccessStatus.REJECTED, reviewer, note);
    }

    public void revoke(UserProfile reviewer, String note) {
        settle(ResearcherAccessStatus.REVOKED, reviewer, note);
    }

    private void settle(
            ResearcherAccessStatus outcome,
            UserProfile reviewer,
            String note
    ) {
        this.status = outcome;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNote = note;
        this.revision++;
    }

    public boolean isApproved() {
        return status == ResearcherAccessStatus.APPROVED;
    }
}
