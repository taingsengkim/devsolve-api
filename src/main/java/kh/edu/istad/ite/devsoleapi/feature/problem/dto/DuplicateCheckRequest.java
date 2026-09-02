package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A problem draft, offered up to be checked against what is already on the
 * platform.
 *
 * <p>A body rather than query parameters, which is why this endpoint is a POST
 * on something that changes nothing: a description runs to thousands of
 * characters and does not belong in a URL, in an access log, or in a proxy's
 * cache key.
 *
 * @param description optional — a title alone is enough to check, and the panel
 *                    opens long before the description is written. Supplying it
 *                    is what makes the answer good.
 * @param excludeId   the problem being edited, so it cannot match itself. Null
 *                    for a draft that has never been saved.
 */
public record DuplicateCheckRequest(

        @NotBlank
        @Size(max = 180)
        String title,

        @Size(max = 20_000)
        @Schema(description = "Clipped before it reaches the model; sending the whole thing is fine.")
        String description,

        UUID excludeId
) {
}
