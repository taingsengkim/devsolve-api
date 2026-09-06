package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagReason;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlaggableType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Every report about one piece of content, as one card.
 *
 * <p>Ten people reporting the same spam post is one decision, not ten. The flat
 * queue shows it as ten rows of identical work, and a moderator who resolves
 * the first sees nine more waiting under it. This is that pile, counted.
 *
 * <p>The counts ignore whatever filter produced the card, so a card seen
 * through {@code status=PENDING} still says how many reports the content has
 * drawn in total and how many of those a colleague has already closed.
 *
 * @param reportCount     every report ever raised on this content
 * @param pendingCount    how many are still open — what one action would close
 * @param reasons         the distinct reasons given, in enum order
 * @param automated       whether the content filter is among the reporters
 * @param firstReportedAt when it was first reported; how long this has waited
 * @param latestFlagId    a flag to open for the detail of the newest report
 */
public record FlagGroupResponse(
        FlaggableType flaggableType,

        UUID flaggableId,

        FlagTargetPreview target,

        long reportCount,

        long pendingCount,

        List<FlagReason> reasons,

        boolean automated,

        LocalDateTime firstReportedAt,

        LocalDateTime lastReportedAt,

        UUID latestFlagId
) {
}
