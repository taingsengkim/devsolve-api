package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A showcase in progress. Every field but the identifiers may be null — that is
 * what unfinished means.
 */
public record ShowcaseDraftResponse(
        UUID id,
        UUID authorId,
        UUID categoryId,
        String title,
        String overview,
        String coverImageUrl,
        String liveUrl,
        String repoUrl,
        String videoUrl,
        List<UUID> tagIds,
        List<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
