package kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ResearcherAccessResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        UUID researcherId,
        String researcherName,
        String researcherEmail,
        ResearcherAccessStatus status,
        boolean canSubmitReports,
        String motivation,
        String reviewNote,
        UUID reviewedBy,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
