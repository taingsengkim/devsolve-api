package kh.edu.istad.ite.devsoleapi.feature.solution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolutionRevisionRepository
        extends JpaRepository<SolutionRevision, UUID> {

    List<SolutionRevision> findAllBySolution_IdOrderByRevisionNumberDesc(
            UUID solutionId
    );

    Optional<SolutionRevision> findByIdAndSolution_Id(
            UUID revisionId,
            UUID solutionId
    );
}
