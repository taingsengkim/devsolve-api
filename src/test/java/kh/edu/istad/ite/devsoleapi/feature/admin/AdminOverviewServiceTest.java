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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOverviewServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private ShowCasesRepository showCasesRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private ContentFlagRepository contentFlagRepository;
    @Mock
    private UserProfileRepository.AdminUserCounts userCounts;
    @Mock
    private OrganizationRepository.AdminOrganizationCounts organizationCounts;
    @Mock
    private ProgramRepository.AdminProgramCounts programCounts;
    @Mock
    private ReportRepository.AdminReportCounts reportCounts;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminReceivesAggregatedOverview() {
        authenticate("ADMIN");
        when(userProfileRepository.findAdminCounts(
                UserStatus.ACTIVE,
                UserStatus.SUSPENDED,
                UserStatus.REMOVED
        )).thenReturn(userCounts);
        when(userCounts.getTotalUsers()).thenReturn(120L);
        when(userCounts.getActiveUsers()).thenReturn(110L);
        when(userCounts.getSuspendedUsers()).thenReturn(7L);
        when(userCounts.getRemovedUsers()).thenReturn(3L);

        when(organizationRepository.findAdminCounts(
                OrganizationStatus.PENDING,
                OrganizationStatus.ACTIVE,
                OrganizationStatus.REJECTED
        ))
                .thenReturn(organizationCounts);
        when(organizationCounts.getTotalOrganizations()).thenReturn(20L);
        when(organizationCounts.getActiveOrganizations()).thenReturn(14L);
        when(organizationCounts.getPendingOrganizations()).thenReturn(4L);
        when(organizationCounts.getRejectedOrganizations()).thenReturn(2L);

        when(programRepository.findAdminCounts(
                ProgramState.DRAFT,
                ProgramState.ACTIVE,
                ProgramState.PAUSED,
                ProgramState.CLOSED,
                SubmissionState.PENDING_REVIEW
        )).thenReturn(programCounts);
        when(programCounts.getTotalPrograms()).thenReturn(35L);
        when(programCounts.getDraftPrograms()).thenReturn(8L);
        when(programCounts.getActivePrograms()).thenReturn(21L);
        when(programCounts.getPausedPrograms()).thenReturn(4L);
        when(programCounts.getClosedPrograms()).thenReturn(2L);
        when(programCounts.getPendingReviewPrograms()).thenReturn(5L);

        when(reportRepository.findAdminCounts(
                ReportState.NEW,
                ReportState.TRIAGING,
                ReportState.NEEDS_MORE_INFO,
                ReportState.VALID_CONFIRMED,
                ReportState.RESOLVED,
                ReportState.REJECTED,
                ReportState.DUPLICATE
        )).thenReturn(reportCounts);
        when(reportCounts.getTotalReports()).thenReturn(80L);
        when(reportCounts.getNewReports()).thenReturn(8L);
        when(reportCounts.getTriagingReports()).thenReturn(6L);
        when(reportCounts.getNeedsMoreInfoReports()).thenReturn(4L);
        when(reportCounts.getValidConfirmedReports()).thenReturn(12L);
        when(reportCounts.getResolvedReports()).thenReturn(35L);
        when(reportCounts.getRejectedReports()).thenReturn(10L);
        when(reportCounts.getDuplicateReports()).thenReturn(5L);

        when(problemRepository.countByStatus(ProblemStatus.PENDING_APPROVAL))
                .thenReturn(3L);
        when(showCasesRepository.countReviewQueue("PENDING"))
                .thenReturn(6L);
        when(solutionRepository.countForModeration(
                kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus
                        .PENDING
        )).thenReturn(7L);
        when(contentFlagRepository.countByStatus(FlagStatus.PENDING))
                .thenReturn(2L);

        var response = service().getOverview();

        assertNotNull(response.generatedAt());
        assertEquals(120L, response.users().total());
        assertEquals(14L, response.organizations().active());
        assertEquals(21L, response.programs().active());
        assertEquals(30L, response.reports().open());
        assertEquals(27L, response.moderation().totalPending());
        assertEquals(6L, response.moderation().showcases());
    }

    @Test
    void nonAdminCannotReadOverview() {
        authenticate("COMPANY");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().getOverview()
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(
                userProfileRepository,
                organizationRepository,
                programRepository,
                reportRepository,
                problemRepository,
                showCasesRepository,
                solutionRepository,
                contentFlagRepository
        );
    }

    private AdminOverviewService service() {
        return new AdminOverviewService(
                userProfileRepository,
                organizationRepository,
                programRepository,
                reportRepository,
                problemRepository,
                showCasesRepository,
                solutionRepository,
                contentFlagRepository
        );
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user",
                        "token",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }
}
