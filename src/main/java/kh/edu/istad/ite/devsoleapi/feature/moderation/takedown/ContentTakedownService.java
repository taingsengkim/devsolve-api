package kh.edu.istad.ite.devsoleapi.feature.moderation.takedown;

import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationTargetType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;

import java.util.UUID;

/**
 * One way for an administrator to take a piece of content down, whichever kind
 * it is.
 *
 * <p>Before this existed each content type answered "can an admin remove this"
 * differently: a program had its own admin endpoint, a problem and a solution
 * quietly admitted admins through the author's own delete, a comment could only
 * be removed by resolving a flag somebody else had raised, and a showcase could
 * not be removed at all. None of them recorded who did it or why, which is why
 * {@link ModerationTargetType} carried six content values that nothing ever
 * wrote.
 *
 * <p>This does not reimplement any of those removals. Each type keeps the
 * takedown path it already had — the one that knows to cascade a problem's
 * solutions, to tombstone a comment that has replies underneath it, to make a
 * program private as well as deleted. What is added is a single door in front
 * of them and an audit row behind them.
 */
public interface ContentTakedownService {

    /**
     * Removes one piece of content and records the decision.
     *
     * <p>Admin-only, and the removal and the audit row share a transaction: a
     * refusal deeper in the content's own removal path leaves no record of a
     * takedown that did not happen.
     *
     * @param targetType which kind of content. {@code USER} and {@code REPORT}
     *                   are refused — an account is moderated through
     *                   {@code ModerationService}, and a report is evidence in
     *                   a disclosure timeline rather than a post
     * @param reason     why, for the moderation history
     * @throws org.springframework.web.server.ResponseStatusException 403 for a
     *         non-admin, 400 for a target type that cannot be taken down, and
     *         whatever the content's own removal path raises — 404 for content
     *         that is already gone
     */
    ModerationActionResponse takeDown(
            ModerationTargetType targetType,
            UUID targetId,
            String reason
    );
}
