package kh.edu.istad.ite.devsoleapi.feature.showcase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowcaseRevisionRepository
        extends JpaRepository<ShowcaseRevision, UUID> {

    Optional<ShowcaseRevision> findByShowcase_Id(UUID showcaseId);

    List<ShowcaseRevision> findByShowcase_IdIn(
            Collection<UUID> showcaseIds
    );

    void deleteByShowcase_Id(UUID showcaseId);
}
