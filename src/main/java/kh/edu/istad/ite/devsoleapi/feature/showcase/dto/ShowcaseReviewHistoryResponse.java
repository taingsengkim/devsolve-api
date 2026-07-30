package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseSubmissionType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ShowcaseReviewHistoryResponse(
        UUID id,
        UUID showcaseId,
        UUID revisionId,
        ShowcaseSubmissionType submissionType,
        UUID categoryId,
        String title,
        String overview,
        String coverImageUrl,
        String liveUrl,
        String repoUrl,
        String videoUrl,
        ReviewStatus reviewStatus,
        UUID submittedBy,
        LocalDateTime submittedAt,
        UUID reviewedBy,
        LocalDateTime reviewedAt,
        String rejectionReason
) {
}
