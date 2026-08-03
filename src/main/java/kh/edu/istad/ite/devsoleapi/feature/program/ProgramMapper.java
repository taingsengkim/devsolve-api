package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramGuidelinesDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramManagementSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.ProgramReward;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ProgramMapper {

    private static final int DESCRIPTION_PREVIEW_LENGTH = 240;

    public Program toEntity(ProgramRequestDto request) {
        Program program = Program.builder()
                .handle(normalizeHandle(request.handle()))
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .engagementType(request.engagementType())
                .visibility(request.visibility())
                .policy(request.policy().trim())
                .proofOfConceptRequirements(
                        request.proofOfConceptRequirements().trim()
                )
                .rulesOfEngagement(normalizeGuidelines(
                        request.rulesOfEngagement()
                ))
                .exclusions(normalizeGuidelines(request.exclusions()))
                .offersBounties(
                        request.offersBounties() == null
                                || request.offersBounties()
                )
                .minimumBounty(request.minimumBounty())
                .maximumBounty(request.maximumBounty())
                .build();

        replaceAssets(program, request.assets());
        replaceRewards(program, request.rewards());
        return program;
    }

    public void updateEntity(
            ProgramUpdateRequestDto request,
            Program program
    ) {
        if (request.handle() != null) {
            program.setHandle(normalizeHandle(request.handle()));
        }
        if (request.name() != null) {
            program.setName(request.name().trim());
        }
        if (request.description() != null) {
            program.setDescription(trimToNull(request.description()));
        }
        if (request.engagementType() != null) {
            program.setEngagementType(request.engagementType());
        }
        if (request.visibility() != null) {
            program.setVisibility(request.visibility());
        }
        if (request.policy() != null) {
            program.setPolicy(request.policy().trim());
        }
        if (request.proofOfConceptRequirements() != null) {
            program.setProofOfConceptRequirements(
                    request.proofOfConceptRequirements().trim()
            );
        }
        if (request.rulesOfEngagement() != null) {
            program.setRulesOfEngagement(normalizeGuidelines(
                    request.rulesOfEngagement()
            ));
        }
        if (request.exclusions() != null) {
            program.setExclusions(normalizeGuidelines(
                    request.exclusions()
            ));
        }
        if (request.offersBounties() != null) {
            program.setOffersBounties(request.offersBounties());
        }
        if (request.minimumBounty() != null) {
            program.setMinimumBounty(request.minimumBounty());
        }
        if (request.maximumBounty() != null) {
            program.setMaximumBounty(request.maximumBounty());
        }
        if (request.assets() != null) {
            replaceAssets(program, request.assets());
        }
        if (request.rewards() != null) {
            replaceRewards(program, request.rewards());
        }
    }

    public ProgramResponseDto toResponseDto(Program program) {
        return new ProgramResponseDto(
                program.getId(),
                program.getOrganizationId(),
                program.getHandle(),
                program.getName(),
                program.getDescription(),
                program.getEngagementType(),
                program.getState(),
                program.getSubmissionState(),
                program.getVisibility(),
                program.getPolicy(),
                program.getProofOfConceptRequirements(),
                program.getRulesOfEngagement(),
                program.getExclusions(),
                program.getOffersBounties(),
                program.getMinimumBounty(),
                program.getMaximumBounty(),
                program.getAssets().stream()
                        .map(this::toAssetResponse)
                        .toList(),
                program.getRewards().stream()
                        .map(this::toRewardResponse)
                        .toList(),
                program.getCreatedAt(),
                program.getUpdatedAt()
        );
    }

    public ProgramSummaryResponseDto toSummaryDto(
            Program program,
            String organizationName,
            List<ProgramAsset> inScopeAssets
    ) {
        return new ProgramSummaryResponseDto(
                program.getId(),
                program.getOrganizationId(),
                organizationName,
                program.getHandle(),
                program.getName(),
                descriptionPreview(program.getDescription()),
                program.getEngagementType(),
                program.getOffersBounties(),
                program.getMinimumBounty(),
                program.getMaximumBounty(),
                toInScopeAssetResponses(inScopeAssets),
                program.getUpdatedAt()
        );
    }

    public PublicProgramResponseDto toPublicResponseDto(
            Program program,
            String organizationName,
            List<ProgramAsset> assets,
            long totalResearchers,
            long totalSubmissions
    ) {
        return new PublicProgramResponseDto(
                program.getId(),
                program.getOrganizationId(),
                organizationName,
                program.getHandle(),
                program.getName(),
                program.getDescription(),
                program.getEngagementType(),
                program.getState(),
                program.getSubmissionState(),
                program.getVisibility(),
                program.getPolicy(),
                program.getProofOfConceptRequirements(),
                program.getRulesOfEngagement(),
                program.getExclusions(),
                program.getOffersBounties(),
                program.getMinimumBounty(),
                program.getMaximumBounty(),
                toAssetResponses(assets),
                program.getRewards().stream()
                        .map(this::toRewardResponse)
                        .toList(),
                totalResearchers,
                totalSubmissions,
                program.getCreatedAt(),
                program.getUpdatedAt()
        );
    }

    public ProgramManagementSummaryResponseDto toManagementSummaryDto(
            Program program
    ) {
        return new ProgramManagementSummaryResponseDto(
                program.getId(),
                program.getOrganizationId(),
                program.getHandle(),
                program.getName(),
                program.getEngagementType(),
                program.getState(),
                program.getSubmissionState(),
                program.getVisibility(),
                program.getOffersBounties(),
                program.getMaximumBounty(),
                program.getCreatedAt(),
                program.getUpdatedAt()
        );
    }

    public ProgramUpdateChangeLogDto toUpdateDto(ProgramUpdate update) {
        return new ProgramUpdateChangeLogDto(
                update.getId(),
                update.getChangeSummary(),
                update.getChangedBy(),
                update.getCreatedAt()
        );
    }

    private void replaceAssets(
            Program program,
            List<ProgramAssetRequestDto> requests
    ) {
        program.getAssets().clear();
        if (requests == null) {
            return;
        }

        program.getAssets().addAll(requests.stream()
                .map(request -> ProgramAsset.builder()
                        .program(program)
                        .assetType(request.assetType())
                        .identifier(request.identifier().trim())
                        .description(trimToNull(request.description()))
                        .isInScope(request.isInScope())
                        .maxSeverity(request.maxSeverity())
                        .build())
                .toList());
    }

    private void replaceRewards(
            Program program,
            List<ProgramRewardRequestDto> requests
    ) {
        program.getRewards().clear();
        if (requests == null) {
            return;
        }

        program.getRewards().addAll(requests.stream()
                .map(request -> ProgramReward.builder()
                        .program(program)
                        .severity(request.severity())
                        .minAmount(request.minAmount())
                        .maxAmount(request.maxAmount())
                        .points(request.points())
                        .build())
                .toList());
    }

    private ProgramAssetResponseDto toAssetResponse(ProgramAsset asset) {
        return new ProgramAssetResponseDto(
                asset.getId(),
                asset.getAssetType(),
                asset.getIdentifier(),
                asset.getDescription(),
                asset.getIsInScope(),
                asset.getMaxSeverity()
        );
    }

    private List<ProgramAssetResponseDto> toInScopeAssetResponses(
            List<ProgramAsset> assets
    ) {
        if (assets == null) {
            return List.of();
        }
        return assets.stream()
                .filter(asset -> Boolean.TRUE.equals(asset.getIsInScope()))
                .map(this::toAssetResponse)
                .toList();
    }

    private List<ProgramAssetResponseDto> toAssetResponses(
            List<ProgramAsset> assets
    ) {
        if (assets == null) {
            return List.of();
        }
        return assets.stream()
                .map(this::toAssetResponse)
                .toList();
    }

    private ProgramRewardResponseDto toRewardResponse(ProgramReward reward) {
        return new ProgramRewardResponseDto(
                reward.getId(),
                reward.getSeverity(),
                reward.getMinAmount(),
                reward.getMaxAmount(),
                reward.getPoints()
        );
    }

    private String normalizeHandle(String handle) {
        return handle.trim().toLowerCase(Locale.ROOT);
    }

    private ProgramGuidelinesDto normalizeGuidelines(
            ProgramGuidelinesDto guidelines
    ) {
        return new ProgramGuidelinesDto(
                guidelines.description().trim(),
                guidelines.rules().stream()
                        .map(String::trim)
                        .toList()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String descriptionPreview(String description) {
        String normalized = trimToNull(description);
        if (normalized == null
                || normalized.length() <= DESCRIPTION_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, DESCRIPTION_PREVIEW_LENGTH - 1)
                .stripTrailing()
                + "…";
    }
}
