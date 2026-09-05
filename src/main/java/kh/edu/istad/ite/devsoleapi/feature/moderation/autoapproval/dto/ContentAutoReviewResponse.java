package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.AutoApprovalHold;
import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.AutoApprovalStatus;
import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.AutoApprovalTarget;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What the automatic check made of one post, for the person who wrote it.
 *
 * @param status  the one word to branch on. {@code NOT_CHECKED} means the
 *                automation never ran, so nothing here is a judgement of the
 *                writing
 * @param hold    which of the three reasons held it, or null when it was
 *                approved or never checked
 * @param reason  the check's own sentence, unedited. Null when there is none —
 *                the model was not reached, or gave no wording
 * @param message the whole thing said in one paragraph, already addressed to
 *                the author. A client that wants to render this without
 *                branching on anything above can show only this
 */
public record ContentAutoReviewResponse(
        AutoApprovalTarget target,
        UUID contentId,
        String title,
        AutoApprovalStatus status,
        AutoApprovalHold hold,
        String reason,
        String message,
        LocalDateTime checkedAt
) {
}
