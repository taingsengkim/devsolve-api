package kh.edu.istad.ite.devsoleapi.feature.solution;

import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolutionRepository extends JpaRepository<Solution, UUID> {

    Page<Solution>
    findAllByProblem_IdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
            UUID problemId,
            Pageable pageable
    );

    Page<Solution> findAllByAuthorIdAndDeletedAtIsNull(
            UUID authorId,
            Pageable pageable
    );

    /**
     * Published solution counts for a whole page of problems in one round
     * trip. Problems with no published solution are absent from the result.
     */
    @Query("""
            select solution.problem.id as id, count(solution) as total
            from Solution solution
            where solution.problem.id in :problemIds
              and solution.currentPublishedRevision is not null
              and solution.deletedAt is null
            group by solution.problem.id
            """)
    List<IdCountProjection> countPublishedByProblemIds(
            @Param("problemIds") Collection<UUID> problemIds
    );

    Page<Solution>
    findAllByAuthorIdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
            UUID authorId,
            Pageable pageable
    );

    @Query("""
            select solution
            from Solution solution
            join solution.latestRevision revision
            where solution.deletedAt is null
              and (:reviewStatus is null or revision.moderationStatus = :reviewStatus)
            """)
    Page<Solution> findForModeration(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            Pageable pageable
    );

    @Query("""
            select count(solution)
            from Solution solution
            join solution.latestRevision revision
            where solution.deletedAt is null
              and revision.moderationStatus = :reviewStatus
            """)
    long countForModeration(
            @Param("reviewStatus") ReviewStatus reviewStatus
    );

    Optional<Solution> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Solution>
    findByIdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(UUID id);

    long countByProblem_IdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
            UUID problemId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select solution
            from Solution solution
            where solution.id = :id and solution.deletedAt is null
            """)
    Optional<Solution> findActiveByIdForUpdate(@Param("id") UUID id);

    /*
     * Compatibility query for target-access services while callers migrate
     * away from treating acceptance as a review status. The status argument
     * is intentionally ignored: publication is represented by the pointer.
     */
    @Query("""
            select solution
            from Solution solution
            join solution.currentPublishedRevision revision
            where solution.id = :id
              and revision.moderationStatus in :reviewStatuses
              and solution.deletedAt is null
            """)
    Optional<Solution> findByIdAndReviewStatusInAndDeletedAtIsNull(
            @Param("id") UUID id,
            @Param("reviewStatuses") Collection<ReviewStatus> reviewStatuses
    );
}
