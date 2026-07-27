package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProgramRejectionRequest(
        @NotBlank(message = "Rejection reason is required")
        @Size(
                max = 1000,
                message = "Rejection reason must not exceed 1000 characters"
        )
        String reason
) {
}
