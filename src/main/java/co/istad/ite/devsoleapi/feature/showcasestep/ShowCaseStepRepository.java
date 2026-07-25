package co.istad.ite.devsoleapi.feature.showcasestep;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowCaseStepRepository extends JpaRepository<ShowcaseStep, UUID> {
    List<ShowcaseStep> findByShowcase_IdOrderByStepNumberAsc(UUID showcaseId);

    Optional<ShowcaseStep> findByIdAndShowcase_Id(
            UUID stepId,
            UUID showcaseId
    );
}
