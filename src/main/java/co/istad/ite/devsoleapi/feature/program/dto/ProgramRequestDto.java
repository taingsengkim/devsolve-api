package co.istad.ite.devsoleapi.feature.program.dto;

import co.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import co.istad.ite.devsoleapi.feature.program.enums.Visibility;
import co.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import co.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardRequestDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

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