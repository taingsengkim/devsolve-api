package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ApproachType;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "solution_revisions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_solution_revisions_number",
                columnNames = {"solution_id", "revision_number"}
        )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SolutionRevision extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solution_id", nullable = false)
    private Solution solution;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(nullable = false, length = 250)
    private String summary;

    @Column(name = "body_markdown", nullable = false, columnDefinition = "TEXT")
    private String bodyMarkdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "approach_type", nullable = false, length = 20)
    private ApproachType approachType;

    @ElementCollection
    @CollectionTable(
            name = "solution_revision_verification_steps",
            joinColumns = @JoinColumn(name = "solution_revision_id")
    )
    @OrderColumn(name = "display_order")
    // Batched so a page of solutions costs a couple of extra selects rather
    // than one per row; every response maps all four collections.
    @BatchSize(size = 50)
    @Builder.Default
    private List<SolutionVerificationStep> verificationSteps =
            new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "solution_revision_tested_with",
            joinColumns = @JoinColumn(name = "solution_revision_id")
    )
    @OrderColumn(name = "display_order")
    @BatchSize(size = 50)
    @Builder.Default
    private List<SolutionTestedWith> testedWith = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String tradeoffs;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus moderationStatus = ReviewStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToMany(
            mappedBy = "revision",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<SolutionResource> resources = new ArrayList<>();

    @OneToMany(
            mappedBy = "revision",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("createdAt ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<SolutionAttachment> attachments = new ArrayList<>();
}
