package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramHandlePolicy;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;


/**
 * A new program, complete or not.
 *
 * <p>Only {@code handle} and {@code name} are required, because they are what
 * identifies the row; everything else may be left out and filled in later.
 * Completeness is checked at submission — {@code PATCH /programs/{id}/submit},
 * or creating with {@code ?submit=true} — not here.
 *
 * <p>That split exists because a wizard with a "Save draft" button on every
 * step otherwise has to satisfy the whole published-program contract on step
 * one, and the only way to do that is to invent the fields the author has not
 * written yet. An invented policy that survives to publication is the policy
 * researchers are bound by, so the API would rather be handed nothing.
 *
 * <p>What is supplied is still checked: a handle that is not a legal handle,
 * or a rule over 500 characters, is refused whether or not the program is a
 * draft. Absence is what a draft is allowed; nonsense is not.
 */
@Schema(description = "A program to create. Only handle and name are required;"
        + " completeness is enforced at submission.")
@Builder
public record ProgramRequestDto(
        @NotBlank(message = "Program handle is required")
        @Size(
                min = ProgramHandlePolicy.MIN_LENGTH,
                max = ProgramHandlePolicy.MAX_LENGTH,
                message = ProgramHandlePolicy.LENGTH_MESSAGE
        )
        @Pattern(
                regexp = ProgramHandlePolicy.FORMAT,
                message = ProgramHandlePolicy.FORMAT_MESSAGE
        )
        @Schema(description = "The program's public URL segment, unique across"
                + " every program. Check it with GET /api/v1/organizations/me"
                + "/programs/handle-available before submitting.")
        String handle,

        @NotBlank(message = "Program name is required")
        @Size(
                min = 2,
                max = 255,
                message = "Program name must be between 2 and 255 characters"
        )
        String name,

        String description,

        @Schema(nullable = true, description = "Required at submission.")
        EngagementType engagementType,

        @Schema(
                nullable = true,
                description = "Defaults to PRIVATE, which is what an "
                        + "unfinished program is."
        )
        Visibility visibility,

        /**
         * {@code DRAFT} or {@code ACTIVE} only, defaulting to {@code DRAFT}.
         * A draft becomes {@code ACTIVE} when it is submitted. Choosing
         * {@code ACTIVE} here submits it immediately; neither path skips admin
         * review.
         */
        ProgramState state,

        @Schema(nullable = true, description = "Required at submission.")
        String policy,

        @Valid
        @Schema(nullable = true, description = "Required at submission.")
        ProgramGuidelinesDto proofOfConceptRequirements,

        @Valid
        @Schema(nullable = true, description = "Required at submission.")
        ProgramGuidelinesDto rulesOfEngagement,

        @Valid
        @Schema(nullable = true, description = "Required at submission.")
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

        @Schema(
                nullable = true,
                description = "At least one asset is required at submission."
        )
        List<@Valid ProgramAssetRequestDto> assets,

        List<@Valid ProgramRewardRequestDto> rewards
) {}
