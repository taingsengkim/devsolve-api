package kh.edu.istad.ite.devsoleapi.feature.problem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemAttachmentRepository
        extends JpaRepository<ProblemAttachment, UUID> {

    List<ProblemAttachment> findAllByProblemIdOrderByCreatedAtAsc(
            UUID problemId
    );

    List<ProblemAttachment> findAllByProblemIdInOrderByCreatedAtAsc(
            Collection<UUID> problemIds
    );

    Optional<ProblemAttachment> findByIdAndProblemId(
            UUID id,
            UUID problemId
    );
}
