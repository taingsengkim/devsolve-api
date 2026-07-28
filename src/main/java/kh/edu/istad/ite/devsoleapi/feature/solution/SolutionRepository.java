package kh.edu.istad.ite.devsoleapi.feature.solution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface SolutionRepository extends JpaRepository<Solution, UUID> {
    @Query("SELECT s FROM Solution s WHERE s.problem.id = :problemId " +
            "AND s.deletedAt IS NULL " +
            "AND s.reviewStatus IN ('APPROVED', 'ACCEPTED')")
    Page<Solution> findAllVisibleByProblemId(UUID problemId, Pageable pageable);

    Page<Solution> findAllByProblemIdAndDeletedAtIsNull(UUID problemId, Pageable pageable);
    Optional<Solution> findByIdAndDeletedAtIsNull(UUID id);
}