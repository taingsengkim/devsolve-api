package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProgramManagementSummaryResponseDto(
        UUID id,
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        String organizationLogoUrl,
        String organizationWebsiteUrl,
        OrganizationStatus organizationStatus,
        String handle,
        String name,
        EngagementType engagementType,
        ProgramState state,
        SubmissionState submissionState,
        Visibility visibility,
        Boolean offersBounties,
        BigDecimal maximumBounty,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
