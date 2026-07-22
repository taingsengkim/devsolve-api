package co.istad.ite.devsoleapi.feature.program.program_asset.dto;


import co.istad.ite.devsoleapi.feature.program.enums.AssetType;
import co.istad.ite.devsoleapi.feature.program.enums.Severity;

import lombok.Builder;

@Builder
public record ProgramAssetRequestDto(
        AssetType assetType,
        String identifier,
        String description,
        Boolean isInScope,
        Severity maxSeverity
) {}