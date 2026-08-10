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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID>, JpaSpecificationExecutor<Program> {
    Optional<Program> findByHandle(String handle);

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

    interface PublicProgramStatistics {

        long getTotalResearchers();

        long getTotalSubmissions();
    }
}
