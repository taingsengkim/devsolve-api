package kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationActionType;
import org.hibernate.tool.schema.TargetType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateModerationActionRequest(

        @NotNull(message = "Moderation action is required")
        ModerationActionType action,

        @NotBlank(message = "Reason is required")
        @Size(
                max = 2000,
                message = "Reason must not exceed 2000 characters"
        )
        String reason,

        @Future(message = "Expiration date must be in the future")
        LocalDateTime expiresAt
) {
}