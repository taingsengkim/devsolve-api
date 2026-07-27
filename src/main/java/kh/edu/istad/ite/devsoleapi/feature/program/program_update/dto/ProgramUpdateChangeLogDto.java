package kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;

@Builder
public record ProgramUpdateChangeLogDto(
        UUID id,
        String changeSummary,
        UUID changedBy,
        LocalDateTime createdAt
) {}
