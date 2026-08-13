package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestedWithRequest(
        @NotBlank(message = "Tested technology is required")
        @Size(max = 100, message = "Tested technology cannot exceed 100 characters")
        String technology,

        @Size(max = 50, message = "Tested version cannot exceed 50 characters")
        String version
) {
}
