package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagGroupRow;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagRow;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagTargetRow;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagTallyRow;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagGroupResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagReporterSummary;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagTargetPreview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns a queue row into the shape the moderation screen reads.
 *
 * <p>One place, because the flat queue, the grouped queue, a single flag and
 * the reporter's own list are all the same row: two of them would drift, and
 * the pair that drifts is the one where the card says the post is live and the
 * detail page says it was removed.
 */
@Component
@RequiredArgsConstructor
public class FlagRowAssembler {

    /**
     * How much of the reported writing travels with the report.
     *
     * <p>Enough to recognise spam, an insult or a recipe without opening
     * anything, and short enough that a page of twenty is still one small
     * response. A moderator who needs more follows the link.
     */
    private static final int SNIPPET_LENGTH = 280;

    private final FlagTargetUrls targetUrls;

    /**
     * @param withSiblingCounts whether to say how many other reports this
     *                          content has drawn. False outside the moderation
     *                          queue — see {@link FlagResponse}
     */
    public FlagResponse toResponse(FlagRow row, boolean withSiblingCounts) {
        FlaggableType type = FlaggableType.valueOf(row.getFlaggableType());
        return new FlagResponse(
                row.getId(),
                FlagSource.valueOf(row.getSource()),
                reporter(row),
                type,
                row.getFlaggableId(),
                FlagReason.valueOf(row.getReason()),
                row.getDescription(),
                FlagStatus.valueOf(row.getStatus()),
                preview(row, type, row.getFlaggableId()),
                withSiblingCounts ? row.getReportCount() : null,
                withSiblingCounts ? row.getPendingCount() : null,
                withSiblingCounts ? reasons(row) : null,
                row.getReviewedBy(),
                row.getReviewedAt(),
                row.getResolutionNote(),
                row.getCreatedAt()
        );
    }

    public FlagGroupResponse toGroup(FlagGroupRow row) {
        FlaggableType type = FlaggableType.valueOf(row.getFlaggableType());
        return new FlagGroupResponse(
                type,
                row.getFlaggableId(),
                preview(row, type, row.getFlaggableId()),
                row.getReportCount(),
                row.getPendingCount(),
                reasons(row),
                Boolean.TRUE.equals(row.getAutomated()),
                row.getFirstReportedAt(),
                row.getLastReportedAt(),
                row.getLatestFlagId()
        );
    }

    private FlagReporterSummary reporter(FlagRow row) {
        if (row.getReporterId() == null) {
            return null;
        }
        return new FlagReporterSummary(
                row.getReporterId(),
                row.getReporterName(),
                row.getReporterAvatarUrl(),
                row.getReporterReputation()
        );
    }

    /**
     * Never null, even when the content is gone: see {@link FlagTargetPreview}.
     */
    private FlagTargetPreview preview(
            FlagTargetRow row,
            FlaggableType type,
            UUID targetId
    ) {
        // The row is NOT NULL in all five tables, so its absence means the
        // lateral matched nothing — the content was hard-deleted rather than
        // hidden, and there is nothing left to describe.
        boolean missing = row.getTargetCreatedAt() == null;
        if (missing) {
            return new FlagTargetPreview(
                    null,
                    null,
                    null,
                    null,
                    null,
                    FlagTargetStatus.DELETED,
                    null,
                    null,
                    null
            );
        }

        FlagTargetStatus status = status(
                type,
                row.getTargetState(),
                Boolean.TRUE.equals(row.getTargetDeleted())
        );
        return new FlagTargetPreview(
                row.getTargetTitle(),
                snippet(row.getTargetBody()),
                row.getTargetAuthorId(),
                row.getTargetAuthorName(),
                row.getTargetAuthorAvatarUrl(),
                status,
                row.getTargetCreatedAt(),
                row.getTargetThumbnailUrl(),
                status == FlagTargetStatus.DELETED
                        ? null
                        : targetUrls.directUrl(
                                type,
                                targetId,
                                row.getTargetSlug(),
                                row.getTargetParentType(),
                                row.getTargetParentId()
                        )
        );
    }

    /**
     * Five status vocabularies reduced to the one question a moderator is
     * asking. See {@link FlagTargetStatus}.
     *
     * <p>An unrecognised value reads as {@link FlagTargetStatus#PUBLISHED}
     * rather than throwing. A status added to one of the five content kinds
     * must not be able to take the whole moderation queue down, and the queue
     * exists to put a human in front of the content either way.
     */
    private FlagTargetStatus status(
            FlaggableType type,
            String state,
            boolean deleted
    ) {
        if (deleted) {
            return FlagTargetStatus.DELETED;
        }
        if (state == null) {
            return FlagTargetStatus.PUBLISHED;
        }
        return switch (type) {
            case PROBLEM -> switch (state) {
                case "DRAFT" -> FlagTargetStatus.DRAFT;
                case "PENDING_APPROVAL" -> FlagTargetStatus.PENDING;
                case "REJECTED" -> FlagTargetStatus.REJECTED;
                // PUBLISHED, RESOLVED and CLOSED are all still on the site.
                default -> FlagTargetStatus.PUBLISHED;
            };
            case SHOWCASE, SOLUTION -> switch (state) {
                case "PENDING" -> FlagTargetStatus.PENDING;
                case "REJECTED" -> FlagTargetStatus.REJECTED;
                default -> FlagTargetStatus.PUBLISHED;
            };
            case COMMENT -> "REMOVED".equals(state)
                    ? FlagTargetStatus.REMOVED
                    : FlagTargetStatus.PUBLISHED;
            case PROGRAM -> switch (state) {
                case "draft" -> FlagTargetStatus.DRAFT;
                // Paused and closed programs stay readable to anyone holding
                // the link, so they are still up as far as moderation goes.
                default -> FlagTargetStatus.PUBLISHED;
            };
        };
    }

    /**
     * The opening of the reported writing, on one line.
     *
     * <p>Whitespace is collapsed because the sources are markdown bodies and
     * comment text, and a snippet that starts with a fenced code block renders
     * as an empty card. Cut on a word where one is near the limit.
     */
    private String snippet(String body) {
        if (body == null) {
            return null;
        }
        String collapsed = body.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return null;
        }
        if (collapsed.length() <= SNIPPET_LENGTH) {
            return collapsed;
        }
        String cut = collapsed.substring(0, SNIPPET_LENGTH);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > SNIPPET_LENGTH - 40) {
            cut = cut.substring(0, lastSpace);
        }
        return cut + "…";
    }

    /**
     * The reasons given across every report on this content.
     *
     * <p>Arrives comma-joined from {@code string_agg}; see
     * {@link FlagQueueQueries}. Sorted into enum order so a card's reasons do
     * not reshuffle between page loads on the whim of the aggregate.
     */
    private List<FlagReason> reasons(FlagTallyRow row) {
        String joined = row.getReasons();
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        List<FlagReason> reasons = new ArrayList<>();
        for (String name : joined.split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // A reason retired from the enum but still on old rows must not
            // take the queue down; those rows are exactly the ones a moderator
            // still has to clear.
            FlagReason reason = parse(trimmed);
            if (reason != null && !reasons.contains(reason)) {
                reasons.add(reason);
            }
        }
        reasons.sort(null);
        return List.copyOf(reasons);
    }

    private FlagReason parse(String name) {
        try {
            return FlagReason.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
