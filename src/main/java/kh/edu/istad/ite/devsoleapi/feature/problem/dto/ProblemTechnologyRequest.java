package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProblemTechnologyRequest(
        @NotBlank(message = "Technology name is required")
        @Size(max = 100, message = "Technology name cannot exceed 100 characters")
        String name,

        @Size(max = 50, message = "Technology version cannot exceed 50 characters")
        String version
) {
}
