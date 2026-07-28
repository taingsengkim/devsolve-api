package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMember;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
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
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String COMPANY_ROLE = "COMPANY";
    private static final String USER_ROLE = "USER";

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
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ReportMapper reportMapper;

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

        if (AuthUtils.hasRole(ADMIN_ROLE)) {
            return reportRepository.findAll(specification, pageable)
                    .map(reportMapper::toResponse);
        }

        if (AuthUtils.hasRole(COMPANY_ROLE)) {
            specification = specification.and(
                    ReportSpecification.forOrganizations(
                            companyOrganizationIds(userId)
                    )
            );
            return reportRepository.findAll(specification, pageable)
                    .map(reportMapper::toResponse);
        }

        if (AuthUtils.hasRole(USER_ROLE)) {
            specification = specification.and(
                    ReportSpecification.submittedBy(userId)
            );
            return reportRepository.findAll(specification, pageable)
                    .map(reportMapper::toResponse);
        }

        throw forbidden("A DevSolve platform role is required");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> findMine(Pageable pageable) {
        requireRole(USER_ROLE);
        return reportRepository
                .findByReporterId(currentUserId(), pageable)
                .map(reportMapper::toResponse);
    }

    @Override
    @Transactional
    public ReportResponse triage(
            UUID id,
            TriageReportRequest request
    ) {
        Report report = findReportForCompanyAction(id);
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
        Report report = findReportForCompanyAction(id);
        if (request.disclosureStatus() == DisclosureStatus.DISCLOSED
                && report.getState() != ReportState.RESOLVED) {
            throw conflict(
                    "Only a resolved report can be marked as disclosed"
            );
        }

        report.setDisclosureStatus(request.disclosureStatus());
        return reportMapper.toResponse(report);
    }

    @Override
    @Transactional
    public ReportResponse recordReward(
            UUID id,
            RewardReportRequest request
    ) {
        Report report = findReportForCompanyAction(id);
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
        findReportWithViewAccess(reportId);
    }

    private Report findReportableProgramReport(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(this::reportNotFound);
    }

    private Report findReportWithViewAccess(UUID reportId) {
        Report report = findReportableProgramReport(reportId);
        UUID userId = currentUserId();

        if (AuthUtils.hasRole(ADMIN_ROLE)
                || report.getReporter().getId().equals(userId)
                || canViewOrganizationReports(
                        report.getProgram().getOrganizationId(),
                        userId
                )) {
            return report;
        }

        // Hide private-report existence from unrelated authenticated users.
        throw reportNotFound();
    }

    private Report findReportForCompanyAction(UUID reportId) {
        requireRole(COMPANY_ROLE);
        Report report = findReportableProgramReport(reportId);
        UUID userId = currentUserId();
        UUID organizationId = report.getProgram().getOrganizationId();

        Organization organization = organizationRepository
                .findByIdAndStatusAndDeletedAtIsNull(
                        organizationId,
                        OrganizationStatus.ACTIVE
                )
                .orElseThrow(() -> forbidden(
                        "The report's organization is not active"
                ));

        if (organization.getOwner().getId().equals(userId)) {
            return report;
        }

        boolean canManage = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .filter(member ->
                        member.getStatus() == MembershipStatus.ACTIVE
                )
                .map(OrganizationMember::getRole)
                .filter(role ->
                        role == OrgRole.MANAGER
                                || role == OrgRole.MEMBER
                )
                .isPresent();
        if (!canManage) {
            throw forbidden(
                    "Only an organization owner, manager, or member can update reports"
            );
        }

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

    private boolean canViewOrganizationReports(
            UUID organizationId,
            UUID userId
    ) {
        if (!AuthUtils.hasRole(COMPANY_ROLE)) {
            return false;
        }

        return companyOrganizationIds(userId).contains(organizationId);
    }

    private Set<UUID> companyOrganizationIds(UUID userId) {
        Set<UUID> organizationIds = new HashSet<>();

        organizationRepository
                .findByOwnerIdAndDeletedAtIsNull(userId)
                .filter(organization ->
                        organization.getStatus()
                                == OrganizationStatus.ACTIVE
                )
                .map(Organization::getId)
                .ifPresent(organizationIds::add);

        organizationMemberRepository
                .findByUserIdAndStatus(
                        userId,
                        MembershipStatus.ACTIVE
                )
                .stream()
                .map(OrganizationMember::getOrganization)
                .filter(organization ->
                        organization.getDeletedAt() == null
                                && organization.getStatus()
                                == OrganizationStatus.ACTIVE
                )
                .map(Organization::getId)
                .forEach(organizationIds::add);

        return organizationIds;
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
