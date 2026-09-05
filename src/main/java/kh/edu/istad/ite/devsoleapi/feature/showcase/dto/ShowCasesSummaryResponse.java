package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param commentCount visible comments on this showcase, so a card can show
 *                     the discussion without fetching a page of it per card.
 * @param engagement   the same counters the detail page shows. A card without
 *                     them could not display the score the feed was sorted by:
 *                     {@code TOP} and {@code TRENDING} order on vote score, so
 *                     leaving it off meant ranking by a number the reader never
 *                     saw.
 * @param viewer       the reader's own state, filled per request and never from
 *                     the listing cache. {@code vote}, {@code bookmarked},
 *                     {@code following}, {@code followingAuthor} and the
 *                     ownership flags are all answered here; only
 *                     {@code editUnderReview} is detail-only — on a card the
 *                     same fact is {@link #hasUnpublishedRevision()}.
 */
@Builder(toBuilder = true)
public record ShowCasesSummaryResponse(
        UUID id,
        String authorId,
        String authorName,
        UUID categoryId,
        String categoryName,
        String title,
        String overview,
        String coverImageUrl,
        String liveUrl,
        String repoUrl,
        String videoUrl,
        ReviewStatus reviewStatus,
        boolean hasUnpublishedRevision,
        String rejectionReason,
        Integer viewCount,
        long commentCount,
        ShowcaseEngagementResponse engagement,
        ShowcaseViewerResponse viewer,
        List<ShowcaseTagResponse> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
