package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.ProgramReward;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProgramMapper {

    // === CREATE: DTO → Entity (with assets & rewards) ===
    public Program toEntity(ProgramRequestDto dto) {
        if (dto == null) return null;

        Program program = Program.builder()
                .handle(dto.handle())
                .name(dto.name())
                .description(dto.description())
                .engagementType(dto.engagementType())
                .visibility(dto.visibility())
                .currency(dto.currency())
                .policy(dto.policy())
                .offersBounties(dto.offersBounties())
                .minimumBounty(dto.minimumBounty())
                .maximumBounty(dto.maximumBounty())
                .build();

        // Map assets if present
        if (dto.assets() != null) {
            List<ProgramAsset> assets = dto.assets().stream()
                    .map(assetDto -> {
                        ProgramAsset asset = new ProgramAsset();
                        asset.setAssetType(assetDto.assetType());
                        asset.setIdentifier(assetDto.identifier());
                        asset.setDescription(assetDto.description());
                        asset.setIsInScope(assetDto.isInScope());
                        asset.setMaxSeverity(assetDto.maxSeverity());
                        asset.setProgram(program);
                        return asset;
                    })
                    .collect(Collectors.toList());
            program.setAssets(assets);
        }

        // Map rewards if present
        if (dto.rewards() != null) {
            List<ProgramReward> rewards = dto.rewards().stream()
                    .map(rewardDto -> {
                        ProgramReward reward = new ProgramReward();
                        reward.setSeverity(rewardDto.severity());
                        reward.setMinAmount(rewardDto.minAmount());
                        reward.setMaxAmount(rewardDto.maxAmount());
                        reward.setPoints(rewardDto.points());
                        reward.setProgram(program);
                        return reward;
                    })
                    .collect(Collectors.toList());
            program.setRewards(rewards);
        }

        return program;
    }

    // === UPDATE: DTO → existing Entity (no changes to child lists) ===
    public void updateEntity(ProgramUpdateRequestDto dto, Program entity) {
        if (dto.handle() != null) entity.setHandle(dto.handle());
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.engagementType() != null) entity.setEngagementType(dto.engagementType());
        if (dto.visibility() != null) entity.setVisibility(dto.visibility());
        if (dto.currency() != null) entity.setCurrency(dto.currency());
        if (dto.policy() != null) entity.setPolicy(dto.policy());
        if (dto.offersBounties() != null) entity.setOffersBounties(dto.offersBounties());
        if (dto.minimumBounty() != null) entity.setMinimumBounty(dto.minimumBounty());
        if (dto.maximumBounty() != null) entity.setMaximumBounty(dto.maximumBounty());



        // 2. Replace assets if provided
        if (dto.assets() != null) {
            // Clear existing assets
            entity.getAssets().clear();
            // Create new assets and link them to the entity
            List<ProgramAsset> newAssets = dto.assets().stream()
                    .map(assetDto -> {
                        ProgramAsset asset = new ProgramAsset();
                        asset.setAssetType(assetDto.assetType());
                        asset.setIdentifier(assetDto.identifier());
                        asset.setDescription(assetDto.description());
                        asset.setIsInScope(assetDto.isInScope());
                        asset.setMaxSeverity(assetDto.maxSeverity());
                        asset.setProgram(entity); // set parent reference
                        return asset;
                    })
                    .collect(Collectors.toList());
            entity.getAssets().addAll(newAssets);
        }

        // 3. Replace rewards if provided
        if (dto.rewards() != null) {
            entity.getRewards().clear();
            List<ProgramReward> newRewards = dto.rewards().stream()
                    .map(rewardDto -> {
                        ProgramReward reward = new ProgramReward();
                        reward.setSeverity(rewardDto.severity());
                        reward.setMinAmount(rewardDto.minAmount());
                        reward.setMaxAmount(rewardDto.maxAmount());
                        reward.setPoints(rewardDto.points());
                        reward.setProgram(entity);
                        return reward;
                    })
                    .collect(Collectors.toList());
            entity.getRewards().addAll(newRewards);
        }

    }

    // === RESPONSE: Entity → DTO (using record constructor) ===
    public ProgramResponseDto toResponseDto(Program entity) {
        if (entity == null) return null;

        return new ProgramResponseDto(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getHandle(),
                entity.getName(),
                entity.getDescription(),
                entity.getEngagementType(),
                entity.getState(),
                entity.getSubmissionState(),
                entity.getVisibility(),
                entity.getCurrency(),
                entity.getPolicy(),
                entity.getOffersBounties(),
                entity.getMinimumBounty(),
                entity.getMaximumBounty(),
                entity.getStartedAcceptingAt(),
                entity.getAssets(),
                entity.getRewards()
        );
    }

    // === UPDATE DTO (for changelog) ===
    public ProgramUpdateChangeLogDto toUpdateDto(ProgramUpdate entity) {
        if (entity == null) return null;
        return new ProgramUpdateChangeLogDto(
                entity.getId(),
                entity.getChangeSummary(),
                entity.getCreatedAt()
        );
    }
}
