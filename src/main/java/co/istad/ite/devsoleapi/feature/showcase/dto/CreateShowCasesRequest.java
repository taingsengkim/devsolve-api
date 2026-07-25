package co.istad.ite.devsoleapi.feature.showcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateShowCasesRequest(
        UUID categoryId,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @NotBlank(message = "Overview is required")
        String overview,

        String coverImageUrl,
        String liveUrl,
        String repoUrl,
        String videoUrl
) {
}
