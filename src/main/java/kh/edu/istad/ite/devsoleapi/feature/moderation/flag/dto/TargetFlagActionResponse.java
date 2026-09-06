package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlaggableType;

import java.util.UUID;

/**
 * What one decision about one piece of content settled.
 *
 * <p>The answer to a card in the grouped queue. Ten reports about the same post
 * deserve one judgement, and closing them one at a time means nine more chances
 * to lose track of which have been dealt with — or to take the same content
 * down twice.
 *
 * @param affected       how many open reports this closed. Zero when a
 *                       colleague got there first, which is a race worth
 *                       reporting rather than failing over
 * @param contentRemoved whether the content itself came down. False when the
 *                       admin only closed the reports
 */
public record TargetFlagActionResponse(
        FlaggableType flaggableType,

        UUID flaggableId,

        int affected,

        boolean contentRemoved
) {
}
