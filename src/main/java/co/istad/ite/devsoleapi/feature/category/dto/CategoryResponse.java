package co.istad.ite.devsoleapi.feature.category.dto;

import java.time.LocalDateTime;

// --- Response DTO ---
public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        String iconUrl,
        Integer sortOrder,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}