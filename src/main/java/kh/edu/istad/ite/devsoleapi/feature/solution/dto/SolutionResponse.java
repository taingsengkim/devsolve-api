package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ApproachType;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ResourceType;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SolutionResponse(
        UUID id,
        UUID problemId,
        AuthorSummary author,
        String summary,
        String bodyMarkdown,
        ApproachType approachType,
        List<VerificationStep> verificationSteps,
        List<TestedWith> testedWith,
        String tradeoffs,
        List<ResourceSummary> resources,
        List<AttachmentSummary> attachments,
        boolean isAccepted,
        long voteScore,
        long commentCount,
        String viewerVote,
        long version,
        ModerationDetails moderation,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record AuthorSummary(
            UUID id,
            String displayName,
            String avatarUrl
    ) {
    }

    public record VerificationStep(
            String instruction,
            String expectedResult
    ) {
    }

    public record TestedWith(
            String technology,
            String version
    ) {
    }

    public record ResourceSummary(
            UUID id,
            ResourceType type,
            String label,
            String url,
            int displayOrder
    ) {
    }

    public record AttachmentSummary(
            UUID id,
            String fileName,
            String mimeType,
            long fileSize,
            String downloadUrl,
            LocalDateTime createdAt
    ) {
    }

    public record ModerationDetails(
            UUID revisionId,
            int revisionNumber,
            ReviewStatus status,
            String rejectionReason,
            UUID reviewedBy,
            LocalDateTime reviewedAt
    ) {
    }
}
