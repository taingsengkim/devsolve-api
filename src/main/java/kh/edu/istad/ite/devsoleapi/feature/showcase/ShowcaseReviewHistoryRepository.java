package kh.edu.istad.ite.devsoleapi.feature.showcase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowcaseReviewHistoryRepository
        extends JpaRepository<ShowcaseReviewHistory, UUID> {

    Page<ShowcaseReviewHistory>
    findByShowcaseIdOrderByReviewedAtDesc(
            UUID showcaseId,
            Pageable pageable
    );
}
