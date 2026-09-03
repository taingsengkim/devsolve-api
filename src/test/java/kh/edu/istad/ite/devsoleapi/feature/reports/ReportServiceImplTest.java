package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.AttachmentScanContext;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.ratelimit.InMemoryRateLimitStore;
import kh.edu.istad.ite.devsoleapi.common.storage.ObjectStorageService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.*;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.ResearcherAccessService;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportAttachment;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportAttachmentRepository reportAttachmentRepository;

    @Mock
    private ReportRewardRepository reportRewardRepository;

    @Mock
    private ReportRetestRepository reportRetestRepository;

    @Mock
    private kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository
            commentRepository;

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
    private kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityRecorder
            hacktivityRecorder;

    @Mock
    private ReportActivityRepository reportActivityRepository;

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
    private CompanyIdentityService companyIdentityService;

    @Mock
    private ResearcherAccessService researcherAccessService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * The three answers a reporter may give about the weakness class, and the
     * one combination that is refused.
     */
    @Test
    void aReporterCanNameAWeaknessTheCatalogDoesNotHave() {
        UUID hackerId = UUID.randomUUID();
        UserProfile hacker = user(hackerId);
        Program program = activeApprovedProgram();
        ProgramAsset asset = program.getAssets().getFirst();
        authenticate(hackerId, "USER");

        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(hacker));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().create(
                program.getId(),
                withWeakness(
                        reportRequest(Severity.HIGH, asset.getId()),
                        null,
                        "  Prompt injection via tool output  "
                )
        );

        ArgumentCaptor<Report> reportCaptor =
                ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        assertEquals(
                "Prompt injection via tool output",
                reportCaptor.getValue().getSuggestedWeakness()
        );
        // Nothing was written to the shared catalog.
        assertNull(reportCaptor.getValue().getWeakness());
        verify(weaknessRepository, never())
                .findByIdAndIsActiveTrue(any(UUID.class));
    }

    /** "Not sure" is a normal answer; triage classifies it later. */
    @Test
    void aReporterMayLeaveTheWeaknessUnset() {
        UUID hackerId = UUID.randomUUID();
        UserProfile hacker = user(hackerId);
        Program program = activeApprovedProgram();
        ProgramAsset asset = program.getAssets().getFirst();
        authenticate(hackerId, "USER");

        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(hacker));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().create(
                program.getId(),
                reportRequest(Severity.HIGH, asset.getId())
        );

        ArgumentCaptor<Report> reportCaptor =
                ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        assertNull(reportCaptor.getValue().getWeakness());
        assertNull(reportCaptor.getValue().getSuggestedWeakness());
    }

    /**
     * Refused rather than picked between. The two say different things about
     * one field, and guessing files the report under something nobody chose.
     */
    @Test
    void aReporterCannotSendBothACatalogEntryAndTheirOwnName() {
        UUID hackerId = UUID.randomUUID();
        Program program = activeApprovedProgram();
        ProgramAsset asset = program.getAssets().getFirst();
        UUID weaknessId = UUID.randomUUID();
        authenticate(hackerId, "USER");

        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(user(hackerId)));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(weaknessRepository.findByIdAndIsActiveTrue(weaknessId))
                .thenReturn(Optional.of(weakness(weaknessId)));

        CreateReportRequest request = withWeakness(
                reportRequest(Severity.HIGH, asset.getId()),
                weaknessId,
                "Prompt injection via tool output"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().create(program.getId(), request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    void hackerCreatesPrivateReportForInScopeAsset() {
        UUID hackerId = UUID.randomUUID();
        UserProfile hacker = user(hackerId);
        Program program = activeApprovedProgram();
        ProgramAsset asset = program.getAssets().getFirst();
        authenticate(hackerId, "USER");

        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(hacker));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().create(
                program.getId(),
                reportRequest(Severity.HIGH, asset.getId())
        );

        ArgumentCaptor<Report> reportCaptor =
                ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        Report report = reportCaptor.getValue();
        assertSame(program, report.getProgram());
        assertSame(hacker, report.getReporter());
        assertSame(asset, report.getAsset());
        assertEquals(Severity.HIGH, report.getReportedSeverity());
        assertNull(report.getTriageSeverity());
        assertNull(report.getSeverity());
        assertEquals(ReportState.NEW, report.getState());
    }

    /**
     * total_reports and valid_reports are read by the leaderboard, the public
     * profile and the admin user list, and nothing used to write them — every
     * researcher showed zero reports for ever. Submission is one of the two
     * moments that can change them.
     */
    @Test
    void submittingAReportRefreshesTheReportersCounters() {
        UUID hackerId = UUID.randomUUID();
        UserProfile hacker = user(hackerId);
        Program program = activeApprovedProgram();
        ProgramAsset asset = program.getAssets().getFirst();
        authenticate(hackerId, "USER");

        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(hacker));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().create(
                program.getId(),
                reportRequest(Severity.HIGH, asset.getId())
        );

        verify(userProfileRepository).refreshReportCounts(
                eq(hackerId),
                // RETESTING counts as valid: a report only reaches it from
                // VALID_CONFIRMED, so the finding was already agreed and the
                // researcher should not lose the credit for it while the
                // company has its fix checked.
                eq(Set.of(
                        ReportState.VALID_CONFIRMED,
                        ReportState.RETESTING,
                        ReportState.RESOLVED
                ))
        );
    }

    /**
     * The hourly cap is counted from the reports table rather than the burst
     * store, so this is the half that has to be exact.
     */
    @Test
    void hackerCannotCreateReportPastTheHourlyLimit() {
        UUID hackerId = UUID.randomUUID();
        UserProfile hacker = user(hackerId);
        Program program = activeApprovedProgram();
        ProgramAsset asset = program.getAssets().getFirst();
        authenticate(hackerId, "USER");

        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(hacker));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(reportRepository.countByReporterSince(eq(hackerId), any()))
                .thenReturn((long) ReportRateLimiter.SUSTAINED_LIMIT);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().create(
                        program.getId(),
                        reportRequest(Severity.HIGH, asset.getId())
                )
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    void hackerCannotCreateReportWithoutCompanyApproval() {
        UUID hackerId = UUID.randomUUID();
        UserProfile hacker = user(hackerId);
        Program program = activeApprovedProgram();
        authenticate(hackerId, "USER");

        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(hacker));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Acme has not reviewed your access request yet."
        )).when(researcherAccessService).requireApprovedReporter(
                program.getOrganizationId(),
                hackerId
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().create(
                        program.getId(),
                        reportRequest(
                                Severity.HIGH,
                                program.getAssets().getFirst().getId()
                        )
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void approvedHackerPassesTheCompanyGateOnTheOwningOrganization() {
        UUID hackerId = UUID.randomUUID();
        UserProfile hacker = user(hackerId);
        Program program = activeApprovedProgram();
        authenticate(hackerId, "USER");

        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(hacker));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().create(
                program.getId(),
                reportRequest(
                        Severity.HIGH,
                        program.getAssets().getFirst().getId()
                )
        );

        verify(researcherAccessService).requireApprovedReporter(
                program.getOrganizationId(),
                hackerId
        );
    }

    @Test
    void hackerCannotCreateReportForDeletedProgram() {
        UUID hackerId = UUID.randomUUID();
        UserProfile hacker = user(hackerId);
        Program program = activeApprovedProgram();
        program.setDeletedAt(LocalDateTime.now());
        authenticate(hackerId, "USER");
        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(hacker));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().create(
                        program.getId(),
                        reportRequest(
                                Severity.HIGH,
                                program.getAssets().getFirst().getId()
                        )
                )
        );

        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    void matchingCompanyTriageSetsFinalSeverityWithoutDispute() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.VALID_CONFIRMED,
                        null,
                        null
                )
        );

        assertEquals(Severity.HIGH, report.getTriageSeverity());
        assertEquals(Severity.HIGH, report.getSeverity());
        assertEquals(ReportState.VALID_CONFIRMED, report.getState());
        verify(disputeRepository, never()).save(any(Dispute.class));
    }

    /**
     * The classification a reporter picks is a guess from a catalog they may
     * not know well. Triage is where it gets settled.
     */
    @Test
    void triageReclassifiesTheReport() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        Weakness reported = Weakness.builder()
                .id(UUID.randomUUID())
                .cweId("CWE-79")
                .name("Cross-site Scripting (XSS)")
                .isActive(true)
                .build();
        Weakness corrected = Weakness.builder()
                .id(UUID.randomUUID())
                .cweId("CWE-89")
                .name("SQL Injection")
                .isActive(true)
                .build();
        report.setWeakness(reported);

        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(weaknessRepository.findByIdAndIsActiveTrue(corrected.getId()))
                .thenReturn(Optional.of(corrected));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.VALID_CONFIRMED,
                        null,
                        corrected.getId()
                )
        );

        assertSame(corrected, report.getWeakness());

        // Triage is the other moment the reporter's counters can change: this
        // is the transition that turns a submission into a valid report.
        verify(userProfileRepository).refreshReportCounts(
                eq(report.getReporter().getId()),
                // RETESTING counts as valid: a report only reaches it from
                // VALID_CONFIRMED, so the finding was already agreed and the
                // researcher should not lose the credit for it while the
                // company has its fix checked.
                eq(Set.of(
                        ReportState.VALID_CONFIRMED,
                        ReportState.RETESTING,
                        ReportState.RESOLVED
                ))
        );
    }

    /**
     * Re-triaging for a state change alone must not undo a classification
     * somebody already corrected.
     */
    @Test
    void triageWithoutAWeaknessKeepsTheOneTheReportHas() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        Weakness existing = Weakness.builder()
                .id(UUID.randomUUID())
                .cweId("CWE-89")
                .name("SQL Injection")
                .isActive(true)
                .build();
        report.setWeakness(existing);

        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.VALID_CONFIRMED,
                        null,
                        null
                )
        );

        assertSame(existing, report.getWeakness());
        verify(weaknessRepository, never()).findByIdAndIsActiveTrue(any());
    }

    /**
     * A retired class stays readable on the reports already filed under it,
     * but nothing new can be classified into it.
     */
    @Test
    void triageCannotReclassifyIntoARetiredWeakness() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        UUID retiredId = UUID.randomUUID();
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(weaknessRepository.findByIdAndIsActiveTrue(retiredId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().triage(
                        report.getId(),
                        new TriageReportRequest(
                                Severity.HIGH,
                                ReportState.VALID_CONFIRMED,
                                null,
                                retiredId
                        )
                )
        );

        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    void mismatchingCompanyTriageLeavesFinalBlankAndOpensDispute() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.CRITICAL);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);
        when(disputeRepository.save(any(Dispute.class)))
                .thenAnswer(invocation -> {
                    Dispute dispute = invocation.getArgument(0);
                    dispute.setId(UUID.randomUUID());
                    return dispute;
                });

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.MEDIUM,
                        ReportState.VALID_CONFIRMED,
                        null,
                        null
                )
        );

        assertEquals(Severity.MEDIUM, report.getTriageSeverity());
        assertNull(report.getSeverity());
        verify(disputeRepository).save(any(Dispute.class));
        assertEquals(1, report.getDisputes().size());
        assertSame(
                report.getReporter(),
                report.getDisputes().getFirst().getRaisedBy()
        );
    }

    @Test
    void adminRulingSurvivesReTriageAndLetsTheReportResolve() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.CRITICAL);
        report.setState(ReportState.VALID_CONFIRMED);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        report.getId(),
                        EnumSet.of(
                                DisputeStatus.OPEN,
                                DisputeStatus.UNDER_REVIEW
                        )
                ))
                .thenReturn(Optional.empty());
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        report.getId(),
                        EnumSet.of(
                                DisputeStatus.RESOLVED,
                                DisputeStatus.DISMISSED
                        )
                ))
                .thenReturn(Optional.of(
                        Dispute.builder()
                                .id(UUID.randomUUID())
                                .report(report)
                                .raisedBy(report.getReporter())
                                .reason("Severities differ")
                                .status(DisputeStatus.RESOLVED)
                                .resolvedSeverity(Severity.MEDIUM)
                                .build()
                ));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.LOW,
                        ReportState.RESOLVED,
                        null,
                        null
                )
        );

        // The administrator's number stands, not either side's claim, and the
        // report is resolvable on it rather than deadlocked a second time.
        assertEquals(Severity.MEDIUM, report.getSeverity());
        assertEquals(ReportState.RESOLVED, report.getState());
        assertNotNull(report.getResolvedAt());
        verify(disputeRepository, never()).save(any(Dispute.class));
    }

    @Test
    void cvssScoreMustMatchTheClaimedSeverity() {
        UUID hackerId = UUID.randomUUID();
        Program program = activeApprovedProgram();
        authenticate(hackerId, "USER");
        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(user(hackerId)));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));

        CreateReportRequest request = new CreateReportRequest(
                "Broken access control",
                "A user can read another account.",
                null, null, null, null, null, null, null, null,
                Severity.CRITICAL,
                "CVSS:3.1/AV:N/AC:H/PR:H/UI:R/S:U/C:L/I:N/A:N",
                new java.math.BigDecimal("2.1"),
                null,
                null,
                program.getAssets().getFirst().getId()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().create(program.getId(), request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    void unrelatedHackerCannotDiscoverPrivateReport() {
        UUID hackerId = UUID.randomUUID();
        Report report = newReport(Severity.LOW);
        authenticate(hackerId, "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().findById(report.getId())
        );
    }

    @Test
    void reporterCanDiscussButCannotSeeOrCreateInternalNotes() {
        Report report = newReport(Severity.LOW);
        UUID reporterId = report.getReporter().getId();
        authenticate(reporterId, "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));

        ReportDiscussionAccess access =
                service().requireDiscussionAccess(report.getId());

        assertFalse(access.canViewInternal());
        assertTrue(access.canComment());
        assertFalse(access.canCreateInternal());
    }

    @Test
    void organizationViewerSeesInternalNotesButCannotWriteThem() {
        UUID viewerId = UUID.randomUUID();
        Report report = newReport(Severity.MEDIUM);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                UUID.randomUUID()
        );
        OrganizationMember viewer = new OrganizationMember();
        viewer.setOrganization(organization);
        viewer.setUser(user(viewerId));
        viewer.setStatus(MembershipStatus.ACTIVE);
        viewer.setPermissions(Set.of(
                OrganizationPermission.VIEW_REPORTS
        ));
        authenticate(viewerId, "COMPANY");

        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationMemberRepository
                .findByOrganizationIdAndUserId(
                        organization.getId(),
                        viewerId
                ))
                .thenReturn(Optional.of(viewer));

        ReportDiscussionAccess access =
                service().requireDiscussionAccess(report.getId());

        assertTrue(access.canViewInternal());
        assertFalse(access.canComment());
        assertFalse(access.canCreateInternal());
    }

    @Test
    void organizationTriagerCanParticipateAndCreateInternalNotes() {
        UUID triagerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                UUID.randomUUID()
        );
        OrganizationMember triager = new OrganizationMember();
        triager.setOrganization(organization);
        triager.setUser(user(triagerId));
        triager.setStatus(MembershipStatus.ACTIVE);
        triager.setPermissions(Set.of(
                OrganizationPermission.VIEW_REPORTS,
                OrganizationPermission.TRIAGE_REPORTS
        ));
        authenticate(triagerId, "COMPANY");

        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationMemberRepository
                .findByOrganizationIdAndUserId(
                        organization.getId(),
                        triagerId
                ))
                .thenReturn(Optional.of(triager));

        ReportDiscussionAccess access =
                service().requireDiscussionAccess(report.getId());

        assertTrue(access.canViewInternal());
        assertTrue(access.canComment());
        assertTrue(access.canCreateInternal());
    }

    @Test
    void organizationViewerCanReadButCannotTriage() {
        UUID viewerId = UUID.randomUUID();
        Report report = newReport(Severity.MEDIUM);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                UUID.randomUUID()
        );
        OrganizationMember viewer = new OrganizationMember();
        viewer.setOrganization(organization);
        viewer.setUser(user(viewerId));
        viewer.setStatus(MembershipStatus.ACTIVE);
        viewer.setRole(OrgRole.VIEWER);
        authenticate(viewerId, "COMPANY");

        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationMemberRepository
                .findByOrganizationIdAndUserId(
                        organization.getId(),
                        viewerId
                ))
                .thenReturn(Optional.of(viewer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().triage(
                        report.getId(),
                        new TriageReportRequest(
                                Severity.MEDIUM,
                                ReportState.VALID_CONFIRMED,
                                null,
                                null
                        )
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void reporterCanUploadValidatedImageWhileReportIsNew() {
        Report report = newReport(Severity.HIGH);
        UUID reporterId = report.getReporter().getId();
        authenticate(reporterId, "USER");
        byte[] content = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.png",
                "image/png",
                content
        );
        AttachmentValidator.ValidatedAttachment validated =
                new AttachmentValidator.ValidatedAttachment(
                        "proof.png",
                        "png",
                        "image/png",
                        content
                );

        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(reportAttachmentRepository.countByReportId(report.getId()))
                .thenReturn(0L);
        when(attachmentValidator.validate(
                eq(file),
                any(AttachmentScanContext.class)
        )).thenReturn(validated);
        when(reportAttachmentRepository.saveAndFlush(
                any(ReportAttachment.class)
        )).thenAnswer(invocation -> {
            ReportAttachment attachment = invocation.getArgument(0);
            attachment.setId(UUID.randomUUID());
            return attachment;
        });

        service().uploadAttachment(report.getId(), file);

        ArgumentCaptor<ReportAttachment> attachmentCaptor =
                ArgumentCaptor.forClass(ReportAttachment.class);
        verify(reportAttachmentRepository)
                .saveAndFlush(attachmentCaptor.capture());
        ReportAttachment attachment = attachmentCaptor.getValue();
        assertSame(report, attachment.getReport());
        assertSame(report.getReporter(), attachment.getUploadedBy());
        assertEquals("proof.png", attachment.getFileName());
        assertTrue(attachment.getStorageKey().startsWith(
                "reports/" + report.getId() + "/"
        ));
        assertTrue(attachment.getStorageKey().endsWith(".png"));
        assertEquals(1, report.getAttachments().size());
        verify(objectStorageService).store(
                eq(attachment.getStorageKey()),
                any(InputStream.class),
                eq((long) content.length),
                eq("image/png")
        );
    }

    @Test
    void failedAttachmentPersistenceRemovesStoredObject() {
        Report report = newReport(Severity.MEDIUM);
        authenticate(report.getReporter().getId(), "USER");
        byte[] content = "proof".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evidence.txt",
                "text/plain",
                content
        );
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(reportAttachmentRepository.countByReportId(report.getId()))
                .thenReturn(0L);
        when(attachmentValidator.validate(
                eq(file),
                any(AttachmentScanContext.class)
        )).thenReturn(
                new AttachmentValidator.ValidatedAttachment(
                        "evidence.txt",
                        "txt",
                        "text/plain",
                        content
                )
        );
        when(reportAttachmentRepository.saveAndFlush(
                any(ReportAttachment.class)
        )).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> service().uploadAttachment(report.getId(), file)
        );

        ArgumentCaptor<String> storageKeyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(objectStorageService).delete(storageKeyCaptor.capture());
        assertTrue(storageKeyCaptor.getValue().startsWith(
                "reports/" + report.getId() + "/"
        ));
    }

    @Test
    void unrelatedUserCannotUploadToPrivateReport() {
        Report report = newReport(Severity.LOW);
        authenticate(UUID.randomUUID(), "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().uploadAttachment(
                        report.getId(),
                        new MockMultipartFile(
                                "file",
                                "proof.txt",
                                "text/plain",
                                new byte[]{'o', 'k'}
                        )
                )
        );

        verify(attachmentValidator, never()).validate(any());
        verify(objectStorageService, never()).store(
                any(),
                any(),
                any(Long.class),
                any()
        );
    }

    @Test
    void reporterCannotChangeAttachmentsAfterTriage() {
        Report report = newReport(Severity.HIGH);
        report.setState(ReportState.VALID_CONFIRMED);
        authenticate(report.getReporter().getId(), "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().uploadAttachment(
                        report.getId(),
                        new MockMultipartFile(
                                "file",
                                "proof.txt",
                                "text/plain",
                                new byte[]{'o', 'k'}
                        )
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(attachmentValidator, never()).validate(any());
    }

    @Test
    void authorizedReporterGetsShortLivedAttachmentDownloadUrl() {
        Report report = newReport(Severity.HIGH);
        ReportAttachment attachment = ReportAttachment.builder()
                .id(UUID.randomUUID())
                .report(report)
                .fileName("proof.png")
                .storageKey("reports/" + report.getId() + "/proof.png")
                .uploadedBy(report.getReporter())
                .build();
        URI downloadUrl = URI.create(
                "https://storage.example.test/report-proof"
        );
        authenticate(report.getReporter().getId(), "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(reportAttachmentRepository.findByIdAndReportId(
                attachment.getId(),
                report.getId()
        )).thenReturn(Optional.of(attachment));
        when(objectStorageService.createDownloadUrl(
                attachment.getStorageKey(),
                Duration.ofMinutes(5)
        )).thenReturn(downloadUrl);

        URI result = service().createAttachmentDownloadUrl(
                report.getId(),
                attachment.getId()
        );

        assertEquals(downloadUrl, result);
    }

    @Test
    void reporterCanDeleteOwnAttachmentWhileReportIsEditable() {
        Report report = newReport(Severity.HIGH);
        ReportAttachment attachment = ReportAttachment.builder()
                .id(UUID.randomUUID())
                .report(report)
                .fileName("proof.png")
                .storageKey("reports/" + report.getId() + "/proof.png")
                .uploadedBy(report.getReporter())
                .build();
        authenticate(report.getReporter().getId(), "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(reportAttachmentRepository.findByIdAndReportId(
                attachment.getId(),
                report.getId()
        )).thenReturn(Optional.of(attachment));

        service().removeAttachment(report.getId(), attachment.getId());

        verify(reportAttachmentRepository).delete(attachment);
        verify(reportAttachmentRepository).flush();
        verify(objectStorageService).delete(attachment.getStorageKey());
    }

    /**
     * A reward is money and nothing else. It used to be able to carry
     * reputation points, which let one organization decide where a researcher
     * sat on a leaderboard spanning every organization -- at 100 points for a
     * critical finding, one mistyped reward was worth ten thousand of them,
     * and nothing in the platform ever subtracts reputation again.
     */
    @Test
    void aRewardPaysMoneyAndDoesNotTouchReputation() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        report.setSeverity(Severity.HIGH);
        report.getProgram().setOffersBounties(true);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRewardRepository.saveAndFlush(any(ReportReward.class)))
                .thenAnswer(invocation -> {
                    ReportReward saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

        service().recordReward(
                report.getId(),
                new RewardReportRequest(
                        new java.math.BigDecimal("1500.00"),
                        "Good find"
                )
        );

        ArgumentCaptor<ReportReward> rewardCaptor =
                ArgumentCaptor.forClass(ReportReward.class);
        verify(reportRewardRepository).saveAndFlush(rewardCaptor.capture());

        assertEquals(
                0,
                new java.math.BigDecimal("1500.00")
                        .compareTo(rewardCaptor.getValue().getAmount())
        );

        // Left unset rather than zeroed: the column still holds the points on
        // rewards recorded before this stopped being an organization's to
        // give.
        assertNull(rewardCaptor.getValue().getPoints());

        // Reputation was settled when the report was resolved. Paying again
        // here would let the organization decide a standing twice over.
        verify(userProfileRepository, never())
                .awardReputation(any(), anyInt(), anyInt());
    }

    /**
     * The two halves of what a researcher earns. The organization pays the
     * bounty out of its own budget; the platform pays the standing, priced by
     * severity, the moment the finding is resolved -- so accepting a report on
     * a paying program hands over both.
     */
    @Test
    void resolvingAReportPaysReputationPricedBySeverity() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        report.setState(ReportState.VALID_CONFIRMED);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);
        when(userProfileRepository.awardReputation(any(), anyInt(), anyInt()))
                .thenReturn(1);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.RESOLVED,
                        null,
                        null
                )
        );

        // HIGH is worth 40, and it is not critical, so criticalReports must
        // not move.
        verify(userProfileRepository).awardReputation(
                report.getReporter().getId(),
                40,
                0
        );

        // Stamped on the report itself, which is what makes the award
        // traceable and stops it happening twice.
        assertEquals(40, report.getReputationPoints());
        assertNotNull(report.getReputationAwardedAt());
    }

    @Test
    void resolvingACriticalAlsoMovesTheCriticalCounter() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.CRITICAL);
        report.setState(ReportState.VALID_CONFIRMED);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);
        when(userProfileRepository.awardReputation(any(), anyInt(), anyInt()))
                .thenReturn(1);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.CRITICAL,
                        ReportState.RESOLVED,
                        null,
                        null
                )
        );

        verify(userProfileRepository).awardReputation(
                report.getReporter().getId(),
                100,
                1
        );
    }

    /**
     * A failed retest reopens a resolved report, and the fix that follows
     * resolves it again. That is one finding being paid for, not two -- and
     * nothing on this platform subtracts reputation, so a second award could
     * not be taken back.
     */
    @Test
    void reResolvingAReportDoesNotPayItsReputationTwice() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        report.setState(ReportState.VALID_CONFIRMED);
        report.setReputationPoints(40);
        report.setReputationAwardedAt(LocalDateTime.now().minusDays(2));
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.RESOLVED,
                        null,
                        null
                )
        );

        verify(userProfileRepository, never())
                .awardReputation(any(), anyInt(), anyInt());
    }

    /**
     * Confirming a report is not fixing it. The standing is paid for a finding
     * the organization has actually resolved.
     */
    @Test
    void confirmingAReportPaysNoReputation() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.VALID_CONFIRMED,
                        null,
                        null
                )
        );

        verify(userProfileRepository, never())
                .awardReputation(any(), anyInt(), anyInt());
        assertNull(report.getReputationAwardedAt());
    }

    /**
     * A resolution is the first thing that reaches the public feed without a
     * recognition behind it. Safe to publish precisely because the finding is
     * fixed -- unlike a submission or a confirmation, which would announce a
     * live vulnerability in a named program.
     */
    @Test
    void resolvingAReportPutsItOnTheFeed() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        report.setState(ReportState.VALID_CONFIRMED);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.RESOLVED,
                        null,
                        null
                )
        );

        verify(hacktivityRecorder).recordResolved(report);
    }

    /**
     * A report that was resolved, reopened for more work, and resolved again
     * is one finding fixed, not two. The feed already has its row.
     */
    @Test
    void reResolvingAnAlreadyResolvedReportAddsNoSecondFeedRow() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        report.setState(ReportState.VALID_CONFIRMED);
        report.setResolvedAt(LocalDateTime.now().minusDays(1));
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.RESOLVED,
                        null,
                        null
                )
        );

        verify(hacktivityRecorder, never()).recordResolved(any());
    }

    /**
     * Confirming is not resolving. A confirmed report is one somebody has
     * agreed is real and has not fixed, which is the last thing to announce
     * on a feed anonymous callers read.
     */
    @Test
    void confirmingAReportPutsNothingOnTheFeed() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.VALID_CONFIRMED,
                        null,
                        null
                )
        );

        verify(hacktivityRecorder, never()).recordResolved(any());
        verify(hacktivityRecorder, never()).recordDisclosed(any());
    }

    private void stubCompanyOwnedReport(
            Report report,
            Organization organization,
            UUID ownerId
    ) {
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(userProfileRepository.findById(ownerId))
                .thenReturn(Optional.of(organization.getOwner()));
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
                // Real, over a store that starts empty for each service(): the
                // existing cases submit once and should pass through the
                // limiter rather than around a mock of it.
                new ReportRateLimiter(new InMemoryRateLimitStore()),
                hacktivityRecorder,
                // Real, over a mocked repository: the timeline is written on
                // every transition these cases exercise, and a mock of the
                // recorder would let a transition stop recording itself without
                // a single test noticing.
                new ReportActivityRecorder(reportActivityRepository),
                reportActivityRepository,
                commentRepository
        );
    }

    /**
     * A minimal valid submission. Tests that care about one field override it
     * with a {@code with...} copy rather than repeating fourteen arguments.
     */
    private CreateReportRequest withWeakness(
            CreateReportRequest request,
            UUID weaknessId,
            String suggestedWeakness
    ) {
        return new CreateReportRequest(
                request.title(),
                request.vulnerabilityInformation(),
                request.impact(),
                request.stepsToReproduce(),
                request.proofOfConcept(),
                request.remediationRecommendation(),
                request.targetEndpoint(),
                request.environment(),
                request.discoveredAt(),
                request.referenceLinks(),
                request.reportedSeverity(),
                request.cvssVector(),
                request.cvssScore(),
                weaknessId,
                suggestedWeakness,
                request.assetId()
        );
    }

    private Weakness weakness(UUID id) {
        return Weakness.builder()
                .id(id)
                .cweId("CWE-284")
                .name("Improper Access Control")
                .isActive(true)
                .build();
    }

    private CreateReportRequest reportRequest(
            Severity reportedSeverity,
            UUID assetId
    ) {
        return new CreateReportRequest(
                "Broken access control",
                "A user can read another account.",
                "Private account data is exposed.",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                reportedSeverity,
                null,
                null,
                null,
                null,
                assetId
        );
    }

    private Report newReport(Severity reportedSeverity) {
        Program program = activeApprovedProgram();
        return Report.builder()
                .id(UUID.randomUUID())
                .program(program)
                .reporter(user(UUID.randomUUID()))
                .title("Security issue")
                .vulnerabilityInformation("Steps to reproduce")
                .reportedSeverity(reportedSeverity)
                .asset(program.getAssets().getFirst())
                .state(ReportState.NEW)
                .build();
    }

    private Program activeApprovedProgram() {
        Program program = new Program();
        program.setId(UUID.randomUUID());
        program.setOrganizationId(UUID.randomUUID());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setOffersBounties(true);

        ProgramAsset asset = ProgramAsset.builder()
                .id(UUID.randomUUID())
                .program(program)
                .assetType(AssetType.URL)
                .identifier("https://app.example.test")
                .isInScope(true)
                .maxSeverity(Severity.CRITICAL)
                .build();
        program.getAssets().add(asset);
        return program;
    }

    private Organization activeOrganization(
            UUID organizationId,
            UUID ownerId
    ) {
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setOwner(user(ownerId));
        organization.setStatus(OrganizationStatus.ACTIVE);
        return organization;
    }

    private UserProfile user(UUID userId) {
        UserProfile user = new UserProfile();
        user.setId(userId);
        return user;
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
                        List.of(new SimpleGrantedAuthority(
                                "ROLE_" + role
                        ))
                )
        );
    }
}
