package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record WeaknessResponse(
        UUID id,
        String cweId,
        String name,
        String description,
        Boolean isActive,
        LocalDateTime createdAt
) {
}
