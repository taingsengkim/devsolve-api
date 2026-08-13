package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProgramGuidelinesDto(
        @NotBlank(message = "Guideline description is required")
        @Size(
                max = 2000,
                message = "Guideline description must not exceed 2000 characters"
        )
        String description,

        @NotEmpty(message = "At least one guideline rule is required")
        @Size(
                max = 50,
                message = "Guidelines cannot contain more than 50 rules"
        )
        List<
                @NotBlank(message = "Guideline rules must not be blank")
                @Size(
                        max = 500,
                        message = "Each guideline rule must not exceed 500 characters"
                ) String
                > rules
) {
}
