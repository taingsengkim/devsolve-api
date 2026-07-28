package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolutionRequest(
        @NotBlank(message = "Description is required")
        String description,

        // Simple string for now – will be a URL/file path later
        String videoUrl,

        // Simple string for now
        String diagramUrl
) {}