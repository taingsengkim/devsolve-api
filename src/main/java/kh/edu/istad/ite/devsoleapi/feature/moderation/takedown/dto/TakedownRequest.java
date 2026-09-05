package kh.edu.istad.ite.devsoleapi.feature.moderation.takedown.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why a piece of content is coming down.
 *
 * <p>Required, and deliberately so. A takedown is the one moderation act with
 * no undo on this platform — a removed problem cannot be restored — and the
 * only record of why it happened is the row this writes. An optional reason
 * becomes an empty one, and six months later nobody can answer the author's
 * question.
 *
 * @param reason shown in the moderation history, not to the author
 */
public record TakedownRequest(

        @NotBlank(message = "A takedown reason is required")
        @Size(
                max = 2000,
                message = "The reason must not exceed 2000 characters"
        )
        String reason
) {
}
