package kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param userId the researcher to ask. An ID rather than an email address:
 *               inviting somebody who has no account here would create a
 *               pending invitation nothing can ever accept, and a private
 *               program is not the place to discover that a handle was typed
 *               wrong
 * @param note   what the company wants to say when asking. Optional, and worth
 *               having — an invitation to a private program with no word of
 *               why reads like spam
 */
public record InviteToProgramRequest(

        @NotNull(message = "User ID is required")
        UUID userId,

        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note
) {
}
