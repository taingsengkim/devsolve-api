package kh.edu.istad.ite.devsoleapi.feature.admin;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.admin.dto.AdminOverviewResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminOverviewService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserProfileRepository userProfileRepository;
    private final OrganizationRepository organizationRepository;
    private final ProgramRepository programRepository;
    private final ReportRepository reportRepository;
    private final ProblemRepository problemRepository;
    private final ShowCasesRepository showCasesRepository;
    private final SolutionRepository solutionRepository;
    private final ContentFlagRepository contentFlagRepository;

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        requireAdmin();

        UserProfileRepository.AdminUserCounts userCounts =
                userProfileRepository.findAdminCounts(
                        UserStatus.ACTIVE,
                        UserStatus.SUSPENDED,
                        UserStatus.REMOVED
                );
        OrganizationRepository.AdminOrganizationCounts organizationCounts =
                organizationRepository.findAdminCounts(
                        OrganizationStatus.PENDING,
                        OrganizationStatus.ACTIVE,
                        OrganizationStatus.REJECTED
                );
        ProgramRepository.AdminProgramCounts programCounts =
                programRepository.findAdminCounts(
                        ProgramState.DRAFT,
                        ProgramState.ACTIVE,
                        ProgramState.PAUSED,
                        ProgramState.CLOSED,
                        SubmissionState.PENDING_REVIEW
                );
        ReportRepository.AdminReportCounts reportCounts =
                reportRepository.findAdminCounts(
                        ReportState.NEW,
                        ReportState.TRIAGING,
                        ReportState.NEEDS_MORE_INFO,
                        ReportState.VALID_CONFIRMED,
                        ReportState.RESOLVED,
                        ReportState.REJECTED,
                        ReportState.DUPLICATE
                );

        long pendingProblems = problemRepository.countByStatus(
                ProblemStatus.PENDING_APPROVAL
        );
        long pendingShowcases = showCasesRepository.countReviewQueue(
                kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus
                        .PENDING.name()
        );
        long pendingSolutions = solutionRepository.countForModeration(
                kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus
                        .PENDING
        );
        long pendingFlags = contentFlagRepository.countByStatus(
                FlagStatus.PENDING
        );
        long totalPending = organizationCounts.getPendingOrganizations()
                + programCounts.getPendingReviewPrograms()
                + pendingProblems
                + pendingShowcases
                + pendingSolutions
                + pendingFlags;
        long openReports = reportCounts.getNewReports()
                + reportCounts.getTriagingReports()
                + reportCounts.getNeedsMoreInfoReports()
                + reportCounts.getValidConfirmedReports();

        return new AdminOverviewResponse(
                Instant.now(),
                new AdminOverviewResponse.UserOverview(
                        userCounts.getTotalUsers(),
                        userCounts.getActiveUsers(),
                        userCounts.getSuspendedUsers(),
                        userCounts.getRemovedUsers()
                ),
                new AdminOverviewResponse.OrganizationOverview(
                        organizationCounts.getTotalOrganizations(),
                        organizationCounts.getActiveOrganizations(),
                        organizationCounts.getPendingOrganizations(),
                        organizationCounts.getRejectedOrganizations()
                ),
                new AdminOverviewResponse.ProgramOverview(
                        programCounts.getTotalPrograms(),
                        programCounts.getDraftPrograms(),
                        programCounts.getActivePrograms(),
                        programCounts.getPausedPrograms(),
                        programCounts.getClosedPrograms(),
                        programCounts.getPendingReviewPrograms()
                ),
                new AdminOverviewResponse.ReportOverview(
                        reportCounts.getTotalReports(),
                        openReports,
                        reportCounts.getNewReports(),
                        reportCounts.getTriagingReports(),
                        reportCounts.getNeedsMoreInfoReports(),
                        reportCounts.getValidConfirmedReports(),
                        reportCounts.getResolvedReports(),
                        reportCounts.getRejectedReports(),
                        reportCounts.getDuplicateReports()
                ),
                new AdminOverviewResponse.ModerationOverview(
                        totalPending,
                        organizationCounts.getPendingOrganizations(),
                        programCounts.getPendingReviewPrograms(),
                        pendingProblems,
                        pendingShowcases,
                        pendingSolutions,
                        pendingFlags
                )
        );
    }

    private void requireAdmin() {
        if (!AuthUtils.hasRole(ADMIN_ROLE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: ADMIN"
            );
        }
    }
}
