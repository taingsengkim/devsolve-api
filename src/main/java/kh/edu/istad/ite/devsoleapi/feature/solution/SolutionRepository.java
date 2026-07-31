package kh.edu.istad.ite.devsoleapi.feature.solution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SolutionRepository extends JpaRepository<Solution, UUID> {

    Page<Solution>
    findAllByProblem_IdAndReviewStatusInAndDeletedAtIsNull(
            UUID problemId,
            Collection<ReviewStatus> reviewStatuses,
            Pageable pageable
    );

    Page<Solution> findAllByAuthorIdAndDeletedAtIsNull(
            UUID authorId,
            Pageable pageable
    );

    @Query("""
            SELECT solution
            FROM Solution solution
            WHERE solution.deletedAt IS NULL
              AND (
                    :reviewStatus IS NULL
                    OR solution.reviewStatus = :reviewStatus
              )
            """)
    Page<Solution> findForModeration(
            @Param("reviewStatus")
            ReviewStatus reviewStatus,
            Pageable pageable
    );

    Optional<Solution> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Solution>
    findByIdAndReviewStatusInAndDeletedAtIsNull(
            UUID id,
            Collection<ReviewStatus> reviewStatuses
    );

    boolean
    existsByProblem_IdAndReviewStatusAndDeletedAtIsNullAndIdNot(
            UUID problemId,
            ReviewStatus reviewStatus,
            UUID excludedSolutionId
    );

}
