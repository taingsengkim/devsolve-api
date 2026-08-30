package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        UUID ownerId,
        String name,
        String slug,
        String domain,
        String websiteUrl,
        String logoUrl,
        String coverImageUrl,
        String description,
        Industry industry,
        String companySize,
        String country,
        OrganizationStatus status,
        int submissionVersion,
        String rejectionReason,
        LocalDateTime reviewedAt,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        /*
         * Only the three profile reads carry this. Register, update and the
         * logo endpoints return the same record, and counting programs,
         * reports and payouts on every logo upload would be three queries
         * nobody asked for, so it is null there.
         */
        OrganizationStatsResponse stats
) {
}
