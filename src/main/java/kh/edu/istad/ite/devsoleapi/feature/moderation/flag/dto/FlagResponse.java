package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagReason;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagStatus;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlaggableType;


import java.time.LocalDateTime;
import java.util.UUID;

public record FlagResponse(
        UUID id,

        UUID reporterId,

        String reporterName,

        FlaggableType flaggableType,

        UUID flaggableId,

        FlagReason reason,

        String description,

        FlagStatus status,

        UUID reviewedBy,

        LocalDateTime reviewedAt,

        String resolutionNote,

        LocalDateTime createdAt
) {
}
