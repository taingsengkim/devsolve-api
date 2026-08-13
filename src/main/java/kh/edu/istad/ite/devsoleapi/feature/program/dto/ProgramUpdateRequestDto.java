package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProgramUpdateRequestDto(
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

        @Size(
                min = 2,
                max = 255,
                message = "Program name must be between 2 and 255 characters"
        )
        @Pattern(regexp = ".*\\S.*", message = "Program name must not be blank")
        String name,

        String description,
        EngagementType engagementType,
        Visibility visibility,

        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "Program policy must not be blank"
        )
        String policy,

        @Valid
        ProgramGuidelinesDto proofOfConceptRequirements,

        @Valid
        ProgramGuidelinesDto rulesOfEngagement,

        @Valid
        ProgramGuidelinesDto exclusions,

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

        List<@Valid ProgramAssetRequestDto> assets,
        List<@Valid ProgramRewardRequestDto> rewards
) {
}
