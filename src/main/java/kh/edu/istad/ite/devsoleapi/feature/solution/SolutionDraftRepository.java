package kh.edu.istad.ite.devsoleapi.feature.solution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SolutionDraftRepository
        extends JpaRepository<SolutionDraft, UUID> {

    /**
     * Scoped to the owner in the query rather than checked after loading. A
     * draft is private to the person writing it, and a lookup that can return
     * somebody else's row is one forgotten {@code if} away from leaking it.
     */
    Optional<SolutionDraft> findByIdAndAuthor_Id(UUID id, UUID authorId);

    Page<SolutionDraft> findByAuthor_Id(UUID authorId, Pageable pageable);

    Page<SolutionDraft> findByAuthor_IdAndProblemId(
            UUID authorId,
            UUID problemId,
            Pageable pageable
    );

    long countByAuthor_Id(UUID authorId);
}
