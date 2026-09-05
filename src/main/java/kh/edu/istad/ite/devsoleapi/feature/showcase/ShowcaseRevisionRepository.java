package kh.edu.istad.ite.devsoleapi.feature.showcase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowcaseRevisionRepository
        extends JpaRepository<ShowcaseRevision, UUID> {

    long countByCategory_Id(UUID categoryId);

    Optional<ShowcaseRevision> findByShowcase_Id(UUID showcaseId);

    /**
     * Whether an edit is waiting on a moderator. Only the author is ever told,
     * so this is read instead of the revision itself: nothing on the detail
     * page renders the pending content, only the fact that it exists.
     */
    boolean existsByShowcase_Id(UUID showcaseId);

    List<ShowcaseRevision> findByShowcase_IdIn(
            Collection<UUID> showcaseIds
    );

    void deleteByShowcase_Id(UUID showcaseId);
}
