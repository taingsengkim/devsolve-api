package kh.edu.istad.ite.devsoleapi.feature.showcase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShowCasesRepository extends JpaRepository<ShowCases, UUID> {


    Page<ShowCases> findByReviewStatusAndDeletedAtIsNull(
            ReviewStatus reviewStatus,
            Pageable pageable
    );

    Page<ShowCases> findByAuthor_IdAndReviewStatusAndDeletedAtIsNull(
            UUID authorId,
            ReviewStatus reviewStatus,
            Pageable pageable
    );

    Optional<ShowCases> findByIdAndDeletedAtIsNull(
            UUID id
    );

    boolean existsByAuthor_IdAndTitleAndDeletedAtIsNull(
            UUID authorId,
            String title
    );

    boolean existsByAuthor_IdAndTitleAndIdNotAndDeletedAtIsNull(
            UUID authorId,
            String title,
            UUID id
    );

    boolean existsByRepoUrl(
            String repoUrl
    );

    boolean existsByLiveUrl(
            String liveUrl
    );
}
