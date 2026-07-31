package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrganizationReviewResponse(
        UUID id,
        String name,
        String slug,
        String domain,
        String websiteUrl,
        String logoUrl,
        String description,
        Industry industry,
        String companySize,
        String country,
        OrganizationStatus status,
        UUID ownerId,
        String ownerFullName,
        String ownerEmail,
        String ownerJobTitle,
        String joiningReason,
        boolean emailVerified,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
