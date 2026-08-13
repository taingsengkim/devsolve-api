package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID>, JpaSpecificationExecutor<Program> {
    Optional<Program> findByHandle(String handle);

    long countByOrganizationIdAndStateAndDeletedAtIsNull(
            UUID organizationId,
            ProgramState state
    );

    long countByOrganizationIdAndStateAndVisibilityAndDeletedAtIsNull(
            UUID organizationId,
            ProgramState state,
            Visibility visibility
    );

    Optional<Program>
    findByIdAndStateAndSubmissionStateAndVisibilityAndDeletedAtIsNull(
            UUID id,
            ProgramState state,
            SubmissionState submissionState,
            Visibility visibility
    );

    boolean existsByHandleIgnoreCase(String handle);

    boolean existsByHandleIgnoreCaseAndIdNot(String handle, UUID id);

    Page<Program> findAll(
            Specification<Program> specification,
            Pageable pageable
    );

    @Query("""
            select count(distinct report.reporter.id) as totalResearchers,
                   count(report.id) as totalSubmissions
            from Report report
            where report.program.id = :programId
            """)
    PublicProgramStatistics findPublicStatisticsByProgramId(
            @Param("programId") UUID programId
    );

    @Query("""
            select count(program) as totalPrograms,
                   coalesce(sum(case when program.state = :draftState
                       then 1 else 0 end), 0) as draftPrograms,
                   coalesce(sum(case when program.state = :activeState
                       then 1 else 0 end), 0) as activePrograms,
                   coalesce(sum(case when program.state = :pausedState
                       then 1 else 0 end), 0) as pausedPrograms,
                   coalesce(sum(case when program.state = :closedState
                       then 1 else 0 end), 0) as closedPrograms,
                   coalesce(sum(case when program.submissionState =
                       :pendingSubmissionState
                       then 1 else 0 end), 0) as pendingReviewPrograms
            from Program program
            where program.deletedAt is null
            """)
    AdminProgramCounts findAdminCounts(
            @Param("draftState") ProgramState draftState,
            @Param("activeState") ProgramState activeState,
            @Param("pausedState") ProgramState pausedState,
            @Param("closedState") ProgramState closedState,
            @Param("pendingSubmissionState")
            SubmissionState pendingSubmissionState
    );

    List<Program> findByOrganizationId(UUID organizationId);

    interface PublicProgramStatistics {

        long getTotalResearchers();

        long getTotalSubmissions();
    }

    interface AdminProgramCounts {

        long getTotalPrograms();

        long getDraftPrograms();

        long getActivePrograms();

        long getPausedPrograms();

        long getClosedPrograms();

        long getPendingReviewPrograms();
    }
}
