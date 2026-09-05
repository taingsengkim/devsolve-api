package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Writes down what the check decided, so the post can show it afterwards.
 *
 * <p>Every outcome, including the ones the author is not notified about. A
 * notification is a moment; this is what the pending post points at for as long
 * as it is pending, and "the automatic check is switched off" is a perfectly
 * good answer to why a post is sitting in a queue — it is just not worth
 * interrupting somebody with.
 *
 * <p>{@code REQUIRES_NEW} for the same reason {@link AutoApprovalHoldNotifier}
 * dispatches rather than publishes: the caller is an after-commit async
 * listener with no transaction of its own, and a {@code REQUIRED} write from
 * there joins a transaction that has already finished and is discarded without
 * an error.
 */
@Service
@RequiredArgsConstructor
public class AutoApprovalRecorder {

    private final ContentAutoReviewRepository reviewRepository;

    /**
     * Replaces the verdict on this post, if there was one.
     *
     * <p>An author who edits a pending post has it checked again, and the
     * question the row answers — why is this post where it is now — has exactly
     * one current answer. The superseded verdict is not history worth keeping:
     * it describes writing that no longer exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            ContentSubmittedEvent event,
            AutoApprovalDecision decision
    ) {
        ContentAutoReview review = reviewRepository
                .findByTargetAndContentId(event.target(), event.contentId())
                .orElseGet(() -> ContentAutoReview.builder()
                        .target(event.target())
                        .contentId(event.contentId())
                        .build());

        review.setAuthorId(event.authorId());
        review.setTitle(clip(event.title()));
        review.setApproved(decision.approved());
        review.setHold(decision.hold());
        review.setReason(decision.reason());
        review.setCheckedAt(LocalDateTime.now());

        reviewRepository.saveAndFlush(review);
    }

    /**
     * Drops the verdict on a post whose row is gone outright.
     *
     * <p>Only for a hard delete. A soft-deleted post keeps its row and its id,
     * so its verdict still describes something real; a hard-deleted one would
     * leave an author's list of verdicts pointing at a post that no longer
     * exists and cannot be opened.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forget(AutoApprovalTarget target, UUID contentId) {
        reviewRepository.deleteByTargetAndContentId(target, contentId);
    }

    /** The column holds 255; a problem title may hold 180 and a showcase more. */
    private String clip(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }
}
