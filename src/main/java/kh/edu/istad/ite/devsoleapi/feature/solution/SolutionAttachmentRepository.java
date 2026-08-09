package kh.edu.istad.ite.devsoleapi.feature.solution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SolutionAttachmentRepository
        extends JpaRepository<SolutionAttachment, UUID> {

    Optional<SolutionAttachment> findByIdAndRevision_Solution_Id(
            UUID attachmentId,
            UUID solutionId
    );

    long countByRevision_Id(UUID revisionId);

    long countByStorageKey(String storageKey);
}
