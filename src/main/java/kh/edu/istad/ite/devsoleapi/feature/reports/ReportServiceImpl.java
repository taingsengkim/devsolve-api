package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.common.storage.ObjectStorageService;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.CompanyIdentityService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.ResearcherAccessService;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportAttachment;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String USER_ROLE = "USER";
    private static final int MAX_REPORT_ATTACHMENTS = 10;
    private static final Duration DOWNLOAD_LINK_VALIDITY =
            Duration.ofMinutes(5);
    private static final Set<ReportState> ATTACHMENT_EDITABLE_STATES =
            EnumSet.of(ReportState.NEW, ReportState.NEEDS_MORE_INFO);
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
            "cvssScore",
            "discoveredAt",
            "disclosureStatus",
            "resolvedAt"
    );

    private static final Set<DisputeStatus> ACTIVE_DISPUTE_STATUSES =
            EnumSet.of(
                    DisputeStatus.OPEN,
                    DisputeStatus.UNDER_REVIEW
            );

    private static final Set<DisputeStatus> SETTLED_DISPUTE_STATUSES =
            EnumSet.of(
                    DisputeStatus.RESOLVED,
                    DisputeStatus.DISMISSED
            );

    private final ReportRepository reportRepository;
    private final ReportAttachmentRepository reportAttachmentRepository;
    private final ReportRewardRepository reportRewardRepository;
    private final DisputeRepository disputeRepository;
    private final WeaknessRepository weaknessRepository;
    private final ProgramRepository programRepository;
    private final UserProfileRepository userProfileRepository;
    private final OrganizationAuthorizationService organizationAuthorization;
    private final ResearcherAccessService researcherAccessService;
    private final CompanyIdentityService companyIdentityService;
    private final ReportMapper reportMapper;
    private final FollowNotificationService followNotificationService;
    private final AttachmentValidator attachmentValidator;
    private final ObjectStorageService objectStorageService;
    private final ApplicationEventPublisher eventPublisher;

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

        // Clearance is held against the organization, so it covers every
        // program that organization runs. Checked here rather than at the
        // controller because a draft being filed reaches this same method.
        researcherAccessService.requireApprovedReporter(
                program.getOrganizationId(),
                reporterId
        );

        ProgramAsset asset = findReportableAsset(
                program,
                request.assetId()
        );
        Weakness weakness = findActiveWeakness(request.weaknessId());

        if (request.reportedSeverity() == Severity.NONE) {
            throw badRequest(
                    "Reported severity must be LOW, MEDIUM, HIGH, or CRITICAL"
            );
        }
        validateCvss(request);

        Report report = Report.builder()
                .program(program)
                .reporter(reporter)
                .title(request.title().trim())
                .vulnerabilityInformation(
                        request.vulnerabilityInformation().trim()
                )
                .impact(trimToNull(request.impact()))
                .stepsToReproduce(trimToNull(request.stepsToReproduce()))
                .proofOfConcept(trimToNull(request.proofOfConcept()))
                .remediationRecommendation(
                        trimToNull(request.remediationRecommendation())
                )
                .targetEndpoint(trimToNull(request.targetEndpoint()))
                .environment(request.environment())
                .discoveredAt(request.discoveredAt())
                .referenceLinks(cleanReferenceLinks(request.referenceLinks()))
                .reportedSeverity(request.reportedSeverity())
                .cvssVector(trimToNull(request.cvssVector()))
                .cvssScore(request.cvssScore())
                .weakness(weakness)
                .asset(asset)
                .state(ReportState.NEW)
                .disclosureStatus(DisclosureStatus.NOT_DISCLOSED)
                .build();

        Report saved = reportRepository.saveAndFlush(report);

        // The triage queue is the one thing an organization must not miss. Sent
        // to everyone who can act on it rather than to the owner alone, or a
        // finding sits unread while the person who could triage it never hears.
        eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                organizationAuthorization.findUserIdsWithPermission(
                        program.getOrganizationId(),
                        OrganizationPermission.TRIAGE_REPORTS
                ),
                reporterId,
                "New report submitted",
                reporter.getFullName() + " submitted a "
                        + saved.getReportedSeverity() + " severity report to "
                        + program.getName() + ": " + saved.getTitle(),
                NotificationType.REPORT,
                saved.getId(),
                "report:" + saved.getId() + ":submitted"
        ));

        return reportMapper.toResponse(saved);
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

        // Classification is triage's call: the reporter picks from the catalog
        // if they recognise the class and leaves it unset otherwise. Omitting
        // it here keeps whatever the report already carries, so re-triaging for
        // a state change alone does not silently undo an earlier correction.
        Weakness weakness = findActiveWeakness(request.weaknessId());
        if (weakness != null) {
            report.setWeakness(weakness);
        }

        // An administrator has already settled this report's severity. That
        // ruling is final: re-triaging may still move the state, but it can
        // neither overwrite the agreed severity nor re-open the argument, or
        // the two sides could bounce the report between them for ever.
        Severity ruledSeverity = findSettledSeverity(report.getId());
        boolean severityMatches = report.getReportedSeverity()
                == request.triageSeverity();
        report.setSeverity(
                ruledSeverity != null
                        ? ruledSeverity
                        : (severityMatches ? request.triageSeverity() : null)
        );

        boolean opensDispute = ruledSeverity == null && !severityMatches;

        if (targetState == ReportState.RESOLVED) {
            if (report.getSeverity() == null) {
                throw conflict(
                        "A report with a severity disagreement cannot be resolved"
                );
            }
            report.setResolvedAt(LocalDateTime.now());
        }

        reportRepository.saveAndFlush(report);

        if (opensDispute) {
            ensureSeverityDispute(report);
        }

        // Keyed on the state, not on the act of triaging: moving a report to
        // the same state twice is one outcome to the reporter, but moving it
        // on to another state later is news again.
        eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                List.of(report.getReporter().getId()),
                triager.getId(),
                "Report " + describe(targetState),
                "Your report \"" + report.getTitle() + "\" on "
                        + report.getProgram().getName() + " is now "
                        + describe(targetState) + ".",
                NotificationType.REPORT,
                report.getId(),
                "report:" + report.getId() + ":state:"
                        + targetState.name().toLowerCase()
        ));

        if (opensDispute) {
            // The reporter asked for one severity and triage assigned another,
            // which opens a dispute they are a party to. Telling them the
            // state changed but not why it is stuck would be worse than
            // silence.
            eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                    List.of(report.getReporter().getId()),
                    triager.getId(),
                    "Severity disputed",
                    "Triage assessed \"" + report.getTitle() + "\" as "
                            + request.triageSeverity() + " rather than "
                            + report.getReportedSeverity()
                            + ". The report is on hold until this is settled.",
                    NotificationType.DISPUTE,
                    report.getId(),
                    "report:" + report.getId() + ":severity-disputed:"
                            + request.triageSeverity()
            ));
            notifyAdministratorsOfDispute(report, request.triageSeverity());
        }

        return reportMapper.toResponse(report);
    }

    private String describe(ReportState state) {
        return state.name().toLowerCase().replace('_', ' ');
    }

    /**
     * Only an administrator can settle a severity dispute, and until one does
     * the report can be neither re-triaged nor paid. Without this the dispute
     * opens into a queue nobody is watching and the report sits there for good.
     */
    private void notifyAdministratorsOfDispute(
            Report report,
            Severity triageSeverity
    ) {
        eventPublisher.publishEvent(new NotificationEvent(
                companyIdentityService.findUserIdsByRealmRole(ADMIN_ROLE),
                "Severity dispute needs a decision",
                "\"" + report.getTitle() + "\" on "
                        + report.getProgram().getName()
                        + " was reported as " + report.getReportedSeverity()
                        + " and triaged as " + triageSeverity + ".",
                NotificationType.DISPUTE,
                report.getId(),
                "report:" + report.getId() + ":severity-disputed:"
                        + triageSeverity + ":admins"
        ));
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
        reportRewardRepository.saveAndFlush(reward);
        report.getRewards().add(reward);

        // Keyed on the reward, not the report: a program may pay more than
        // once for the same finding, and each payment is its own news.
        eventPublisher.publishEvent(NotificationEvent.to(
                report.getReporter().getId(),
                "You have been rewarded",
                describeReward(reward) + " for your report \""
                        + report.getTitle() + "\" on "
                        + report.getProgram().getName() + ".",
                NotificationType.REWARD,
                report.getId(),
                "report:" + report.getId() + ":reward:" + reward.getId()
        ));

        return reportMapper.toResponse(report);
    }

    private String describeReward(ReportReward reward) {
        if (reward.getAmount() != null && reward.getPoints() != null) {
            return "You were awarded " + reward.getAmount()
                    + " and " + reward.getPoints() + " points";
        }
        if (reward.getAmount() != null) {
            return "You were awarded " + reward.getAmount();
        }
        return "You were awarded " + reward.getPoints() + " points";
    }

    @Override
    @Transactional
    public ReportResponse uploadAttachment(
            UUID reportId,
            MultipartFile file
    ) {
        requireRole(USER_ROLE);
        Report report = findReportableProgramReport(reportId);
        requireEditableReporter(report);
        if (reportAttachmentRepository.countByReportId(reportId)
                >= MAX_REPORT_ATTACHMENTS) {
            throw conflict(
                    "A report cannot have more than "
                            + MAX_REPORT_ATTACHMENTS
                            + " attachments"
            );
        }

        AttachmentValidator.ValidatedAttachment validated =
                attachmentValidator.validate(file);
        String storageKey = "reports/"
                + reportId
                + "/"
                + UUID.randomUUID()
                + "."
                + validated.extension();

        objectStorageService.store(
                storageKey,
                new ByteArrayInputStream(validated.content()),
                validated.sizeBytes(),
                validated.mimeType()
        );

        try {
            ReportAttachment attachment = ReportAttachment.builder()
                    .report(report)
                    .fileName(validated.originalFileName())
                    .storageKey(storageKey)
                    .mimeType(validated.mimeType())
                    .sizeBytes(validated.sizeBytes())
                    .uploadedBy(report.getReporter())
                    .build();
            reportAttachmentRepository.saveAndFlush(attachment);
            report.getAttachments().add(attachment);
            return reportMapper.toResponse(report);
        } catch (RuntimeException exception) {
            deleteStoredObjectQuietly(storageKey);
            throw exception;
        }
    }

    @Override
    @Transactional
    public void removeAttachment(
            UUID reportId,
            UUID attachmentId
    ) {
        requireRole(USER_ROLE);
        Report report = findReportableProgramReport(reportId);
        requireEditableReporter(report);
        ReportAttachment attachment = reportAttachmentRepository
                .findByIdAndReportId(attachmentId, reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Report attachment not found"
                ));
        if (!attachment.getUploadedBy().getId().equals(currentUserId())) {
            throw reportNotFound();
        }

        String storageKey = attachment.getStorageKey();
        reportAttachmentRepository.delete(attachment);
        reportAttachmentRepository.flush();
        deleteStoredObjectAfterCommit(storageKey);
    }

    @Override
    @Transactional(readOnly = true)
    public URI createAttachmentDownloadUrl(
            UUID reportId,
            UUID attachmentId
    ) {
        Report report = findReportableProgramReport(reportId);
        resolveDiscussionAccess(report, currentUserId());
        ReportAttachment attachment = reportAttachmentRepository
                .findByIdAndReportId(attachmentId, reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Report attachment not found"
                ));
        return objectStorageService.createDownloadUrl(
                attachment.getStorageKey(),
                DOWNLOAD_LINK_VALIDITY
        );
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
        UUID reporterId = report.getReporter().getId();
        UUID organizationId = report.getProgram().getOrganizationId();

        boolean admin = AuthUtils.hasRole(ADMIN_ROLE);
        if (admin) {
            return new ReportDiscussionAccess(
                    true,
                    true,
                    true,
                    reporterId,
                    organizationId
            );
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
                    canTriageForOrganization,
                    reporterId,
                    organizationId
            );
        }
        if (reporterId.equals(userId)) {
            return new ReportDiscussionAccess(
                    false,
                    true,
                    false,
                    reporterId,
                    organizationId
            );
        }

        // Hide private-report existence from unrelated authenticated users.
        throw reportNotFound();
    }

    private void requireEditableReporter(Report report) {
        if (!report.getReporter().getId().equals(currentUserId())) {
            throw reportNotFound();
        }
        if (!ATTACHMENT_EDITABLE_STATES.contains(report.getState())) {
            throw conflict(
                    "Attachments can only be changed while a report is new "
                            + "or needs more information"
            );
        }
    }

    private void deleteStoredObjectAfterCommit(String storageKey) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            deleteStoredObjectQuietly(storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteStoredObjectQuietly(storageKey);
                    }
                }
        );
    }

    private void deleteStoredObjectQuietly(String storageKey) {
        try {
            objectStorageService.delete(storageKey);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to delete report attachment object {}",
                    storageKey,
                    exception
            );
        }
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
                .filter(candidate -> candidate.getDeletedAt() == null)
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

    /**
     * A CVSS score and a severity claim describe the same thing twice. Letting
     * them contradict each other puts a number in the record that argues with
     * the label beside it, and triage ends up arbitrating arithmetic instead of
     * the finding.
     */
    private void validateCvss(CreateReportRequest request) {
        if (request.cvssVector() != null
                && !CvssSeverityBands.isWellFormedVector(
                        request.cvssVector().trim()
                )) {
            throw badRequest(
                    "CVSS vector must be a CVSS v3.0 or v3.1 base vector, "
                            + "for example CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/"
                            + "C:H/I:H/A:H"
            );
        }
        if (request.cvssScore() == null) {
            return;
        }

        Severity rating = CvssSeverityBands.ratingFor(request.cvssScore());
        if (rating != request.reportedSeverity()) {
            throw badRequest(
                    "A CVSS score of " + request.cvssScore()
                            + " is rated " + rating
                            + ", which does not match the reported severity "
                            + request.reportedSeverity()
            );
        }
    }

    /**
     * Null rather than an empty list when nothing survives, so the column stays
     * absent instead of holding an empty jsonb array that reads as a deliberate
     * "no references".
     */
    private List<String> cleanReferenceLinks(List<String> referenceLinks) {
        if (referenceLinks == null) {
            return null;
        }
        List<String> cleaned = referenceLinks.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return cleaned.isEmpty() ? null : cleaned;
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

    /**
     * The severity an administrator settled on for this report, or null when
     * no dispute has ever been ruled on.
     */
    private Severity findSettledSeverity(UUID reportId) {
        return disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        reportId,
                        SETTLED_DISPUTE_STATUSES
                )
                .map(Dispute::getResolvedSeverity)
                .orElse(null);
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

    /**
     * A retired class stays readable on the reports already filed under it but
     * can no longer be chosen, which is the whole point of retiring one rather
     * than deleting it.
     */
    private Weakness findActiveWeakness(UUID weaknessId) {
        if (weaknessId == null) {
            return null;
        }
        return weaknessRepository.findByIdAndIsActiveTrue(weaknessId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active weakness not found"
                ));
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
