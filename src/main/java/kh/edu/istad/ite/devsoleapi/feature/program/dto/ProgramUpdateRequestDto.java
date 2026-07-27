package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardRequestDto;

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
