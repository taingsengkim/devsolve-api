package kh.edu.istad.ite.devsoleapi.feature.category.dto;

import java.time.LocalDateTime;
import java.util.UUID;

// --- Response DTO ---
public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String iconUrl,
        Integer sortOrder,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}