package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateProblemRequest(
        UUID categoryId,

        @NotBlank(message = "Title is required")
        @Size(
                min = 10,
                max = 180,
                message = "Title must be between 10 and 180 characters"
        )
        String title,

        SdlcPhase sdlcPhase,

        @Size(
                max = 20_000,
                message = "Description cannot exceed 20,000 characters"
        )
        String description,

        @Size(
                max = 20,
                message = "A problem can contain at most 20 technologies"
        )
        List<@Valid ProblemTechnologyRequest> technologies,

        @Size(
                max = 10,
                message = "A problem can contain at most 10 tag IDs"
        )
        Set<UUID> tagIds,

        @Size(
                max = 10,
                message = "A problem can contain at most 10 tag names"
        )
        Set<@NotBlank(message = "Tag names cannot be blank")
                @Size(max = 50, message = "Tag names cannot exceed 50 characters")
                String> tags
) {
}
