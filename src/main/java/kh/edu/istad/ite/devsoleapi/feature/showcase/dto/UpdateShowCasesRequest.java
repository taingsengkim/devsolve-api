package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateShowCasesRequest(
        UUID categoryId,

        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        String overview,
        String coverImageUrl,
        String liveUrl,
        String repoUrl,
        String videoUrl
) {
}
