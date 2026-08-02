package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;


@Builder
public record ProgramRequestDto(
        @NotBlank(message = "Program handle is required")
        @Size(
                min = 2,
                max = 100,
                message = "Program handle must be between 2 and 100 characters"
        )
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Program handle must use lowercase letters, numbers, and single hyphens"
        )
        String handle,

        @NotBlank(message = "Program name is required")
        @Size(
                min = 2,
                max = 255,
                message = "Program name must be between 2 and 255 characters"
        )
        String name,

        String description,

        @NotNull(message = "Engagement type is required")
        EngagementType engagementType,

        @NotNull(message = "Visibility is required")
        Visibility visibility,

        @NotBlank(message = "Program policy is required")
        String policy,

        @NotBlank(message = "Proof of concept requirements are required")
        @Size(
                max = 10000,
                message = "Proof of concept requirements must not exceed 10000 characters"
        )
        String proofOfConceptRequirements,

        Boolean offersBounties,

        @DecimalMin(value = "0.00", message = "Minimum bounty cannot be negative")
        @Digits(
                integer = 8,
                fraction = 2,
                message = "Minimum bounty must fit NUMERIC(10,2)"
        )
        BigDecimal minimumBounty,

        @DecimalMin(value = "0.00", message = "Maximum bounty cannot be negative")
        @Digits(
                integer = 8,
                fraction = 2,
                message = "Maximum bounty must fit NUMERIC(10,2)"
        )
        BigDecimal maximumBounty,

        @NotEmpty(message = "At least one program asset is required")
        List<@Valid ProgramAssetRequestDto> assets,

        List<@Valid ProgramRewardRequestDto> rewards
) {}
