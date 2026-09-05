package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/**
 * @param authorId    the author in the flat shape this record has always had.
 *                    Kept because clients read it; {@link #author()} is the
 *                    same person with everything a profile card needs.
 * @param commentCount visible comments on this showcase. Filled in on the read
 *                     paths a reader actually sees; the review and revision
 *                     paths leave it at zero, because a submission still under
 *                     review has no discussion on it yet.
 * @param author      null everywhere except the detail path. A write path
 *                    answers with the showcase it just changed, and loading a
 *                    follower count to answer "I saved your edit" would be four
 *                    queries nobody reads.
 * @param engagement  null on the same paths and for the same reason
 * @param viewer      null on the same paths. Never null on the detail path, not
 *                    even for a signed-out reader — see
 *                    {@link ShowcaseViewerResponse#anonymous()}
 * @param related     other showcases worth opening next, empty when nothing
 *                    shares a tag or a category with this one
 */
@Builder(toBuilder = true)
public record ShowCasesResponse(
        UUID id,
        String authorId,
        String authorName,
        ShowcaseAuthorResponse author,
        UUID categoryId,
        String categoryName,
        String title,
        String overview,
        String coverImageUrl,
        String liveUrl,
        String repoUrl,
        String videoUrl,
        ReviewStatus reviewStatus,
        Integer viewCount,
        long commentCount,
        ShowcaseEngagementResponse engagement,
        ShowcaseViewerResponse viewer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ShowcaseTagResponse> tags,
        List<ShowcaseStepResponse> steps,
        List<RelatedShowcaseResponse> related
) {
}
