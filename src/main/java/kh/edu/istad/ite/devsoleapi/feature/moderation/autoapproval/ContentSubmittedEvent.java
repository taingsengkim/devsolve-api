package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import java.util.UUID;

/**
 * A post has just entered the review queue.
 *
 * <p>Carries the prose rather than only the id so the reviewer does not have to
 * re-read the row on another thread, and so each service decides for itself
 * which of its fields were written by the author. A pasted stack trace is
 * output, not writing, and judging somebody by what their tooling printed is
 * how a security write-up gets held for the contents of a log line.
 */
public record ContentSubmittedEvent(
        AutoApprovalTarget target,
        UUID contentId,
        String title,
        String prose
) {
}
