package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseSubmissionType;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record ShowcaseReviewDetailResponse(
        UUID showcaseId,
        UUID revisionId,
        ShowcaseSubmissionType submissionType,
        UUID authorId,
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
        UUID reviewedBy,
        LocalDateTime reviewedAt,
        String rejectionReason,
        LocalDateTime submittedAt,
        List<ShowcaseStepResponse> steps
) {
}
