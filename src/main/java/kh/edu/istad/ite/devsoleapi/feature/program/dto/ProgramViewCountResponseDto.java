package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import java.util.UUID;

public record ProgramViewCountResponseDto(
        UUID programId,
        long viewCount
) {
}
