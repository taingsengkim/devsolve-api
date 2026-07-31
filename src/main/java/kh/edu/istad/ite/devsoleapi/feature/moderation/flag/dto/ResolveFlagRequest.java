package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveFlagRequest(

        @NotBlank(message = "Resolution note is required")
        @Size(
                max = 2000,
                message = "Resolution note must not exceed 2000 characters"
        )
        String resolutionNote
) {
}
