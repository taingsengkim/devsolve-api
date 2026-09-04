package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * The autosave payload. Everything is optional — a draft saved after one
 * keystroke is still a draft worth keeping — and only length caps are enforced,
 * so that a save can never fail for a reason the author has not finished
 * causing yet.
 *
 * <p>The caps match the columns the draft will eventually be written to. A
 * draft that could hold a 600-character URL would be a draft that autosaves
 * happily and then cannot be posted.
 */
public record SaveShowcaseDraftRequest(

        UUID categoryId,

        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        String overview,

        @Size(max = 500, message = "Cover image URL must be at most 500 characters")
        String coverImageUrl,

        @Size(max = 500, message = "Live URL must be at most 500 characters")
        String liveUrl,

        @Size(max = 500, message = "Repository URL must be at most 500 characters")
        String repoUrl,

        @Size(max = 500, message = "Video URL must be at most 500 characters")
        String videoUrl,

        @Size(max = 20, message = "A showcase can carry at most 20 tags")
        List<UUID> tagIds,

        @Size(max = 20, message = "A showcase can carry at most 20 tags")
        List<@Size(max = 50, message = "A tag must be at most 50 characters")
                String> tags
) {
}
