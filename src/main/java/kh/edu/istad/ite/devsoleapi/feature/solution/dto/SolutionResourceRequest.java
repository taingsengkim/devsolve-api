package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ResourceType;

public record SolutionResourceRequest(
        @NotNull(message = "Resource type is required")
        ResourceType type,

        @NotBlank(message = "Resource label is required")
        @Size(max = 150, message = "Resource label cannot exceed 150 characters")
        String label,

        @NotBlank(message = "Resource URL is required")
        @Size(max = 1_000, message = "Resource URL cannot exceed 1,000 characters")
        @Pattern(
                regexp = "(?i)^https://[^\\s]+$",
                message = "Resource URL must use HTTPS"
        )
        String url
) {
}
