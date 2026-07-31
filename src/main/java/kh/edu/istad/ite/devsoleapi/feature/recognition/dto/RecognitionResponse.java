package kh.edu.istad.ite.devsoleapi.feature.recognition.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecognitionResponse(
        UUID id,
        UUID userId,
        UUID programId,
        UUID reportId,
        String title,
        String description,
        UUID awardedBy,
        LocalDateTime awardedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}