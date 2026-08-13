package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetResponseDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProgramSummaryResponseDto(
        UUID id,
        UUID organizationId,
        String organizationName,
        ProgramOrganizationDto organization,
        String handle,
        String name,
        String description,
        EngagementType engagementType,
        Boolean offersBounties,
        BigDecimal minimumBounty,
        BigDecimal maximumBounty,
        List<ProgramAssetResponseDto> inScopeAssets,
        LocalDateTime updatedAt
) {
}
