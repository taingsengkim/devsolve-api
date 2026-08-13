package kh.edu.istad.ite.devsoleapi.feature.recognition.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

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
        Severity severity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}