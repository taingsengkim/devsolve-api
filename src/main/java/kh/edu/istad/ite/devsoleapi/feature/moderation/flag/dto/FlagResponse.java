package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagReason;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagSource;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagStatus;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlaggableType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One report, with the thing it is about.
 *
 * @param reporter                    null when the content filter raised this
 *                                    rather than a person
 * @param target                      never null; see {@link FlagTargetPreview}
 * @param reportCountOnTarget         how many reports this content has drawn in
 *                                    all, this one included — the closest thing
 *                                    to urgency the queue has. Counted across
 *                                    every status, so it does not fall as
 *                                    colleagues work through the same pile.
 *                                    Null outside the moderation queue: how
 *                                    many others reported the same post is not
 *                                    something to tell the person who reported
 *                                    it, who could read it as a score to game
 * @param pendingReportCountOnTarget  how many of those are still open, which is
 *                                    what a single decision here would close.
 *                                    Null for the same reason
 * @param allReasons                  the distinct reasons given across all of
 *                                    them. Null for the same reason
 */
public record FlagResponse(
        UUID id,

        FlagSource source,

        FlagReporterSummary reporter,

        FlaggableType flaggableType,

        UUID flaggableId,

        FlagReason reason,

        String description,

        FlagStatus status,

        FlagTargetPreview target,

        Long reportCountOnTarget,

        Long pendingReportCountOnTarget,

        List<FlagReason> allReasons,

        UUID reviewedBy,

        LocalDateTime reviewedAt,

        String resolutionNote,

        LocalDateTime createdAt
) {
}
