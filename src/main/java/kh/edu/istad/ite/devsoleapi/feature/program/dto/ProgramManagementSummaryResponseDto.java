package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetResponseDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
        String description,
        EngagementType engagementType,
        ProgramState state,
        SubmissionState submissionState,
        Visibility visibility,
        Boolean offersBounties,
        BigDecimal maximumBounty,
        List<ProgramAssetResponseDto> assets,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
