package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityRecorder;
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
import kh.edu.istad.ite.devsoleapi.feature.comments.Comment;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RequestRetestRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SubmitRetestRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportAttachment;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportRetest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.RetestVerdict;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.AttachmentScanContext;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.VirusTotalContentGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String USER_ROLE = "USER";
    private static final int MAX_REPORT_ATTACHMENTS = 10;
    private static final Duration DOWNLOAD_LINK_VALIDITY =
            Duration.ofMinutes(5);
    /**
     * How long a researcher has to answer a retest before the attempt lapses.
     *
     * <p>An attempt with nobody answering it used to sit open for ever and
     * block every later retest on the report, leaving the organization to
     * triage its own report out of RETESTING to get the queue moving. Two weeks
     * is long enough to cover someone being away and short enough that a
     * program is not held by a researcher who has moved on.
     */
    private static final Duration RETEST_RESPONSE_WINDOW = Duration.ofDays(14);
    /**
     * What counts towards a researcher's valid-report tally: the findings that
     * were agreed to be real. NEW, TRIAGING and NEEDS_MORE_INFO have not been
     * decided; REJECTED and DUPLICATE were decided against.
     *
     * <p>RETESTING belongs here. A report only reaches it from VALID_CONFIRMED,
     * so the finding was agreed before the retest started; leaving it out would
     * quietly dock a researcher a valid report for the days an organization
     * takes to have its fix checked.
     */
    private static final Set<ReportState> VALID_REPORT_STATES = Set.of(
            ReportState.VALID_CONFIRMED,
            ReportState.RETESTING,
            ReportState.RESOLVED
    );

    /**
     * RETESTING is editable because verifying a fix produces new evidence — the
     * screenshot of the request now being refused — and the researcher has
     * nowhere to put it otherwise.
     */
    private static final Set<ReportState> ATTACHMENT_EDITABLE_STATES =
            EnumSet.of(
                    ReportState.NEW,
                    ReportState.NEEDS_MORE_INFO,
                    ReportState.RETESTING
            );
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
    private final ReportRetestRepository reportRetestRepository;
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
    private final ReportRateLimiter reportRateLimiter;
    private final HacktivityRecorder hacktivityRecorder;
    /**
     * The repository rather than CommentService: comments already depend on
     * this service to decide who may read a report's thread, and injecting the
     * service back would close that loop into a bean cycle. Retest notices are
     * written straight to the table anyway — they are a record of something the
     * platform did, so the rate limits, profanity review and duplicate check
     * that guard what a person types do not apply to them.
     */
    private final CommentRepository commentRepository;
    private VirusTotalContentGuard virusTotalContentGuard;

    /**
     * Evicts the leaderboard because the board prints totalReports and
     * validReports, and this changes one of them. Awarding a recognition was
     * doing this already; submitting and triaging were not, so a researcher's
     * report counts stayed at whatever the board had cached — which, on a
     * board first filled before those counters were ever written, was zero for
     * everybody.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LEADERBOARD, allEntries = true)
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
        requireSafeSubmittedUrls(request);

        // Ordered so nothing can fail after the burst window has been
        // consumed: a rejected report must not spend the reporter's allowance.
        reportRateLimiter.checkSustained(
                reportRepository.countByReporterSince(
                        reporterId,
                        LocalDateTime.now()
                                .minus(ReportRateLimiter.SUSTAINED_WINDOW)
                )
        );
        reportRateLimiter.checkBurst(reporterId);

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

        // Mapped before the counters are refreshed: that query clears the
        // persistence context, and everything the response needs has to be
        // read while the entities are still managed.
        ReportResponse response = reportMapper.toResponse(saved);
        refreshReportCounts(reporterId);
        return response;
    }

    @Autowired
    void setVirusTotalContentGuard(
            VirusTotalContentGuard virusTotalContentGuard
    ) {
        this.virusTotalContentGuard = virusTotalContentGuard;
    }

    private void requireSafeSubmittedUrls(CreateReportRequest request) {
        if (virusTotalContentGuard == null) {
            return;
        }
        virusTotalContentGuard.requireSafeUrl(request.targetEndpoint());
        virusTotalContentGuard.requireSafeUrls(request.referenceLinks());
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

    /** Moves validReports, so the board it is printed on has to be dropped. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LEADERBOARD, allEntries = true)
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
        boolean leftRetest = report.getState() == ReportState.RETESTING
                && targetState != ReportState.RETESTING;
        boolean reopened = report.getState() == ReportState.RESOLVED
                && targetState != ReportState.RESOLVED;
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

        boolean newlyResolved = targetState == ReportState.RESOLVED
                && report.getResolvedAt() == null;

        if (targetState == ReportState.RESOLVED) {
            if (report.getSeverity() == null) {
                throw conflict(
                        "A report with a severity disagreement cannot be resolved"
                );
            }
            report.setResolvedAt(LocalDateTime.now());
        }

        // The report is not fixed any more, so the date it was fixed has to go
        // with it — the same clearing a failed retest does. Leaving it behind
        // would keep every query that reads resolvedAt believing otherwise, and
        // would stop triage ever setting it again: the branch above only
        // stamps a report that has none.
        if (reopened) {
            report.setResolvedAt(null);
        }

        reportRepository.saveAndFlush(report);

        if (leftRetest) {
            closeOpenRetest(report, triager);
        }

        // Only the first time it lands on RESOLVED. Re-triaging a resolved
        // report to the same state is not a second thing happening, and the
        // feed should not say it was.
        if (newlyResolved) {
            hacktivityRecorder.recordResolved(report);
        }

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

        // Same ordering as create(): the refresh clears the persistence
        // context, so the response is built while the report is still managed.
        ReportResponse response = reportMapper.toResponse(report);
        refreshReportCounts(report.getReporter().getId());
        return response;
    }

    /**
     * Brings the reporter's report counters back in line with their reports.
     *
     * <p>Called on submission and on every triage decision, which between them
     * are the only two things that can change either number. A profile that
     * has been deleted since is not an error worth failing the triage over —
     * there is simply nothing left to count for.
     */
    private void refreshReportCounts(UUID reporterId) {
        userProfileRepository.refreshReportCounts(
                reporterId,
                VALID_REPORT_STATES
        );
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
            hacktivityRecorder.recordDisclosed(report);
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

    /**
     * Records a payout. Money only: a reward no longer moves reputation, so
     * the leaderboard is untouched and is not evicted here.
     */
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
        if (!Boolean.TRUE.equals(
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

        // points is left unset: the column stays for the rewards recorded
        // before reputation stopped being an organization's to hand out.
        ReportReward reward = ReportReward.builder()
                .report(report)
                .amount(request.amount())
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

    /**
     * Says only what the reward actually is. It used to promise "and N
     * points" for a number that never reached anybody's standing, which is a
     * worse thing to send than nothing at all.
     */
    private String describeReward(ReportReward reward) {
        return "You were awarded " + reward.getAmount();
    }

    /**
     * Opens a round of fix verification.
     *
     * <p>Only from RESOLVED. Resolving a report is the organization's claim
     * that it fixed the vulnerability; a retest is the researcher checking that
     * claim. Asking before the report is resolved would be asking about a fix
     * nobody has said is finished, and it would hold the organization's queue
     * open against a researcher who may never reply — the organization closes
     * its own report, and the verification happens against what it closed.
     *
     * <p>Evicts the leaderboard for the same reason triage does: the state
     * moves, and the board prints counts derived from it.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LEADERBOARD, allEntries = true)
    public ReportResponse requestRetest(
            UUID id,
            RequestRetestRequest request
    ) {
        Report report = findReportForRetestRequest(id);
        requireNoActiveDispute(report.getId());

        if (report.getState() != ReportState.RESOLVED) {
            throw conflict(
                    "Only a resolved report can be sent for retest, and this "
                            + "one is " + describe(report.getState())
            );
        }
        // Belt and braces against the state above: an open attempt means the
        // report should already be RETESTING, and asking twice would leave two
        // rows nothing can tell apart.
        reportRetestRepository
                .findFirstByReportIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
                        report.getId()
                )
                .ifPresent(open -> {
                    throw conflict(
                            "A retest is already awaiting the researcher"
                    );
                });

        // Triage will not resolve a report without one, so this only catches a
        // row that predates that rule. Left in because a failed retest reopens
        // the report, and reopening it into a state triage cannot move it out
        // of would strand it.
        if (report.getSeverity() == null) {
            throw conflict(
                    "A final severity is required before requesting a retest"
            );
        }
        if (request.bountyReward() != null
                && !Boolean.TRUE.equals(
                        report.getProgram().getOffersBounties()
                )) {
            throw conflict(
                    "This program does not offer monetary bounties"
            );
        }

        UserProfile requester = findUserProfile(currentUserId());
        Integer highest = reportRetestRepository
                .findHighestAttemptNumber(report.getId());

        ReportRetest retest = ReportRetest.builder()
                .report(report)
                .attemptNumber(highest == null ? 1 : highest + 1)
                .environment(
                        request.environment() == null
                                ? ReportEnvironment.STAGING
                                : request.environment()
                )
                .targetEndpoint(trimToNull(request.targetEndpoint()))
                .requestNotes(trimToNull(request.notes()))
                .bountyReward(request.bountyReward())
                .requestedBy(requester)
                // From now rather than from requestedAt, which Hibernate only
                // fills in on the insert below — reading it here would put the
                // deadline fourteen days after null.
                .dueAt(LocalDateTime.now().plus(RETEST_RESPONSE_WINDOW))
                .build();
        reportRetestRepository.saveAndFlush(retest);
        report.getRetests().add(retest);

        report.setState(ReportState.RETESTING);
        reportRepository.saveAndFlush(report);

        postRetestNotice(
                report,
                requester.getId(),
                requester.getFullName()
                        + " asked for a retest of this report (attempt "
                        + retest.getAttemptNumber() + ")"
                        + describeRetestTarget(retest) + "."
                        + (retest.getRequestNotes() == null
                                ? ""
                                : " " + retest.getRequestNotes())
                        + " A verdict is due by "
                        + retest.getDueAt().toLocalDate() + "."
        );

        // Keyed on the attempt: a second request after a failed fix is news
        // again, and must not be swallowed as a repeat of the first.
        eventPublisher.publishEvent(NotificationEvent.to(
                report.getReporter().getId(),
                "Retest requested",
                report.getProgram().getName() + " deployed a fix for \""
                        + report.getTitle()
                        + "\" and asked you to confirm it holds. Please answer"
                        + " by " + retest.getDueAt().toLocalDate() + ".",
                NotificationType.REPORT,
                report.getId(),
                "report:" + report.getId() + ":retest:" + retest.getId()
                        + ":requested"
        ));

        ReportResponse response = reportMapper.toResponse(report);
        refreshReportCounts(report.getReporter().getId());
        return response;
    }

    /**
     * The researcher's verdict on an open retest.
     *
     * <p>A pass confirms what the organization already claimed, so the report
     * stays resolved and only the attempt closes. A fail reopens it to
     * VALID_CONFIRMED and clears {@code resolvedAt} — the fix did not hold, so
     * the report is not fixed, and leaving the timestamp behind would leave
     * every query that reads it believing otherwise.
     *
     * <p>Any bonus promised when the retest was asked for is paid either way:
     * it buys the verification, not a particular answer. See
     * {@link #payRetestBounty}.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LEADERBOARD, allEntries = true)
    public ReportResponse submitRetest(
            UUID id,
            SubmitRetestRequest request
    ) {
        requireRole(USER_ROLE);
        Report report = findReportableProgramReport(id);
        if (!report.getReporter().getId().equals(currentUserId())) {
            // Not "forbidden": to anyone but the reporter and the program's own
            // team, this report's existence is not public knowledge.
            throw reportNotFound();
        }
        if (report.getState() != ReportState.RETESTING) {
            throw conflict("This report is not awaiting a retest");
        }

        ReportRetest retest = reportRetestRepository
                .findFirstByReportIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
                        report.getId()
                )
                .orElseThrow(() -> conflict(
                        "This report has no retest awaiting your verification"
                ));

        UserProfile researcher = report.getReporter();
        retest.setVerdict(request.verdict());
        retest.setResultNotes(trimToNull(request.notes()));
        retest.setAttachmentIds(
                resolveRetestAttachmentIds(report, request.attachmentIds())
        );
        retest.setCompletedBy(researcher);
        retest.setCompletedAt(LocalDateTime.now());
        reportRetestRepository.saveAndFlush(retest);

        boolean fixed = request.verdict() == RetestVerdict.VERIFIED_FIXED;
        if (fixed) {
            // Back to where the report was before the retest was asked for.
            // resolvedAt is deliberately left alone: the organization resolved
            // this report on the day it says, and the retest confirmed that
            // rather than caused it. Nothing new reaches the hacktivity feed
            // either — the resolution was announced when it was made.
            report.setState(ReportState.RESOLVED);
        } else {
            report.setState(ReportState.VALID_CONFIRMED);
            report.setResolvedAt(null);
        }

        // Outside the branch on purpose. The bonus is for running the proof of
        // concept again, and that was done either way — see payRetestBounty.
        payRetestBounty(report, retest);
        reportRepository.saveAndFlush(report);

        postRetestNotice(
                report,
                researcher.getId(),
                researcher.getFullName()
                        + (fixed
                                ? " verified the fix. The finding is no longer"
                                        + " reproducible."
                                : " retested the fix and the finding is still"
                                        + " reproducible. The report has been"
                                        + " reopened.")
                        + (retest.getResultNotes() == null
                                ? ""
                                : " " + retest.getResultNotes())
        );

        eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                organizationAuthorization.findUserIdsWithPermission(
                        report.getProgram().getOrganizationId(),
                        OrganizationPermission.TRIAGE_REPORTS
                ),
                researcher.getId(),
                fixed ? "Retest passed" : "Retest failed",
                researcher.getFullName()
                        + (fixed
                                ? " confirmed the fix for \""
                                        + report.getTitle() + "\"."
                                : " could still reproduce \""
                                        + report.getTitle()
                                        + "\". The report has been reopened."),
                NotificationType.REPORT,
                report.getId(),
                "report:" + report.getId() + ":retest:" + retest.getId()
                        + ":completed"
        ));

        ReportResponse response = reportMapper.toResponse(report);
        refreshReportCounts(researcher.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findOverdueRetestIds() {
        return reportRetestRepository.findOverdueIds(LocalDateTime.now());
    }

    /**
     * The researcher never came back, so the attempt lapses.
     *
     * <p>The report returns to RESOLVED rather than reopening. The organization
     * resolved it and nobody has produced any evidence that the fix failed —
     * silence is not that evidence, and reopening on it would let a researcher
     * undo a resolution by ignoring it. {@code resolvedAt} is left where it
     * was for the same reason.
     *
     * <p>Nothing is paid. The bonus buys a verdict, and no verdict was given;
     * it is the one case where the promised bonus does not reach the
     * researcher. Asking again re-commits it on the new attempt.
     */
    @Override
    @Transactional
    public void expireRetest(UUID retestId) {
        ReportRetest retest = reportRetestRepository.findById(retestId)
                .orElse(null);
        // Listed and then answered, or closed by triage, between the sweep
        // reading the ids and reaching this one. Whatever happened to it, it is
        // no longer outstanding and there is nothing here to lapse.
        if (retest == null || !retest.isOpen()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        retest.setCompletedAt(now);
        // completedBy stays null: nobody completed this, the clock did. The
        // column is nullable precisely so an attempt can be closed by nobody.
        retest.setResultNotes(
                "Closed without a verdict: no answer by "
                        + retest.getDueAt().toLocalDate() + "."
        );
        reportRetestRepository.saveAndFlush(retest);

        Report report = retest.getReport();
        // Guarded rather than assumed. If triage moved the report on without
        // closing this attempt, its state is triage's decision and not a stale
        // retest's to overwrite.
        if (report.getState() == ReportState.RETESTING) {
            report.setState(ReportState.RESOLVED);
            reportRepository.saveAndFlush(report);
        }

        UUID requesterId = retest.getRequestedBy().getId();
        postRetestNotice(
                report,
                requesterId,
                "The retest window for attempt " + retest.getAttemptNumber()
                        + " closed on " + retest.getDueAt().toLocalDate()
                        + " without a verdict. The report stays resolved, and"
                        + " a further retest can be requested."
        );

        eventPublisher.publishEvent(NotificationEvent.to(
                report.getReporter().getId(),
                "Retest window closed",
                "The window to verify the fix for \"" + report.getTitle()
                        + "\" on " + report.getProgram().getName()
                        + " has closed without your verdict.",
                NotificationType.REPORT,
                report.getId(),
                "report:" + report.getId() + ":retest:" + retest.getId()
                        + ":expired"
        ));

        eventPublisher.publishEvent(new NotificationEvent(
                organizationAuthorization.findUserIdsWithPermission(
                        report.getProgram().getOrganizationId(),
                        OrganizationPermission.TRIAGE_REPORTS
                ),
                "Retest went unanswered",
                "Nobody verified the fix for \"" + report.getTitle()
                        + "\" within the retest window. The report stays"
                        + " resolved.",
                NotificationType.REPORT,
                report.getId(),
                "report:" + report.getId() + ":retest:" + retest.getId()
                        + ":expired:organization"
        ));
    }

    /**
     * Pays what was promised when the retest was asked for, if anything was.
     *
     * <p>On either verdict. The bonus is owed for re-running the proof of
     * concept, and that work is the same whether the fix held or not — paying
     * only for VERIFIED_FIXED would make the researcher better off saying the
     * vulnerability is gone, which is the one thing their answer must not
     * depend on. A fix that failed is the more valuable of the two answers
     * anyway; it is the one that stops a live bug being filed as fixed.
     *
     * <p>Recorded as an ordinary reward against the report so that it shows up
     * wherever bounties are counted, rather than as money that only exists on
     * the retest row. Attributed to whoever requested the retest: it is their
     * organization's budget, and they are the one who committed it.
     */
    private void payRetestBounty(Report report, ReportRetest retest) {
        if (retest.getBountyReward() == null) {
            return;
        }
        ReportReward reward = ReportReward.builder()
                .report(report)
                .amount(retest.getBountyReward())
                .awardedBy(retest.getRequestedBy())
                .note("Retest bonus for attempt " + retest.getAttemptNumber())
                .build();
        reportRewardRepository.saveAndFlush(reward);
        report.getRewards().add(reward);

        eventPublisher.publishEvent(NotificationEvent.to(
                report.getReporter().getId(),
                "You have been rewarded",
                describeReward(reward) + " for retesting \""
                        + report.getTitle() + "\" on "
                        + report.getProgram().getName() + ".",
                NotificationType.REWARD,
                report.getId(),
                "report:" + report.getId() + ":reward:" + reward.getId()
        ));
    }

    /**
     * Keeps only ids that really are attachments on this report.
     *
     * <p>Silently dropping an unknown id would let a retest cite evidence that
     * does not exist, and accepting an id from another report would leak that
     * report's contents through a download link.
     */
    private List<UUID> resolveRetestAttachmentIds(
            Report report,
            List<UUID> requested
    ) {
        if (requested == null || requested.isEmpty()) {
            return null;
        }
        Set<UUID> onReport = report.getAttachments().stream()
                .map(ReportAttachment::getId)
                .collect(Collectors.toSet());
        List<UUID> resolved = requested.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (UUID attachmentId : resolved) {
            if (!onReport.contains(attachmentId)) {
                throw badRequest(
                        "Attachment " + attachmentId
                                + " is not on this report"
                );
            }
        }
        return resolved.isEmpty() ? null : resolved;
    }

    /**
     * Writes the retest into the report thread, so the conversation reads in
     * one place rather than half in comments and half in a history panel.
     *
     * <p>Authored by the person who acted rather than by a platform account:
     * there is no system user to attribute it to, and "the organization asked"
     * is less useful to read back than which member did.
     */
    private void postRetestNotice(
            Report report,
            UUID authorId,
            String content
    ) {
        Comment notice = new Comment();
        notice.setCommentableType(CommentableType.REPORT);
        notice.setCommentableId(report.getId());
        notice.setAuthorId(authorId);
        notice.setContent(content);
        notice.setInternal(false);
        commentRepository.save(notice);
    }

    private String describeRetestTarget(ReportRetest retest) {
        StringBuilder description = new StringBuilder();
        if (retest.getEnvironment() != null) {
            description.append(" on ")
                    .append(describe(retest.getEnvironment().name()));
        }
        if (retest.getTargetEndpoint() != null) {
            description.append(" at ").append(retest.getTargetEndpoint());
        }
        return description.toString();
    }

    /**
     * Either permission is enough. Deploying the fix and running the triage
     * queue are often different people, and the spec for this workflow treats
     * both as entitled to say a fix is ready to check.
     */
    private Report findReportForRetestRequest(UUID reportId) {
        Report report = findReportableProgramReport(reportId);
        UUID organizationId = report.getProgram().getOrganizationId();
        UUID userId = currentUserId();

        if (organizationAuthorization.hasPermission(
                organizationId,
                userId,
                OrganizationPermission.TRIAGE_REPORTS
        )) {
            return report;
        }
        organizationAuthorization.requirePermission(
                organizationId,
                userId,
                OrganizationPermission.MANAGE_PROGRAM_STATE
        );
        return report;
    }

    /**
     * Closes an attempt nobody is going to answer.
     *
     * <p>Triage can still act on a report while a retest is open — a finding
     * turning out to be a duplicate does not wait for a researcher to come
     * back. Left alone, that attempt would stay open for ever and block every
     * later retest on the report, so it is closed with no verdict: something
     * happened to it, and it was not a verification.
     */
    private void closeOpenRetest(Report report, UserProfile actor) {
        reportRetestRepository
                .findFirstByReportIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
                        report.getId()
                )
                .ifPresent(open -> {
                    open.setCompletedBy(actor);
                    open.setCompletedAt(LocalDateTime.now());
                    open.setResultNotes(
                            "Closed without a verdict: triage moved the report "
                                    + "to " + describe(report.getState()) + "."
                    );
                    reportRetestRepository.saveAndFlush(open);
                });
    }

    private String describe(String enumName) {
        return enumName.toLowerCase().replace('_', ' ');
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
                attachmentValidator.validate(
                        file,
                        new AttachmentScanContext(
                                report.getProgram().getOrganizationId(),
                                reportId,
                                NotificationType.SECURITY,
                                "a report"
                        )
                );
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

    /**
     * <p>REJECTED and DUPLICATE are the end of the line. RESOLVED is not: a fix
     * can turn out not to have held, whether because a retest was never asked
     * for or because one came back wrong. Reopening it to VALID_CONFIRMED is
     * the only move allowed out of RESOLVED — the finding was agreed, so that
     * is the state it goes back to, and rejecting or duplicating something the
     * organization has already paid and closed is not a triage decision.
     */
    private void validateTriageTransition(
            ReportState current,
            ReportState target
    ) {
        if (current == ReportState.REJECTED
                || current == ReportState.DUPLICATE) {
            throw conflict("A terminal report cannot be triaged again");
        }
        if (current == ReportState.RESOLVED
                && target != ReportState.VALID_CONFIRMED) {
            throw conflict(
                    "A resolved report can only be reopened to valid confirmed"
            );
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
