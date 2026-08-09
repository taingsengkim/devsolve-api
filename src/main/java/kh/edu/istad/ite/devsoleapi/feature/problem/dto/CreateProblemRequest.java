package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemSeverity;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemType;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateProblemRequest(
        @NotNull(message = "Category is required")
        UUID categoryId,

        @NotBlank(message = "Title is required")
        @Size(min = 10, max = 180, message = "Title must be between 10 and 180 characters")
        String title,

        SdlcPhase sdlcPhase,

        @NotBlank(message = "Description is required")
        @Size(min = 30, max = 20_000, message = "Description must be between 30 and 20,000 characters")
        String description,

        @Size(max = 20, message = "A problem can contain at most 20 technologies")
        List<@Valid ProblemTechnologyRequest> technologies,

        @Size(max = 10, message = "A problem can contain at most 10 tag IDs")
        Set<UUID> tagIds,

        @Size(max = 10, message = "A problem can contain at most 10 new tag names")
        Set<@NotBlank(message = "New tag names cannot be blank")
                @Size(max = 50, message = "New tag names cannot exceed 50 characters") String> newTagNames,

        @NotNull(message = "Problem type is required")
        ProblemType problemType,

        @Size(max = 5_000, message = "Expected behavior cannot exceed 5,000 characters")
        String expectedBehavior,

        @Size(max = 5_000, message = "Actual behavior cannot exceed 5,000 characters")
        String actualBehavior,

        @Size(max = 20, message = "A problem can contain at most 20 reproduction steps")
        List<@NotBlank(message = "Reproduction steps cannot be blank")
                @Size(max = 1_000, message = "A reproduction step cannot exceed 1,000 characters") String> reproductionSteps,

        @Size(max = 20, message = "A problem can contain at most 20 environment entries")
        List<@Valid ProblemEnvironmentRequest> environment,

        @Size(max = 5_000, message = "Attempts tried cannot exceed 5,000 characters")
        String attemptsTried,

        @Size(max = 10_000, message = "Error message cannot exceed 10,000 characters")
        String errorMessage,

        ProblemSeverity severity,

        @Size(max = 1_000, message = "Repository URL cannot exceed 1,000 characters")
        @Pattern(
                regexp = "(?i)^https://[^\\s]+$",
                message = "Repository URL must use HTTPS"
        )
        String repositoryUrl
) {
    /** Source-compatible constructor for callers created before context fields. */
    public CreateProblemRequest(
            UUID categoryId,
            String title,
            SdlcPhase sdlcPhase,
            String description,
            List<ProblemTechnologyRequest> technologies,
            Set<UUID> tagIds,
            Set<String> newTagNames
    ) {
        this(
                categoryId,
                title,
                sdlcPhase,
                description,
                technologies,
                tagIds,
                newTagNames,
                ProblemType.GENERAL,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null
        );
    }
}
