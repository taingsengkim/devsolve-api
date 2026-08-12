package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramGuidelinesDto;
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
                .rulesOfEngagement(guidelines(
                        "  Follow these rules.  ",
                        "  Test only in scope.  "
                ))
                .exclusions(guidelines(
                        "  These findings are excluded.  ",
                        "  Self-XSS without impact.  "
                ))
                .build());

        assertEquals(
                "Include reproducible steps and evidence.",
                program.getProofOfConceptRequirements()
        );
        assertEquals(
                "Follow these rules.",
                program.getRulesOfEngagement().description()
        );
        assertEquals(
                "Test only in scope.",
                program.getRulesOfEngagement().rules().getFirst()
        );
        assertEquals(
                "Self-XSS without impact.",
                program.getExclusions().rules().getFirst()
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
                organization(program),
                List.of(inScope, outOfScope)
        );

        assertEquals(program.getId(), result.id());
        assertEquals(program.getOrganizationId(), result.organizationId());
        assertEquals("Acme Corporation", result.organizationName());
        assertEquals(
                program.getOrganizationId(),
                result.organization().id()
        );
        assertEquals("acme-corporation", result.organization().slug());
        assertEquals(
                "https://acme.test/logo.png",
                result.organization().logoUrl()
        );
        assertEquals(Industry.TECHNOLOGY, result.organization().industry());
        assertEquals("Cambodia", result.organization().country());
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
    void managementSummaryContainsDescriptionAssetsAndWorkflowFields() {
        Program program = program();
        program.setRewards(null);
        ProgramAsset inScope = asset(
                program,
                true,
                "https://app.acme.test"
        );
        ProgramAsset outOfScope = asset(
                program,
                false,
                "https://legacy.acme.test"
        );
        Organization organization = new Organization();
        organization.setId(program.getOrganizationId());
        organization.setName("Acme Corporation");
        organization.setSlug("acme-corporation");
        organization.setLogoUrl("https://acme.test/logo.png");
        organization.setWebsiteUrl("https://acme.test");
        organization.setStatus(OrganizationStatus.ACTIVE);

        ProgramManagementSummaryResponseDto result =
                mapper.toManagementSummaryDto(
                        program,
                        organization,
                        List.of(inScope, outOfScope)
                );

        assertEquals(program.getId(), result.id());
        assertEquals("Acme Corporation", result.organizationName());
        assertEquals("acme-corporation", result.organizationSlug());
        assertEquals(
                "https://acme.test/logo.png",
                result.organizationLogoUrl()
        );
        assertEquals(
                "https://acme.test",
                result.organizationWebsiteUrl()
        );
        assertEquals(
                OrganizationStatus.ACTIVE,
                result.organizationStatus()
        );
        assertEquals(ProgramState.ACTIVE, result.state());
        assertEquals(SubmissionState.APPROVED, result.submissionState());
        assertEquals(Visibility.PUBLIC, result.visibility());
        assertEquals("Security research program", result.description());
        assertEquals(2, result.assets().size());
        assertTrue(result.assets().stream().anyMatch(asset ->
                Boolean.FALSE.equals(asset.isInScope())
        ));
        assertEquals(program.getCreatedAt(), result.createdAt());
    }

    @Test
    void publicDetailIncludesInScopeAndOutOfScopeAssets() {
        Program program = program();
        ProgramAsset inScope = asset(program, true, "https://app.acme.test");
        ProgramAsset outOfScope = asset(
                program,
                false,
                "https://legacy.acme.test"
        );

        PublicProgramResponseDto result = mapper.toPublicResponseDto(
                program,
                organization(program),
                List.of(inScope, outOfScope),
                7,
                12
        );

        assertEquals("Acme Corporation", result.organizationName());
        assertEquals("acme-corporation", result.organization().slug());
        assertEquals(
                "Security-first payments company",
                result.organization().description()
        );
        assertEquals(
                program.getProofOfConceptRequirements(),
                result.proofOfConceptRequirements()
        );
        assertEquals(
                program.getRulesOfEngagement(),
                result.rulesOfEngagement()
        );
        assertEquals(program.getExclusions(), result.exclusions());
        assertEquals(2, result.assets().size());
        assertTrue(result.assets().stream().anyMatch(asset ->
                Boolean.FALSE.equals(asset.isInScope())
                        && "https://legacy.acme.test".equals(
                                asset.identifier()
                        )
        ));
        assertEquals(7, result.totalResearchers());
        assertEquals(12, result.totalSubmissions());
        assertEquals(
                "https://app.acme.test",
                result.assets().getFirst().identifier()
        );
    }

    private Organization organization(Program program) {
        Organization organization = new Organization();
        organization.setId(program.getOrganizationId());
        organization.setName("Acme Corporation");
        organization.setSlug("acme-corporation");
        organization.setLogoUrl("https://acme.test/logo.png");
        organization.setWebsiteUrl("https://acme.test");
        organization.setDescription("Security-first payments company");
        organization.setIndustry(Industry.TECHNOLOGY);
        organization.setCountry("Cambodia");
        organization.setStatus(OrganizationStatus.ACTIVE);
        return organization;
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
                .rulesOfEngagement(guidelines(
                        "Follow these rules during testing.",
                        "Test only in-scope assets"
                ))
                .exclusions(guidelines(
                        "The following findings are excluded.",
                        "Self-XSS without demonstrated impact"
                ))
                .offersBounties(true)
                .minimumBounty(new BigDecimal("100.00"))
                .maximumBounty(new BigDecimal("5000.00"))
                .build();
        program.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        program.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 11, 0));
        return program;
    }

    private ProgramGuidelinesDto guidelines(
            String description,
            String rule
    ) {
        return new ProgramGuidelinesDto(description, List.of(rule));
    }
}
