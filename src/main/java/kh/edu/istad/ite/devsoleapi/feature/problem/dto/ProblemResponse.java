package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemSeverity;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemType;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProblemResponse(
        UUID id,
        AuthorSummary author,
        CategorySummary category,
        String title,
        String description,
        ProblemType problemType,
        SdlcPhase sdlcPhase,
        ProblemSeverity severity,
        String expectedBehavior,
        String actualBehavior,
        List<String> reproductionSteps,
        List<EnvironmentSummary> environment,
        String attemptsTried,
        String errorMessage,
        String repositoryUrl,
        ProblemStatus status,
        long viewCount,
        List<TechnologySummary> technologies,
        List<TagSummary> tags,
        List<AttachmentSummary> attachments,
        List<String> contentWarnings,
        long solutionCount,
        long commentCount,
        long voteScore,
        long bookmarkCount,
        UUID acceptedSolutionId,
        boolean isBookmarkedByViewer,
        String viewerVote,
        boolean canEdit,
        boolean canDelete,
        boolean canAcceptSolution,
        Instant publishedAt,
        Instant deletedAt,
        long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record AuthorSummary(
            UUID id,
            String fullName,
            String avatarUrl,
            int reputation
    ) {
    }

    public record CategorySummary(
            UUID id,
            String name,
            String slug,
            CategoryScope scope
    ) {
    }

    public record TechnologySummary(
            UUID id,
            String name,
            String version
    ) {
    }

    public record EnvironmentSummary(
            String technology,
            String version
    ) {
    }

    public record TagSummary(
            UUID id,
            String name,
            String slug
    ) {
    }

    public record AttachmentSummary(
            UUID id,
            String originalFileName,
            String mimeType,
            long sizeBytes,
            UUID uploadedBy,
            Instant createdAt,
            String downloadUrl
    ) {
    }
}
