package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "solutions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_solutions_published_revision",
                        columnNames = "current_published_revision_id"
                ),
                @UniqueConstraint(
                        name = "uq_solutions_latest_revision",
                        columnNames = "latest_revision_id"
                )
        }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Solution extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "current_published_revision_id",
            foreignKey = @ForeignKey(
                    name = "fk_solutions_current_published_revision"
            )
    )
    private SolutionRevision currentPublishedRevision;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "latest_revision_id",
            foreignKey = @ForeignKey(name = "fk_solutions_latest_revision")
    )
    private SolutionRevision latestRevision;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
