package kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

import java.util.UUID;

public record ProgramAssetResponseDto(
        UUID id,
        AssetType assetType,
        String identifier,
        String description,
        Boolean isInScope,
        Severity maxSeverity
) {
}
