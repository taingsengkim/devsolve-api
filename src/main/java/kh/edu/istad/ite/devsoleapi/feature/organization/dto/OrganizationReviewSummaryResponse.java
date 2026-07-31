package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrganizationReviewSummaryResponse(
        UUID id,
        String name,
        String slug,
        String websiteUrl,
        Industry industry,
        String companySize,
        String country,
        OrganizationStatus status,
        UUID ownerId,
        String ownerFullName,
        String ownerEmail,
        LocalDateTime createdAt
) {
}
