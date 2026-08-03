package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationReviewDecision;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrganizationReviewHistoryResponse(
        UUID id,
        int submissionVersion,
        OrganizationReviewDecision decision,
        UUID reviewerId,
        String reason,
        LocalDateTime reviewedAt
) {
}
