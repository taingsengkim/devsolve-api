package co.istad.ite.devsoleapi.feature.showcase.dto;

import co.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import co.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Builder
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ShowcaseStepResponse> steps
) {
}
