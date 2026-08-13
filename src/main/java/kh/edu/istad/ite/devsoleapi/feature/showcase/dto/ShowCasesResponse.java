package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/**
 * @param commentCount visible comments on this showcase. Filled in on the read
 *                     paths a reader actually sees; the review and revision
 *                     paths leave it at zero, because a submission still under
 *                     review has no discussion on it yet.
 */
@Builder(toBuilder = true)
public record ShowCasesResponse(
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
        Integer viewCount,
        long commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ShowcaseTagResponse> tags,
        List<ShowcaseStepResponse> steps
) {
}
