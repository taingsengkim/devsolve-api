package co.istad.ite.devsoleapi.feature.showcasestep.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ShowcaseStepResponse(

        UUID id,
        Integer stepNumber,
        String title,
        String description,
        String codeSnippet,
        String imageUrl,
        String diagramUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {}
