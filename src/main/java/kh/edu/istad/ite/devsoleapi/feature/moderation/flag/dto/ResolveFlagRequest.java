package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param removeContent whether upholding the flag should also take the
 *                      reported content down. Resolving used to record a
 *                      decision and leave the content exactly where it was,
 *                      which meant a reader who reported something abusive
 *                      watched it stay up after being told the report had been
 *                      reviewed. Defaults to false so an admin who only wants
 *                      to close the report still can.
 */
public record ResolveFlagRequest(

        @NotBlank(message = "Resolution note is required")
        @Size(
                max = 2000,
                message = "Resolution note must not exceed 2000 characters"
        )
        String resolutionNote,

        boolean removeContent
) {
}
