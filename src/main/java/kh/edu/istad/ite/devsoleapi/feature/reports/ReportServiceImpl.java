package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String USER_ROLE = "USER";
    private static final Set<String> REPORT_SORT_PROPERTIES = Set.of(
            "id",
            "submittedAt",
            "createdAt",
            "updatedAt",
            "title",
            "state",
            "reportedSeverity",
            "triageSeverity",
            "severity",
            "disclosureStatus",
            "resolvedAt"
    );

    private static final Set<DisputeStatus> ACTIVE_DISPUTE_STATUSES =
            EnumSet.of(
                    DisputeStatus.OPEN,
                    DisputeStatus.UNDER_REVIEW
            );

    private final ReportRepository reportRepository;
    private final ReportRewardRepository reportRewardRepository;
    private final DisputeRepository disputeRepository;
    private final WeaknessRepository weaknessRepository;
    private final ProgramRepository programRepository;
    private final UserProfileRepository userProfileRepository;
    private final OrganizationAuthorizationService organizationAuthorization;
    private final ReportMapper reportMapper;
    private final FollowNotificationService followNotificationService;

    @Override
    @Transactional
    public ReportResponse create(
            UUID programId,
            CreateReportRequest request
    ) {
        requireRole(USER_ROLE);
        UUID reporterId = currentUserId();
        UserProfile reporter = findUserProfile(reporterId);
        Program program = findReportableProgram(programId);
        ProgramAsset asset = findReportableAsset(
                program,
                request.assetId()
        );
        Weakness weakness = request.weaknessId() == null
                ? null
                : weaknessRepository
                        .findByIdAndIsActiveTrue(request.weaknessId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Active weakness not found"
                        ));

        if (request.reportedSeverity() == Severity.NONE) {
            throw badRequest(
                    "Reported severity must be LOW, MEDIUM, HIGH, or CRITICAL"
            );
        }

        Report report = Report.builder()
                .program(program)
                .reporter(reporter)
                .title(request.title().trim())
                .vulnerabilityInformation(
                        request.vulnerabilityInformation().trim()
                )
                .impact(trimToNull(request.impact()))
                .reportedSeverity(request.reportedSeverity())
                .weakness(weakness)
                .asset(asset)
                .state(ReportState.NEW)
                .disclosureStatus(DisclosureStatus.NOT_DISCLOSED)
                .build();

        return reportMapper.toResponse(
                reportRepository.saveAndFlush(report)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponse findById(UUID id) {
        return reportMapper.toResponse(findReportWithViewAccess(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> findAccessible(
            UUID programId,
            ReportState state,
            Pageable pageable
    ) {
        UUID userId = currentUserId();
        Specification<Report> specification =
                ReportSpecification.forProgram(programId)
                        .and(ReportSpecification.withState(state));

        if (!AuthUtils.hasRole(ADMIN_ROLE)) {
            Set<UUID> organizationIds =
                    organizationAuthorization.findAccessibleOrganizationIds(
                            userId,
                            OrganizationPermission.VIEW_REPORTS
                    );
            if (!organizationIds.isEmpty()) {
                specification = specification.and(
                        ReportSpecification.forOrganizations(
                                organizationIds
                        )
                );
            } else if (AuthUtils.hasRole(USER_ROLE)) {
                specification = specification.and(
                        ReportSpecification.submittedBy(userId)
                );
            } else {
                throw forbidden("A DevSolve platform role is required");
            }
        }

        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                REPORT_SORT_PROPERTIES
        );
        return reportRepository.findAll(specification, validatedPageable)
                .map(reportMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> findMine(Pageable pageable) {
        requireRole(USER_ROLE);
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                REPORT_SORT_PROPERTIES
        );
        return reportRepository
                .findByReporterId(currentUserId(), validatedPageable)
                .map(reportMapper::toResponse);
    }

    @Override
    @Transactional
    public ReportResponse triage(
            UUID id,
            TriageReportRequest request
    ) {
        Report report = findReportForOrganizationAction(
                id,
                OrganizationPermission.TRIAGE_REPORTS
        );
        requireNoActiveDispute(report.getId());

        ReportState targetState = request.state() == null
                ? ReportState.VALID_CONFIRMED
                : request.state();
        validateTriageTransition(report.getState(), targetState);
        applyDuplicate(report, targetState, request.duplicateOfId());

        UserProfile triager = findUserProfile(currentUserId());
        report.setTriageSeverity(request.triageSeverity());
        report.setTriagedBy(triager);
        report.setTriagedAt(LocalDateTime.now());
        report.setState(targetState);

        boolean severityMatches = report.getReportedSeverity()
                == request.triageSeverity();
        report.setSeverity(
                severityMatches ? request.triageSeverity() : null
        );

        if (targetState == ReportState.RESOLVED) {
            if (!severityMatches) {
                throw conflict(
                        "A report with a severity disagreement cannot be resolved"
                );
            }
            report.setResolvedAt(LocalDateTime.now());
        }

        reportRepository.saveAndFlush(report);

        if (!severityMatches) {
            ensureSeverityDispute(report);
        }

        return reportMapper.toResponse(report);
    }

    @Override
    @Transactional
    public ReportResponse updateDisclosureStatus(
            UUID id,
            UpdateDisclosureStateRequest request
    ) {
        Report report = findReportForOrganizationAction(
                id,
                OrganizationPermission.MANAGE_DISCLOSURE
        );
        if (request.disclosureStatus() == DisclosureStatus.DISCLOSED
                && report.getState() != ReportState.RESOLVED) {
            throw conflict(
                    "Only a resolved report can be marked as disclosed"
            );
        }

        boolean newlyDisclosed = report.getDisclosureStatus()
                != DisclosureStatus.DISCLOSED
                && request.disclosureStatus() == DisclosureStatus.DISCLOSED;
        report.setDisclosureStatus(request.disclosureStatus());
        if (newlyDisclosed) {
            followNotificationService.notifyFollowers(
                    FollowType.USER,
                    report.getReporter().getId(),
                    currentUserId(),
                    "New disclosed security report",
                    report.getTitle(),
                    NotificationType.REPORT,
                    report.getId(),
                    "report-disclosed:" + report.getId()
            );
        }
        return reportMapper.toResponse(report);
    }

    @Override
    @Transactional
    public ReportResponse recordReward(
            UUID id,
            RewardReportRequest request
    ) {
        Report report = findReportForOrganizationAction(
                id,
                OrganizationPermission.AWARD_REWARDS
        );
        if (request.amount() == null && request.points() == null) {
            throw badRequest(
                    "A reward must contain an amount, points, or both"
            );
        }
        if (request.amount() != null
                && !Boolean.TRUE.equals(
                        report.getProgram().getOffersBounties()
                )) {
            throw conflict(
                    "This program does not offer monetary bounties"
            );
        }
        if (report.getSeverity() == null) {
            throw conflict(
                    "A final severity is required before recording a reward"
            );
        }
        requireNoActiveDispute(report.getId());

        ReportReward reward = ReportReward.builder()
                .report(report)
                .amount(request.amount())
                .points(request.points())
                .awardedBy(findUserProfile(currentUserId()))
                .note(trimToNull(request.note()))
                .build();
        reportRewardRepository.save(reward);
        report.getRewards().add(reward);

        return reportMapper.toResponse(report);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireViewAccess(UUID reportId) {
        requireDiscussionAccess(reportId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportDiscussionAccess requireDiscussionAccess(UUID reportId) {
        Report report = findReportableProgramReport(reportId);
        UUID userId = currentUserId();
        return resolveDiscussionAccess(report, userId);
    }

    private ReportDiscussionAccess resolveDiscussionAccess(
            Report report,
            UUID userId
    ) {
        boolean admin = AuthUtils.hasRole(ADMIN_ROLE);
        if (admin) {
            return new ReportDiscussionAccess(true, true, true);
        }

        boolean canTriageForOrganization =
                organizationAuthorization.hasPermission(
                        report.getProgram().getOrganizationId(),
                        userId,
                        OrganizationPermission.TRIAGE_REPORTS
                );
        boolean canViewForOrganization =
                canTriageForOrganization
                        || organizationAuthorization.hasPermission(
                                report.getProgram().getOrganizationId(),
                                userId,
                                OrganizationPermission.VIEW_REPORTS
                        );

        if (canViewForOrganization) {
            return new ReportDiscussionAccess(
                    true,
                    canTriageForOrganization,
                    canTriageForOrganization
            );
        }
        if (report.getReporter().getId().equals(userId)) {
            return new ReportDiscussionAccess(false, true, false);
        }

        // Hide private-report existence from unrelated authenticated users.
        throw reportNotFound();
    }

    private Report findReportableProgramReport(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(this::reportNotFound);
    }

    private Report findReportWithViewAccess(UUID reportId) {
        Report report = findReportableProgramReport(reportId);
        resolveDiscussionAccess(report, currentUserId());
        return report;
    }

    private Report findReportForOrganizationAction(
            UUID reportId,
            OrganizationPermission permission
    ) {
        Report report = findReportableProgramReport(reportId);
        UUID userId = currentUserId();
        UUID organizationId = report.getProgram().getOrganizationId();

        organizationAuthorization.requirePermission(
                organizationId,
                userId,
                permission
        );
        return report;
    }

    private Program findReportableProgram(UUID programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Program not found"
                ));
        if (program.getState() != ProgramState.ACTIVE
                || program.getSubmissionState()
                != SubmissionState.APPROVED) {
            throw conflict(
                    "Reports can only be submitted to active, approved programs"
            );
        }
        return program;
    }

    private ProgramAsset findReportableAsset(
            Program program,
            UUID assetId
    ) {
        if (assetId == null) {
            return null;
        }
        return program.getAssets().stream()
                .filter(asset -> asset.getId().equals(assetId))
                .filter(asset ->
                        Boolean.TRUE.equals(asset.getIsInScope())
                )
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "In-scope program asset not found"
                ));
    }

    private void ensureSeverityDispute(Report report) {
        Dispute dispute = disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        report.getId(),
                        ACTIVE_DISPUTE_STATUSES
                )
                .orElseGet(() -> disputeRepository.save(
                        Dispute.builder()
                                .report(report)
                                .raisedBy(report.getReporter())
                                .reason(
                                        "Automatically opened because the reported and triage severities differ"
                                )
                                .status(DisputeStatus.OPEN)
                                .build()
                ));

        boolean alreadyLoaded = report.getDisputes().stream()
                .anyMatch(candidate ->
                        candidate.getId() != null
                                && candidate.getId().equals(dispute.getId())
                );
        if (!alreadyLoaded) {
            report.getDisputes().add(dispute);
        }
    }

    private void requireNoActiveDispute(UUID reportId) {
        disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        reportId,
                        ACTIVE_DISPUTE_STATUSES
                )
                .ifPresent(dispute -> {
                    throw conflict(
                            "An administrator must resolve the open severity dispute"
                    );
                });
    }

    private void applyDuplicate(
            Report report,
            ReportState targetState,
            UUID duplicateOfId
    ) {
        if (targetState != ReportState.DUPLICATE) {
            if (duplicateOfId != null) {
                throw badRequest(
                        "duplicateOfId is only allowed for duplicate reports"
                );
            }
            report.setDuplicateOf(null);
            return;
        }

        if (duplicateOfId == null) {
            throw badRequest(
                    "duplicateOfId is required for duplicate reports"
            );
        }
        if (duplicateOfId.equals(report.getId())) {
            throw badRequest("A report cannot duplicate itself");
        }

        Report original = findReportableProgramReport(duplicateOfId);
        if (!original.getProgram().getId()
                .equals(report.getProgram().getId())) {
            throw badRequest(
                    "A duplicate must reference a report from the same program"
            );
        }
        report.setDuplicateOf(original);
    }

    private void validateTriageTransition(
            ReportState current,
            ReportState target
    ) {
        if (current == ReportState.RESOLVED
                || current == ReportState.REJECTED
                || current == ReportState.DUPLICATE) {
            throw conflict("A terminal report cannot be triaged again");
        }
        if (target == ReportState.NEW) {
            throw badRequest(
                    "Triage cannot return a report to the NEW state"
            );
        }
        if (target == ReportState.RESOLVED
                && current != ReportState.VALID_CONFIRMED) {
            throw conflict(
                    "Only a valid confirmed report can be resolved"
            );
        }
    }

    private UserProfile findUserProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user profile not found"
                ));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    private void requireRole(String role) {
        if (!AuthUtils.hasRole(role)) {
            throw forbidden("Required realm role: " + role);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                reason
        );
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }

    private ResourceNotFoundException reportNotFound() {
        return new ResourceNotFoundException("Report not found");
    }
}
