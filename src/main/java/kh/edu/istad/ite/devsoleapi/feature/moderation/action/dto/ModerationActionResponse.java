package kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationActionType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationTargetType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ModerationActionResponse(

        UUID id,

        UUID adminId,

        String adminName,

        ModerationTargetType targetType,

        UUID targetId,

        ModerationActionType action,

        String reason,

        LocalDateTime expiresAt,

        LocalDateTime createdAt
) {
}
