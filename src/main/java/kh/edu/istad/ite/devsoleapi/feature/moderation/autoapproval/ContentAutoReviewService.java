package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.dto.ContentAutoReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

/**
 * Reads back what the automatic check decided, for the author it decided about.
 *
 * <p>Author-scoped throughout, and deliberately not readable by anyone else. The
 * stored reason is the model's own sentence about a submission, which on the
 * cases that matter names what gave that submission away — useful to the person
 * who has to fix their post, and a map of the check's edges to anybody else.
 * Administrators already read the same verdicts, with more around them, in the
 * moderation queue.
 */
@Service
@RequiredArgsConstructor
public class ContentAutoReviewService {

    private static final String ADMIN_ROLE = "ADMIN";

    /**
     * Ordering is over when the check ran, and nothing else. The rest of the
     * row is an enum or a sentence, and sorting a queue of pending posts by the
     * wording of their rejection answers nothing.
     */
    private static final Set<String> SORT_PROPERTIES = Set.of("checkedAt");

    private final ContentAutoReviewRepository reviewRepository;

    /**
     * The author's own verdicts, newest first.
     *
     * @param target   one kind of post, or null for all of them
     * @param approved null for every verdict; false is the useful one — the
     *                 posts of theirs that are still waiting on somebody
     */
    @Transactional(readOnly = true)
    public Page<ContentAutoReviewResponse> findMine(
            AutoApprovalTarget target,
            Boolean approved,
            Pageable pageable
    ) {
        UUID authorId = currentUserId();
        Pageable validated = PageableValidator.requireAllowedSort(
                pageable,
                SORT_PROPERTIES
        );

        Page<ContentAutoReview> page;
        if (target == null && approved == null) {
            page = reviewRepository.findByAuthorId(authorId, validated);
        } else if (target == null) {
            page = reviewRepository.findByAuthorIdAndApproved(
                    authorId,
                    approved,
                    validated
            );
        } else if (approved == null) {
            page = reviewRepository.findByAuthorIdAndTarget(
                    authorId,
                    target,
                    validated
            );
        } else {
            page = reviewRepository.findByAuthorIdAndTargetAndApproved(
                    authorId,
                    target,
                    approved,
                    validated
            );
        }
        return page.map(this::toResponse);
    }

    /**
     * The verdict on one post, for its author.
     *
     * <p>404 rather than 403 when somebody else asks. Whether a given post was
     * held, and for which of the three reasons, is not something a stranger gets
     * to learn by watching which status code comes back.
     */
    @Transactional(readOnly = true)
    public ContentAutoReviewResponse findOne(
            AutoApprovalTarget target,
            UUID contentId
    ) {
        ContentAutoReview review = reviewRepository
                .findByTargetAndContentId(target, contentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This " + AutoApprovalWording.noun(target)
                                + " has not been checked automatically"
                ));

        if (!AuthUtils.hasRole(ADMIN_ROLE)
                && !currentUserId().equals(review.getAuthorId())) {
            throw new ResourceNotFoundException(
                    "This " + AutoApprovalWording.noun(target)
                            + " has not been checked automatically"
            );
        }
        return toResponse(review);
    }

    private ContentAutoReviewResponse toResponse(ContentAutoReview review) {
        return new ContentAutoReviewResponse(
                review.getTarget(),
                review.getContentId(),
                review.getTitle(),
                AutoApprovalStatus.of(review),
                review.getHold(),
                review.getReason(),
                AutoApprovalWording.message(
                        review.getTarget(),
                        review.isApproved(),
                        review.getHold(),
                        review.getTitle(),
                        review.getReason()
                ),
                review.getCheckedAt()
        );
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }
}
