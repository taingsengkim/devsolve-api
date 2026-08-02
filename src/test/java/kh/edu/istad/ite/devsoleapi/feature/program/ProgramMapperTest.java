package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramManagementSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramMapperTest {

    private final ProgramMapper mapper = new ProgramMapper();

    @Test
    void createAndUpdateMapProofOfConceptRequirements() {
        Program program = mapper.toEntity(ProgramRequestDto.builder()
                .handle("acme-security")
                .name("Acme Security Program")
                .engagementType(EngagementType.RESPONSE)
                .visibility(Visibility.PRIVATE)
                .policy("Follow the rules of engagement.")
                .proofOfConceptRequirements(
                        "  Include reproducible steps and evidence.  "
                )
                .build());

        assertEquals(
                "Include reproducible steps and evidence.",
                program.getProofOfConceptRequirements()
        );

        mapper.updateEntity(
                new ProgramUpdateRequestDto(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "  Provide an HTTP request trace.  ",
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                program
        );

        assertEquals(
                "Provide an HTTP request trace.",
                program.getProofOfConceptRequirements()
        );
    }

    @Test
    void publicSummaryIncludesOrganizationAndOnlyInScopeAssets() {
        Program program = program();
        program.setDescription("A".repeat(300));
        program.setRewards(null);
        ProgramAsset inScope = asset(program, true, "https://app.acme.test");
        ProgramAsset outOfScope = asset(
                program,
                false,
                "https://legacy.acme.test"
        );

        ProgramSummaryResponseDto result = mapper.toSummaryDto(
                program,
                "Acme Corporation",
                List.of(inScope, outOfScope)
        );

        assertEquals(program.getId(), result.id());
        assertEquals(program.getOrganizationId(), result.organizationId());
        assertEquals("Acme Corporation", result.organizationName());
        assertEquals(1, result.inScopeAssets().size());
        assertEquals(
                "https://app.acme.test",
                result.inScopeAssets().getFirst().identifier()
        );
        assertEquals(240, result.description().length());
        assertTrue(result.description().endsWith("…"));
        assertEquals(program.getMaximumBounty(), result.maximumBounty());
    }

    private ProgramAsset asset(
            Program program,
            boolean inScope,
            String identifier
    ) {
        return ProgramAsset.builder()
                .id(UUID.randomUUID())
                .program(program)
                .assetType(AssetType.URL)
                .identifier(identifier)
                .isInScope(inScope)
                .build();
    }

    @Test
    void managementSummaryContainsWorkflowFieldsWithoutRelationships() {
        Program program = program();
        program.setAssets(null);
        program.setRewards(null);

        ProgramManagementSummaryResponseDto result =
                mapper.toManagementSummaryDto(program);

        assertEquals(program.getId(), result.id());
        assertEquals(ProgramState.ACTIVE, result.state());
        assertEquals(SubmissionState.APPROVED, result.submissionState());
        assertEquals(Visibility.PUBLIC, result.visibility());
        assertEquals(program.getCreatedAt(), result.createdAt());
    }

    @Test
    void publicDetailPreservesAssetsFieldButExcludesOutOfScopeAssets() {
        Program program = program();
        ProgramAsset inScope = asset(program, true, "https://app.acme.test");
        ProgramAsset outOfScope = asset(
                program,
                false,
                "https://legacy.acme.test"
        );

        PublicProgramResponseDto result = mapper.toPublicResponseDto(
                program,
                "Acme Corporation",
                List.of(inScope, outOfScope),
                7,
                12
        );

        assertEquals("Acme Corporation", result.organizationName());
        assertEquals(
                program.getProofOfConceptRequirements(),
                result.proofOfConceptRequirements()
        );
        assertEquals(1, result.assets().size());
        assertEquals(7, result.totalResearchers());
        assertEquals(12, result.totalSubmissions());
        assertEquals(
                "https://app.acme.test",
                result.assets().getFirst().identifier()
        );
    }

    private Program program() {
        Program program = Program.builder()
                .id(UUID.randomUUID())
                .organizationId(UUID.randomUUID())
                .handle("acme-security")
                .name("Acme Security Program")
                .description("Security research program")
                .engagementType(EngagementType.BOUNTY)
                .state(ProgramState.ACTIVE)
                .submissionState(SubmissionState.APPROVED)
                .visibility(Visibility.PUBLIC)
                .policy("Follow the program rules of engagement.")
                .proofOfConceptRequirements(
                        "Include reproducible steps and supporting evidence."
                )
                .offersBounties(true)
                .minimumBounty(new BigDecimal("100.00"))
                .maximumBounty(new BigDecimal("5000.00"))
                .build();
        program.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        program.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 11, 0));
        return program;
    }
}
