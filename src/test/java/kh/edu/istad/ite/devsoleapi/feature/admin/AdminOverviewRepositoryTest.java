package kh.edu.istad.ite.devsoleapi.feature.admin;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.ContentFlagRepository;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class AdminOverviewRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ProblemRepository problemRepository;
    @Autowired
    private ShowCasesRepository showCasesRepository;
    @Autowired
    private SolutionRepository solutionRepository;
    @Autowired
    private ContentFlagRepository contentFlagRepository;

    @Test
    void overviewQueriesReturnZeroForEmptyDatabase() {
        assertEquals(
                0L,
                userProfileRepository.findAdminCounts(
                        UserStatus.ACTIVE,
                        UserStatus.SUSPENDED,
                        UserStatus.REMOVED
                ).getTotalUsers()
        );
        assertEquals(
                0L,
                organizationRepository.findAdminCounts(
                        OrganizationStatus.PENDING,
                        OrganizationStatus.ACTIVE,
                        OrganizationStatus.REJECTED
                )
                        .getTotalOrganizations()
        );
        assertEquals(
                0L,
                programRepository.findAdminCounts(
                        ProgramState.DRAFT,
                        ProgramState.ACTIVE,
                        ProgramState.PAUSED,
                        ProgramState.CLOSED,
                        SubmissionState.PENDING_REVIEW
                ).getTotalPrograms()
        );
        assertEquals(
                0L,
                reportRepository.findAdminCounts(
                        ReportState.NEW,
                        ReportState.TRIAGING,
                        ReportState.NEEDS_MORE_INFO,
                        ReportState.VALID_CONFIRMED,
                        ReportState.RESOLVED,
                        ReportState.REJECTED,
                        ReportState.DUPLICATE
                ).getTotalReports()
        );
        assertEquals(
                0L,
                problemRepository.countByStatus(
                        ProblemStatus.PENDING_APPROVAL
                )
        );
        assertEquals(
                0L,
                showCasesRepository.countReviewQueue("PENDING")
        );
        assertEquals(
                0L,
                solutionRepository.countForModeration(
                        kh.edu.istad.ite.devsoleapi.feature.solution.enums
                                .ReviewStatus.PENDING
                )
        );
        assertEquals(
                0L,
                contentFlagRepository.countByStatus(FlagStatus.PENDING)
        );
    }
}
