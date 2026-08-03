package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardResponseDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PublicProgramResponseDto(
        UUID id,
        UUID organizationId,
        String organizationName,
        String handle,
        String name,
        String description,
        EngagementType engagementType,
        ProgramState state,
        SubmissionState submissionState,
        Visibility visibility,
        String policy,
        String proofOfConceptRequirements,
        ProgramGuidelinesDto rulesOfEngagement,
        ProgramGuidelinesDto exclusions,
        Boolean offersBounties,
        BigDecimal minimumBounty,
        BigDecimal maximumBounty,
        List<ProgramAssetResponseDto> assets,
        List<ProgramRewardResponseDto> rewards,
        long totalResearchers,
        long totalSubmissions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
