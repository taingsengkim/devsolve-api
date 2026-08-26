package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new entry in the weakness catalog. Only an administrator can add one:
 * the field is a closed vocabulary so that reports can be counted by class,
 * and a catalog anyone could write into would be four spellings of XSS.
 *
 * <p>{@code cweId} is accepted as "CWE-79", "cwe 79" or "79" and stored
 * canonically. It is optional, for the occasional class that has no CWE of
 * its own.
 */
public record CreateWeaknessRequest(

        @Size(
                max = 20,
                message = "CWE identifier must not exceed 20 characters"
        )
        String cweId,

        @NotBlank(message = "Name is required")
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
