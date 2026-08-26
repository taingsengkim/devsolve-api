package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.Size;

/**
 * A partial edit. Every field is optional and a null one is left alone, so
 * retiring an entry is {@code {"isActive": false}} and nothing else.
 */
public record UpdateWeaknessRequest(

        @Size(
                max = 20,
                message = "CWE identifier must not exceed 20 characters"
        )
        String cweId,

        @Size(
                max = 255,
                message = "Name must not exceed 255 characters"
        )
        String name,

        @Size(
                max = 2000,
                message = "Description must not exceed 2000 characters"
        )
        String description,

        Boolean isActive
) {
}
