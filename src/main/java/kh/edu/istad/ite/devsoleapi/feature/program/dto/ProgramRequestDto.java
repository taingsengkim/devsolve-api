package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardRequestDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;


@Builder
public record ProgramRequestDto(
        @NotBlank String handle,
        @NotBlank String name,
        String description,
        @NotNull EngagementType engagementType,
        @NotNull Visibility visibility,
        String currency,
        String policy,
        Boolean offersBounties,
        BigDecimal minimumBounty,
        BigDecimal maximumBounty,
        List<ProgramAssetRequestDto> assets,
        List<ProgramRewardRequestDto> rewards
) {}