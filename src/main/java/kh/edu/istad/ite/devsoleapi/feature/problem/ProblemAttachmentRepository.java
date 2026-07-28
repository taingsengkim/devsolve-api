package kh.edu.istad.ite.devsoleapi.feature.problem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemAttachmentRepository
        extends JpaRepository<ProblemAttachment, UUID> {

    List<ProblemAttachment> findAllByProblemIdOrderByCreatedAtAsc(
            UUID problemId
    );

    Optional<ProblemAttachment> findByIdAndProblemId(
            UUID id,
            UUID problemId
    );
}
