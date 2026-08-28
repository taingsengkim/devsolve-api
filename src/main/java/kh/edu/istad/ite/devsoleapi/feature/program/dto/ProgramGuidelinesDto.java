package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One block of program guidelines.
 *
 * <p>Both parts are optional so that a half-written block can be saved as a
 * draft: an author on step 3 has usually written the description before any of
 * the rules. Both are required at submission, where
 * {@code ProgramServiceImpl.validateGuidelines} names whichever is missing.
 *
 * <p>The caps still apply to whatever is supplied — a draft may be
 * incomplete, not oversized.
 */
public record ProgramGuidelinesDto(
        @Size(
                max = 2000,
                message = "Guideline description must not exceed 2000 characters"
        )
        @Schema(nullable = true, description = "Required at submission.")
        String description,

        @Size(
                max = 50,
                message = "Guidelines cannot contain more than 50 rules"
        )
        @Schema(
                nullable = true,
                description = "At least one rule is required at submission."
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
