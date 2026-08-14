package kh.edu.istad.ite.devsoleapi.feature.program;

import jakarta.persistence.EntityManager;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramGuidelinesDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.ProgramReward;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto.ProgramRewardRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class ProgramRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramMapper programMapper;

    @Autowired
    private EntityManager entityManager;

    @Test
    void publicListingQueryAcceptsAbsentAndPresentFilters() {
        assertEquals(0, assertDoesNotThrow(() -> search(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "publishedAt",
                "DESC"
        )).getTotalElements());

        assertEquals(0, assertDoesNotThrow(() -> search(
                UUID.randomUUID(),
                "bounty",
                true,
                "%acme%",
                new BigDecimal("100.00"),
                new BigDecimal("1000.00"),
                "api",
                "critical",
                "technology",
                "cambodia",
                "followerCount",
                "DESC"
        )).getTotalElements());
    }

    @Test
    void everySupportedSortExecutesAgainstPostgres() {
        List<String> properties = List.of(
                "id",
                "publishedAt",
                "createdAt",
                "updatedAt",
                "name",
                "handle",
                "minimumBounty",
                "maximumBounty",
                "viewCount",
                "followerCount",
                "totalSubmissions"
        );

        for (String property : properties) {
            assertDoesNotThrow(() -> search(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    property,
                    "ASC"
            ));
        }
    }

    @Test
    @Transactional
    void resavingFetchedChildrenPreservesIdsWithoutAUniqueConflict() {
        Program program = Program.builder()
                .organizationId(UUID.randomUUID())
                .handle("draft-resave-" + UUID.randomUUID())
                .name("Draft resave regression")
                .engagementType(EngagementType.BOUNTY)
                .visibility(Visibility.PRIVATE)
                .policy("Test only in scope.")
                .proofOfConceptRequirements(guidelines("Provide evidence"))
                .rulesOfEngagement(guidelines("Follow the rules"))
                .exclusions(guidelines("Excluded findings"))
                .offersBounties(true)
                .build();
        ProgramAsset asset = ProgramAsset.builder()
                .program(program)
                .assetType(AssetType.URL)
                .identifier("https://app.acme.test")
                .isInScope(true)
                .maxSeverity(Severity.CRITICAL)
                .build();
        ProgramReward reward = ProgramReward.builder()
                .program(program)
                .severity(Severity.LOW)
                .minAmount(new BigDecimal("100.00"))
                .maxAmount(new BigDecimal("200.00"))
                .points(10)
                .build();
        program.getAssets().add(asset);
        program.getRewards().add(reward);
        programRepository.saveAndFlush(program);
        UUID assetId = asset.getId();
        UUID rewardId = reward.getId();

        ProgramUpdateRequestDto replay = new ProgramUpdateRequestDto(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(ProgramAssetRequestDto.builder()
                        .assetType(AssetType.URL)
                        .identifier("https://app.acme.test")
                        .isInScope(true)
                        .maxSeverity(Severity.CRITICAL)
                        .build()),
                List.of(
                        ProgramRewardRequestDto.builder()
                                .id(rewardId)
                                .severity(Severity.HIGH)
                                .minAmount(new BigDecimal("600.00"))
                                .maxAmount(new BigDecimal("1200.00"))
                                .points(60)
                                .build(),
                        ProgramRewardRequestDto.builder()
                                .severity(Severity.LOW)
                                .minAmount(new BigDecimal("150.00"))
                                .maxAmount(new BigDecimal("250.00"))
                                .points(15)
                                .build()
                )
        );

        assertDoesNotThrow(() -> {
            programMapper.updateEntity(replay, program);
            programRepository.flush();
        });
        entityManager.clear();

        Program reloaded = programRepository.findById(program.getId())
                .orElseThrow();
        assertEquals(1, reloaded.getAssets().size());
        assertEquals(assetId, reloaded.getAssets().getFirst().getId());
        assertEquals(2, reloaded.getRewards().size());
        ProgramReward reloadedLow = reloaded.getRewards().stream()
                .filter(item -> item.getSeverity() == Severity.LOW)
                .findFirst()
                .orElseThrow();
        ProgramReward reloadedHigh = reloaded.getRewards().stream()
                .filter(item -> item.getSeverity() == Severity.HIGH)
                .findFirst()
                .orElseThrow();
        assertEquals(rewardId, reloadedLow.getId());
        assertEquals(
                new BigDecimal("150.00"),
                reloadedLow.getMinAmount()
        );
        assertEquals(new BigDecimal("600.00"), reloadedHigh.getMinAmount());
    }

    private ProgramGuidelinesDto guidelines(String description) {
        return new ProgramGuidelinesDto(
                description,
                List.of(description)
        );
    }

    private org.springframework.data.domain.Page<Program> search(
            UUID organizationId,
            String engagementType,
            Boolean offersBounties,
            String queryPattern,
            BigDecimal minimumBounty,
            BigDecimal maximumBounty,
            String assetType,
            String maxSeverity,
            String industry,
            String country,
            String sortProperty,
            String sortDirection
    ) {
        return programRepository.searchPublicPrograms(
                organizationId,
                engagementType,
                offersBounties,
                queryPattern,
                minimumBounty,
                maximumBounty,
                assetType,
                maxSeverity,
                industry,
                country,
                sortProperty,
                sortDirection,
                PageRequest.of(0, 20)
        );
    }
}
