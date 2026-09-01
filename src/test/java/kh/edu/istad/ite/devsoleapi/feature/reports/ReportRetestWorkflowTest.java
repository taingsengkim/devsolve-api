package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.ratelimit.InMemoryRateLimitStore;
import kh.edu.istad.ite.devsoleapi.common.storage.ObjectStorageService;
import kh.edu.istad.ite.devsoleapi.feature.comments.Comment;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityRecorder;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.organization.CompanyIdentityService;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.ResearcherAccessService;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RequestRetestRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SubmitRetestRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportAttachment;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportRetest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.RetestVerdict;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The retest loop: a company deploys a fix, asks the researcher who found the
 * bug to confirm it, and the report either resolves or comes back.
 */
@ExtendWith(MockitoExtension.class)
class ReportRetestWorkflowTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportAttachmentRepository reportAttachmentRepository;
    @Mock
    private ReportRewardRepository reportRewardRepository;
    @Mock
    private ReportRetestRepository reportRetestRepository;
    @Mock
    private DisputeRepository disputeRepository;
    @Mock
    private WeaknessRepository weaknessRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private ResearcherAccessService researcherAccessService;
    @Mock
    private CompanyIdentityService companyIdentityService;
    @Mock
    private ReportMapper reportMapper;
    @Mock
    private FollowNotificationService followNotificationService;
    @Mock
    private AttachmentValidator attachmentValidator;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private HacktivityRecorder hacktivityRecorder;
    @Mock
    private CommentRepository commentRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestingARetestMovesTheReportAndRecordsTheFirstAttempt() {
        UUID ownerId = UUID.randomUUID();
        Report report = confirmedReport();
        stubOwnedReport(report, ownerId);
        stubRequesterProfile(ownerId);
        stubNoOpenRetest(report);
        when(reportRetestRepository.findHighestAttemptNumber(report.getId()))
                .thenReturn(null);
        stubRetestSave();

        service().requestRetest(
                report.getId(),
                new RequestRetestRequest(
                        ReportEnvironment.STAGING,
                        "https://staging.api.example.test/v1/orders",
                        "Patch deployed in build v2.4.1.",
                        null
                )
        );

        ReportRetest retest = savedRetest();
        assertEquals(1, retest.getAttemptNumber());
        assertEquals(ReportEnvironment.STAGING, retest.getEnvironment());
        assertEquals(
                "https://staging.api.example.test/v1/orders",
                retest.getTargetEndpoint()
        );
        assertEquals(ownerId, retest.getRequestedBy().getId());
        assertNull(retest.getVerdict());
        assertTrue(retest.isOpen());
        assertEquals(ReportState.RETESTING, report.getState());
    }

    /**
     * The environment is what tells the researcher which deployment to point
     * their proof of concept at. Staging is the safe assumption: re-running an
     * exploit against production because a field was left blank is the one
     * outcome nobody wants.
     */
    @Test
    void anUnspecifiedEnvironmentDefaultsToStaging() {
        UUID ownerId = UUID.randomUUID();
        Report report = confirmedReport();
        stubOwnedReport(report, ownerId);
        stubRequesterProfile(ownerId);
        stubNoOpenRetest(report);
        when(reportRetestRepository.findHighestAttemptNumber(report.getId()))
                .thenReturn(null);
        stubRetestSave();

        service().requestRetest(
                report.getId(),
                new RequestRetestRequest(null, null, null, null)
        );

        assertEquals(
                ReportEnvironment.STAGING,
                savedRetest().getEnvironment()
        );
    }

    @Test
    void aSecondRetestTakesTheNextAttemptNumber() {
        UUID ownerId = UUID.randomUUID();
        Report report = confirmedReport();
        stubOwnedReport(report, ownerId);
        stubRequesterProfile(ownerId);
        stubNoOpenRetest(report);
        when(reportRetestRepository.findHighestAttemptNumber(report.getId()))
                .thenReturn(1);
        stubRetestSave();

        service().requestRetest(
                report.getId(),
                new RequestRetestRequest(null, null, "Second attempt.", null)
        );

        assertEquals(2, savedRetest().getAttemptNumber());
    }

    @Test
    void aReportStillInTriageCannotBeSentForRetest() {
        UUID ownerId = UUID.randomUUID();
        Report report = confirmedReport();
        report.setState(ReportState.TRIAGING);
        stubOwnedReport(report, ownerId);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service().requestRetest(
                        report.getId(),
                        new RequestRetestRequest(null, null, null, null)
                )
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(reportRetestRepository, never())
                .saveAndFlush(any(ReportRetest.class));
    }

    /**
     * Resolution is the step after a passing retest, so a report that has
     * already been resolved has nothing left for one to decide.
     */
    @Test
    void aResolvedReportCannotBeSentForRetest() {
        UUID ownerId = UUID.randomUUID();
        Report report = confirmedReport();
        report.setState(ReportState.RESOLVED);
        stubOwnedReport(report, ownerId);

        assertEquals(
                HttpStatus.CONFLICT,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service().requestRetest(
                                report.getId(),
                                new RequestRetestRequest(
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ).getStatusCode()
        );
    }

    @Test
    void aRetestBonusIsRefusedOnAProgramThatPaysNoBounties() {
        UUID ownerId = UUID.randomUUID();
        Report report = confirmedReport();
        report.getProgram().setOffersBounties(false);
        stubOwnedReport(report, ownerId);
        stubNoOpenRetest(report);

        assertEquals(
                HttpStatus.CONFLICT,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service().requestRetest(
                                report.getId(),
                                new RequestRetestRequest(
                                        null,
                                        null,
                                        null,
                                        new BigDecimal("50.00")
                                )
                        )
                ).getStatusCode()
        );
    }

    @Test
    void aVerifiedFixResolvesTheReportAndClosesTheAttempt() {
        UUID researcherId = UUID.randomUUID();
        Report report = retestingReport(researcherId);
        ReportRetest open = openRetest(report, 1, null);
        stubResearcherSubmission(researcherId, report, open);

        service().submitRetest(
                report.getId(),
                new SubmitRetestRequest(
                        RetestVerdict.VERIFIED_FIXED,
                        "The endpoint now returns 403.",
                        null
                )
        );

        assertEquals(ReportState.RESOLVED, report.getState());
        assertNotNull(report.getResolvedAt());
        assertEquals(RetestVerdict.VERIFIED_FIXED, open.getVerdict());
        assertEquals("The endpoint now returns 403.", open.getResultNotes());
        assertEquals(researcherId, open.getCompletedBy().getId());
        assertNotNull(open.getCompletedAt());
        verify(hacktivityRecorder).recordResolved(report);
    }

    @Test
    void aStillVulnerableVerdictSendsTheReportBackToConfirmed() {
        UUID researcherId = UUID.randomUUID();
        Report report = retestingReport(researcherId);
        ReportRetest open = openRetest(report, 1, null);
        stubResearcherSubmission(researcherId, report, open);

        service().submitRetest(
                report.getId(),
                new SubmitRetestRequest(
                        RetestVerdict.STILL_VULNERABLE,
                        "The payload still works.",
                        null
                )
        );

        assertEquals(ReportState.VALID_CONFIRMED, report.getState());
        assertNull(report.getResolvedAt());
        assertEquals(RetestVerdict.STILL_VULNERABLE, open.getVerdict());
        assertNotNull(open.getCompletedAt());
        verify(hacktivityRecorder, never()).recordResolved(any(Report.class));
        verify(reportRewardRepository, never())
                .saveAndFlush(any(ReportReward.class));
    }

    /**
     * The bonus is owed for the verification work, so it is paid when that work
     * comes back — not when it was promised, and not when the answer is that
     * the fix did not hold.
     */
    @Test
    void aPromisedBonusIsPaidOnlyWhenTheFixIsVerified() {
        UUID researcherId = UUID.randomUUID();
        Report report = retestingReport(researcherId);
        ReportRetest open = openRetest(report, 1, new BigDecimal("50.00"));
        stubResearcherSubmission(researcherId, report, open);
        when(reportRewardRepository.saveAndFlush(any(ReportReward.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().submitRetest(
                report.getId(),
                new SubmitRetestRequest(
                        RetestVerdict.VERIFIED_FIXED,
                        null,
                        null
                )
        );

        ArgumentCaptor<ReportReward> rewardCaptor =
                ArgumentCaptor.forClass(ReportReward.class);
        verify(reportRewardRepository).saveAndFlush(rewardCaptor.capture());
        ReportReward reward = rewardCaptor.getValue();
        assertEquals(new BigDecimal("50.00"), reward.getAmount());
        assertEquals(
                open.getRequestedBy().getId(),
                reward.getAwardedBy().getId()
        );
    }

    @Test
    void aPromisedBonusIsNotPaidWhenTheFixFails() {
        UUID researcherId = UUID.randomUUID();
        Report report = retestingReport(researcherId);
        ReportRetest open = openRetest(report, 1, new BigDecimal("50.00"));
        stubResearcherSubmission(researcherId, report, open);

        service().submitRetest(
                report.getId(),
                new SubmitRetestRequest(
                        RetestVerdict.STILL_VULNERABLE,
                        null,
                        null
                )
        );

        verify(reportRewardRepository, never())
                .saveAndFlush(any(ReportReward.class));
    }

    /**
     * The report belongs to whoever filed it. To anybody else it is a private
     * finding they should not learn the existence of, which is why this is a
     * 404 rather than a 403.
     */
    @Test
    void nobodyButTheReporterMaySubmitAVerdict() {
        UUID researcherId = UUID.randomUUID();
        Report report = retestingReport(researcherId);
        authenticate(UUID.randomUUID(), "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().submitRetest(
                        report.getId(),
                        new SubmitRetestRequest(
                                RetestVerdict.VERIFIED_FIXED,
                                null,
                                null
                        )
                )
        );
        assertEquals(ReportState.RETESTING, report.getState());
    }

    @Test
    void aReportNotAwaitingARetestCannotBeVerified() {
        UUID researcherId = UUID.randomUUID();
        Report report = retestingReport(researcherId);
        report.setState(ReportState.VALID_CONFIRMED);
        authenticate(researcherId, "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));

        assertEquals(
                HttpStatus.CONFLICT,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service().submitRetest(
                                report.getId(),
                                new SubmitRetestRequest(
                                        RetestVerdict.VERIFIED_FIXED,
                                        null,
                                        null
                                )
                        )
                ).getStatusCode()
        );
    }

    /**
     * An id from another report would otherwise become a download link on this
     * one, which is a way to read a stranger's evidence.
     */
    @Test
    void evidenceMustBeAnAttachmentOnThisReport() {
        UUID researcherId = UUID.randomUUID();
        Report report = retestingReport(researcherId);
        ReportRetest open = openRetest(report, 1, null);
        authenticate(researcherId, "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(reportRetestRepository
                .findFirstByReportIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
                        report.getId()
                ))
                .thenReturn(Optional.of(open));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service().submitRetest(
                                report.getId(),
                                new SubmitRetestRequest(
                                        RetestVerdict.VERIFIED_FIXED,
                                        null,
                                        List.of(UUID.randomUUID())
                                )
                        )
                ).getStatusCode()
        );
        assertEquals(ReportState.RETESTING, report.getState());
    }

    @Test
    void evidenceAlreadyOnTheReportIsKept() {
        UUID researcherId = UUID.randomUUID();
        Report report = retestingReport(researcherId);
        ReportAttachment evidence = ReportAttachment.builder()
                .id(UUID.randomUUID())
                .report(report)
                .build();
        report.getAttachments().add(evidence);
        ReportRetest open = openRetest(report, 1, null);
        stubResearcherSubmission(researcherId, report, open);

        service().submitRetest(
                report.getId(),
                new SubmitRetestRequest(
                        RetestVerdict.VERIFIED_FIXED,
                        null,
                        List.of(evidence.getId())
                )
        );

        assertEquals(List.of(evidence.getId()), open.getAttachmentIds());
    }

    /** Both sides read the thread, so both sides see the retest in it. */
    @Test
    void bothHalvesOfTheLoopAreWrittenIntoTheReportThread() {
        UUID ownerId = UUID.randomUUID();
        Report report = confirmedReport();
        stubOwnedReport(report, ownerId);
        stubRequesterProfile(ownerId);
        stubNoOpenRetest(report);
        when(reportRetestRepository.findHighestAttemptNumber(report.getId()))
                .thenReturn(null);
        stubRetestSave();

        service().requestRetest(
                report.getId(),
                new RequestRetestRequest(null, null, "Fix is on staging.", null)
        );

        ArgumentCaptor<Comment> commentCaptor =
                ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(commentCaptor.capture());
        Comment notice = commentCaptor.getValue();
        assertEquals(report.getId(), notice.getCommentableId());
        assertEquals(ownerId, notice.getAuthorId());
        assertTrue(notice.getContent().contains("Fix is on staging."));
    }

    @Test
    void theResearcherIsToldWhenARetestIsRequested() {
        UUID ownerId = UUID.randomUUID();
        Report report = confirmedReport();
        stubOwnedReport(report, ownerId);
        stubRequesterProfile(ownerId);
        stubNoOpenRetest(report);
        when(reportRetestRepository.findHighestAttemptNumber(report.getId()))
                .thenReturn(null);
        stubRetestSave();

        service().requestRetest(
                report.getId(),
                new RequestRetestRequest(null, null, null, null)
        );

        ArgumentCaptor<NotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent event = eventCaptor.getValue();
        assertEquals(
                List.of(report.getReporter().getId()),
                List.copyOf(event.recipientIds())
        );
        assertTrue(event.eventKey().endsWith(":requested"));
    }

    /**
     * Triage does not have to wait for a researcher who never comes back — a
     * finding turning out to be a duplicate is not their call. The attempt is
     * closed with no verdict rather than left open, or it would block every
     * later retest on the report.
     */
    @Test
    void triagingOutOfRetestClosesTheOpenAttemptWithoutAVerdict() {
        UUID ownerId = UUID.randomUUID();
        Report report = confirmedReport();
        report.setState(ReportState.RETESTING);
        ReportRetest open = openRetest(report, 1, null);
        stubOwnedReport(report, ownerId);
        stubRequesterProfile(ownerId);
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRetestRepository
                .findFirstByReportIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
                        report.getId()
                ))
                .thenReturn(Optional.of(open));
        when(reportRetestRepository.saveAndFlush(any(ReportRetest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        report.getReportedSeverity(),
                        ReportState.VALID_CONFIRMED,
                        null,
                        null
                )
        );

        assertNotNull(open.getCompletedAt());
        assertNull(open.getVerdict());
        assertEquals(ownerId, open.getCompletedBy().getId());
    }

    private void stubOwnedReport(Report report, UUID ownerId) {
        Organization organization = new Organization();
        organization.setId(report.getProgram().getOrganizationId());
        organization.setOwner(user(ownerId));
        organization.setStatus(OrganizationStatus.ACTIVE);

        authenticate(ownerId, "COMPANY");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
    }

    /**
     * Separate from {@link #stubOwnedReport} because the cases that refuse a
     * request never get as far as resolving who is making it, and Mockito's
     * strict stubbing is right to say so.
     */
    private void stubRequesterProfile(UUID ownerId) {
        when(userProfileRepository.findById(ownerId))
                .thenReturn(Optional.of(user(ownerId)));
    }

    private void stubNoOpenRetest(Report report) {
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRetestRepository
                .findFirstByReportIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
                        report.getId()
                ))
                .thenReturn(Optional.empty());
    }

    private void stubRetestSave() {
        when(reportRetestRepository.saveAndFlush(any(ReportRetest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubResearcherSubmission(
            UUID researcherId,
            Report report,
            ReportRetest open
    ) {
        authenticate(researcherId, "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(reportRetestRepository
                .findFirstByReportIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
                        report.getId()
                ))
                .thenReturn(Optional.of(open));
        when(reportRetestRepository.saveAndFlush(any(ReportRetest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ReportRetest savedRetest() {
        ArgumentCaptor<ReportRetest> captor =
                ArgumentCaptor.forClass(ReportRetest.class);
        verify(reportRetestRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private ReportRetest openRetest(
            Report report,
            int attemptNumber,
            BigDecimal bountyReward
    ) {
        return ReportRetest.builder()
                .id(UUID.randomUUID())
                .report(report)
                .attemptNumber(attemptNumber)
                .environment(ReportEnvironment.STAGING)
                .bountyReward(bountyReward)
                .requestedBy(user(UUID.randomUUID()))
                .requestedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    /** Confirmed, severity settled, on a program that pays bounties. */
    private Report confirmedReport() {
        Program program = new Program();
        program.setId(UUID.randomUUID());
        program.setOrganizationId(UUID.randomUUID());
        program.setName("Acme");
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setOffersBounties(true);

        return Report.builder()
                .id(UUID.randomUUID())
                .program(program)
                .reporter(user(UUID.randomUUID()))
                .title("Broken access control")
                .vulnerabilityInformation("A user can read another account.")
                .reportedSeverity(Severity.HIGH)
                .severity(Severity.HIGH)
                .state(ReportState.VALID_CONFIRMED)
                .build();
    }

    private Report retestingReport(UUID researcherId) {
        Report report = confirmedReport();
        report.setReporter(user(researcherId));
        report.setState(ReportState.RETESTING);
        return report;
    }

    private UserProfile user(UUID userId) {
        UserProfile user = new UserProfile();
        user.setId(userId);
        user.setFullName("Person " + userId.toString().substring(0, 4));
        return user;
    }

    private ReportServiceImpl service() {
        return new ReportServiceImpl(
                reportRepository,
                reportAttachmentRepository,
                reportRewardRepository,
                reportRetestRepository,
                disputeRepository,
                weaknessRepository,
                programRepository,
                userProfileRepository,
                new OrganizationAuthorizationService(
                        organizationRepository,
                        organizationMemberRepository
                ),
                researcherAccessService,
                companyIdentityService,
                reportMapper,
                followNotificationService,
                attachmentValidator,
                objectStorageService,
                eventPublisher,
                new ReportRateLimiter(new InMemoryRateLimitStore()),
                hacktivityRecorder,
                commentRepository
        );
    }

    private void authenticate(UUID userId, String role) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }
}
