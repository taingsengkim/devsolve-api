package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentAutoReviewRepository
        extends JpaRepository<ContentAutoReview, UUID> {

    Optional<ContentAutoReview> findByTargetAndContentId(
            AutoApprovalTarget target,
            UUID contentId
    );

    Page<ContentAutoReview> findByAuthorId(UUID authorId, Pageable pageable);

    Page<ContentAutoReview> findByAuthorIdAndApproved(
            UUID authorId,
            boolean approved,
            Pageable pageable
    );

    Page<ContentAutoReview> findByAuthorIdAndTarget(
            UUID authorId,
            AutoApprovalTarget target,
            Pageable pageable
    );

    Page<ContentAutoReview> findByAuthorIdAndTargetAndApproved(
            UUID authorId,
            AutoApprovalTarget target,
            boolean approved,
            Pageable pageable
    );

    /**
     * Every verdict for a page of posts, so a listing can show why each one is
     * still pending without a query per row.
     */
    List<ContentAutoReview> findByTargetAndContentIdIn(
            AutoApprovalTarget target,
            Collection<UUID> contentIds
    );

    void deleteByTargetAndContentId(
            AutoApprovalTarget target,
            UUID contentId
    );
}
