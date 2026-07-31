package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolutionRequest(
        @NotBlank(message = "Description is required")
        @Size(
                max = 20000,
                message = "Description must not exceed 20000 characters"
        )
        String description,

        // Simple string for now – will be a URL/file path later
        @Size(
                max = 500,
                message = "Video URL must not exceed 500 characters"
        )
        String videoUrl,

        // Simple string for now
        @Size(
                max = 500,
                message = "Diagram URL must not exceed 500 characters"
        )
        String diagramUrl
) {}
