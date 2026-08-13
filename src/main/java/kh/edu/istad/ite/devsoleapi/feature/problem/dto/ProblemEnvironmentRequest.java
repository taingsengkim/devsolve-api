package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProblemEnvironmentRequest(
        @NotBlank(message = "Environment technology is required")
        @Size(max = 100, message = "Environment technology cannot exceed 100 characters")
        String technology,

        @Size(max = 50, message = "Environment version cannot exceed 50 characters")
        String version
) {
}
