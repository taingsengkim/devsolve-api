package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowCaseStepRepository extends JpaRepository<ShowcaseStep, UUID> {
    List<ShowcaseStep> findByShowcase_IdOrderByStepNumberAsc(UUID showcaseId);

    Optional<ShowcaseStep> findByIdAndShowcase_IdAndShowcase_DeletedAtIsNull(
            UUID stepId,
            UUID showcaseId
    );

    boolean existsByShowcase_IdAndStepNumber(
            UUID showcaseId,
            Integer stepNumber
    );

    boolean existsByShowcase_IdAndStepNumberAndIdNot(
            UUID showcaseId,
            Integer stepNumber,
            UUID stepId
    );

    void deleteByShowcase_Id(UUID showcaseId);
}
