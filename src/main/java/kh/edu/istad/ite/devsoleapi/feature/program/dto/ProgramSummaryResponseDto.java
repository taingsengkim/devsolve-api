package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProgramSummaryResponseDto(
        UUID id,
        UUID organizationId,
        String handle,
        String name,
        String description,
        EngagementType engagementType,
        Boolean offersBounties,
        BigDecimal minimumBounty,
        BigDecimal maximumBounty,
        LocalDateTime updatedAt
) {
}
