package kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Grants access immediately, so {@code userId} has to name an account that
 * already exists — there is nobody to hold a pending grant for otherwise.
 */
public record InviteResearcherRequest(
        @NotNull(message = "Researcher user id is required")
        UUID userId,

        @Size(max = 2000, message = "A note cannot exceed 2000 characters")
        String note
) {
}
