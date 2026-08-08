package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
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
        List<ShowcaseTagResponse> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
