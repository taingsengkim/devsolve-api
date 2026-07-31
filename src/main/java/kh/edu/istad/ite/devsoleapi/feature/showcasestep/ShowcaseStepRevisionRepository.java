package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowcaseStepRevisionRepository
        extends JpaRepository<ShowcaseStepRevision, UUID> {

    List<ShowcaseStepRevision> findByRevision_IdOrderByStepNumberAsc(
            UUID revisionId
    );

    Optional<ShowcaseStepRevision>
    findByIdAndRevision_Showcase_Id(
            UUID id,
            UUID showcaseId
    );

    Optional<ShowcaseStepRevision>
    findBySourceStepIdAndRevision_Showcase_Id(
            UUID sourceStepId,
            UUID showcaseId
    );

    boolean existsByRevision_IdAndStepNumber(
            UUID revisionId,
            Integer stepNumber
    );

    boolean existsByRevision_IdAndStepNumberAndIdNot(
            UUID revisionId,
            Integer stepNumber,
            UUID id
    );

    void deleteByRevision_Id(UUID revisionId);
}
