package kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto;


import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Builder;

@Builder
public record ProgramAssetRequestDto(
        @NotNull(message = "Asset type is required")
        AssetType assetType,

        @NotBlank(message = "Asset identifier is required")
        @Size(
                max = 500,
                message = "Asset identifier must not exceed 500 characters"
        )
        String identifier,

        String description,

        @NotNull(message = "Asset scope flag is required")
        Boolean isInScope,

        Severity maxSeverity
) {}
