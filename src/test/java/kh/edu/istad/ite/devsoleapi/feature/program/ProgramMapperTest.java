package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramManagementSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramMapperTest {

    private final ProgramMapper mapper = new ProgramMapper();

    @Test
    void publicSummaryUsesDescriptionPreviewWithoutRelationships() {
        Program program = program();
        program.setDescription("A".repeat(300));
        program.setAssets(null);
        program.setRewards(null);

        ProgramSummaryResponseDto result = mapper.toSummaryDto(program);

        assertEquals(program.getId(), result.id());
        assertEquals(program.getOrganizationId(), result.organizationId());
        assertEquals(240, result.description().length());
        assertTrue(result.description().endsWith("…"));
        assertEquals(program.getMaximumBounty(), result.maximumBounty());
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
                .offersBounties(true)
                .minimumBounty(new BigDecimal("100.00"))
                .maximumBounty(new BigDecimal("5000.00"))
                .build();
        program.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        program.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 11, 0));
        return program;
    }
}
