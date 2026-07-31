package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseSubmissionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ShowcaseReviewQueueItemResponse(
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
        LocalDateTime submittedAt
) {
}
