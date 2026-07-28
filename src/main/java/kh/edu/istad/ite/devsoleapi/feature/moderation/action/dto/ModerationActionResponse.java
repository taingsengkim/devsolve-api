package kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationActionType;
import org.hibernate.tool.schema.TargetType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ModerationActionResponse(
        UUID id,

        UUID adminId,

        TargetType targetType,

        UUID targetId,

        ModerationActionType action,

        String reason,

        LocalDateTime expiresAt,

        LocalDateTime createdAt
) {
}
