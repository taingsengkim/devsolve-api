package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.constraints.Size;

public record SolutionUpdateRequest(
        @Size(
                max = 20000,
                message = "Description must not exceed 20000 characters"
        )
        String description,

        @Size(
                max = 500,
                message = "Video URL must not exceed 500 characters"
        )
        String videoUrl,

        @Size(
                max = 500,
                message = "Diagram URL must not exceed 500 characters"
        )
        String diagramUrl
) {}
