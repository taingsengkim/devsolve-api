package co.istad.ite.devsoleapi.feature.program.dto;

import co.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import co.istad.ite.devsoleapi.feature.program.enums.Visibility;
import co.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import co.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardRequestDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ProgramUpdateRequestDto(
        String handle,
        String name,
        String description,
        EngagementType engagementType,
        Visibility visibility,
        String currency,
        String policy,
        Boolean offersBounties,
        BigDecimal minimumBounty,
        BigDecimal maximumBounty,
        List<ProgramAssetRequestDto> assets,
        List<ProgramRewardRequestDto> rewards
) {
}
