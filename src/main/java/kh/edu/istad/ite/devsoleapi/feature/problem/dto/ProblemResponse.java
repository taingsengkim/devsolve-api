package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
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
        SdlcPhase sdlcPhase,
        ProblemStatus status,
        long viewCount,
        List<TechnologySummary> technologies,
        List<TagSummary> tags,
        List<AttachmentSummary> attachments,
        List<String> contentWarnings,
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
