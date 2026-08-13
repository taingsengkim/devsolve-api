package kh.edu.istad.ite.devsoleapi.feature.problem;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemSeverity;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemType;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "problems")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Problem extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "problem_type", nullable = false, length = 30)
    private ProblemType problemType = ProblemType.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "sdlc_phase", length = 40)
    private SdlcPhase sdlcPhase;

    @Column(name = "expected_behavior", columnDefinition = "TEXT")
    private String expectedBehavior;

    @Column(name = "actual_behavior", columnDefinition = "TEXT")
    private String actualBehavior;

    // Batched so a page of problems costs a couple of extra selects rather
    // than two per row; every response maps both collections.
    @ElementCollection
    @CollectionTable(
            name = "problem_reproduction_steps",
            joinColumns = @JoinColumn(name = "problem_id")
    )
    @OrderColumn(name = "display_order")
    @Column(name = "instruction", nullable = false, length = 1_000)
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> reproductionSteps = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "problem_environments",
            joinColumns = @JoinColumn(name = "problem_id")
    )
    @OrderColumn(name = "display_order")
    @BatchSize(size = 50)
    @Builder.Default
    private List<ProblemEnvironment> environment = new ArrayList<>();

    @Column(name = "attempts_tried", columnDefinition = "TEXT")
    private String attemptsTried;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProblemSeverity severity;

    @Column(name = "repository_url", length = 1_000)
    private String repositoryUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProblemStatus status = ProblemStatus.DRAFT;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private long viewCount = 0L;

    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(
            mappedBy = "problem",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("acceptedAt ASC")
    @Builder.Default
    private List<ProblemAcceptedSolution> acceptedSolutions = new ArrayList<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
