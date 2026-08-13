package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificationStepRequest(
        @NotBlank(message = "Verification instruction is required")
        @Size(max = 1_000, message = "Verification instruction cannot exceed 1,000 characters")
        String instruction,

        @NotBlank(message = "Expected result is required")
        @Size(max = 1_000, message = "Expected result cannot exceed 1,000 characters")
        String expectedResult
) {
}
