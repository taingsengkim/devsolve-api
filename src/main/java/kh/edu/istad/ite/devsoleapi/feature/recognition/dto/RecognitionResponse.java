package kh.edu.istad.ite.devsoleapi.feature.recognition.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param programId the bare id, kept beside {@link #program()} because clients
 *                  already key off it
 * @param program   which program the finding was against and who runs it. Null
 *                  only if the program has since been erased outright, which
 *                  soft deletion means does not normally happen
 */
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
        ProgramSummary program,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}