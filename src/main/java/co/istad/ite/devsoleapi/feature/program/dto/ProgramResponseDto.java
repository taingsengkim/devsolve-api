package co.istad.ite.devsoleapi.feature.program.dto;

import co.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import co.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import co.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import co.istad.ite.devsoleapi.feature.program.enums.Visibility;
import co.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import co.istad.ite.devsoleapi.feature.program.program_reward.ProgramReward;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record ProgramResponseDto(
        UUID id,
        UUID organizationId,
        String handle,
        String name,
        String description,
        EngagementType engagementType,
        ProgramState state,
        SubmissionState submissionState,
        Visibility visibility,
        String currency,
        String policy,
        Boolean offersBounties,
        BigDecimal minimumBounty,
        BigDecimal maximumBounty,
        LocalDateTime startedAcceptingAt,
        List<ProgramAsset> assets,
        List<ProgramReward> rewards
) {}