package kh.edu.istad.ite.devsoleapi.feature.solution.dto;




import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SolutionResponse(
        UUID id,
        UUID problemId,
        UUID authorId,
        String description,
        String videoUrl,
        String diagramUrl,
        ReviewStatus reviewStatus,
        UUID reviewedBy,
        LocalDateTime reviewedAt,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}