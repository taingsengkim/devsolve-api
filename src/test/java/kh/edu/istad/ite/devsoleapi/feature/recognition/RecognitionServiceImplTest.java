package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.Hacktivity;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMember;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.ThanksResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecognitionServiceImplTest {

    @Mock
    private RecognitionRepository recognitionRepository;

    @Mock
    private RecognitionMapper recognitionMapper;

    @Mock
    private HacktivityRepository hacktivityRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RecognitionServiceImpl recognitionService;

    private final UUID researcherId = UUID.randomUUID();
    private final UUID triagerId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID programId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();

    private UserProfile researcher;
    private Organization organization;
    private Program program;
    private Report report;

    @BeforeEach
    void setUp() {
        researcher = new UserProfile();
        researcher.setId(researcherId);

        organization = new Organization();
        organization.setId(organizationId);

        program = Program.builder()
                .id(programId)
                .organizationId(organizationId)
                .build();

        report = Report.builder()
                .id(reportId)
                .program(program)
                .reporter(researcher)
                .state(ReportState.RESOLVED)
                .severity(Severity.HIGH)
                .build();

        when(userProfileRepository.findById(researcherId))
                .thenReturn(Optional.of(researcher));
        when(reportRepository.findById(reportId))
                .thenReturn(Optional.of(report));
        when(programRepository.findById(programId))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organizationId))
                .thenReturn(Optional.of(organization));
        when(organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, triagerId))
                .thenReturn(Optional.of(membership(MembershipStatus.ACTIVE)));
        when(recognitionRepository.existsByReportId(reportId))
                .thenReturn(false);
        when(recognitionRepository.saveAndFlush(any(Recognition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userProfileRepository.incrementRecognitionCount(any()))
                .thenReturn(1);
    }

    private OrganizationMember membership(MembershipStatus status) {
        OrganizationMember member = new OrganizationMember();
        member.setOrganization(organization);
        member.setUser(researcher);
        member.setStatus(status);
        return member;
    }

    private CreateRecognitionRequest request() {
        return new CreateRecognitionRequest(
                researcherId,
                programId,
                reportId,
                "SQL injection in the billing export",
                "Reported and fixed within a day."
        );
    }

    @Test
    void awardCountsTheRecognitionAndRecordsTheFinding() {

        recognitionService.awardRecognition(request(), triagerId);

        verify(userProfileRepository)
                .incrementRecognitionCount(researcherId);

        ArgumentCaptor<Recognition> saved =
                ArgumentCaptor.forClass(Recognition.class);
        verify(recognitionRepository).saveAndFlush(saved.capture());
        assertEquals(Severity.HIGH, saved.getValue().getSeverity());
        assertEquals(triagerId, saved.getValue().getAwardedBy());

        verify(hacktivityRepository).save(any(Hacktivity.class));
    }

    /**
     * The researcher was paid for this finding when the report was resolved.
     * Paying again for the organization's public thank-you would be a second
     * award for one bug -- and an irreversible one, because nothing on this
     * platform subtracts reputation.
     */
    @Test
    void awardDoesNotPayReputationASecondTime() {

        report.setSeverity(Severity.CRITICAL);

        recognitionService.awardRecognition(request(), triagerId);

        verify(userProfileRepository, never())
                .awardReputation(any(), anyInt(), anyInt());
    }

    @Test
    void informationalFindingIsStillRecognised() {

        report.setSeverity(Severity.NONE);

        recognitionService.awardRecognition(request(), triagerId);

        verify(userProfileRepository)
                .incrementRecognitionCount(researcherId);
        verify(hacktivityRepository).save(any(Hacktivity.class));
    }

    @Test
    void outsiderCannotAwardRecognitionOnAnotherOrganizationsProgram() {

        when(organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, triagerId))
                .thenReturn(Optional.empty());

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> recognitionService.awardRecognition(request(), triagerId)
        );

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verify(recognitionRepository, never())
                .saveAndFlush(any(Recognition.class));
        verify(userProfileRepository, never())
                .incrementRecognitionCount(any());
    }

    @Test
    void suspendedMemberCannotAwardRecognition() {

        when(organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, triagerId))
                .thenReturn(Optional.of(
                        membership(MembershipStatus.SUSPENDED)
                ));

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> recognitionService.awardRecognition(request(), triagerId)
        );

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
    }

    /**
     * A body that names the wrong program does not sink the award. The program
     * is a fact of the report, so it is read off the report — a client that
     * restates it wrongly used to get "Program not found" for a program the
     * server could see on the report in front of it.
     */
    @Test
    void aWrongProgramInTheBodyIsIgnoredInFavourOfTheReports() {

        UUID stale = UUID.randomUUID();
        when(programRepository.findById(stale)).thenReturn(Optional.empty());

        recognitionService.awardRecognition(
                new CreateRecognitionRequest(
                        researcherId,
                        stale,
                        reportId,
                        "SQL injection in the billing export",
                        null
                ),
                triagerId
        );

        ArgumentCaptor<Recognition> saved =
                ArgumentCaptor.forClass(Recognition.class);
        verify(recognitionRepository).saveAndFlush(saved.capture());
        assertEquals(programId, saved.getValue().getProgramId());
    }

    /**
     * The credit goes to whoever reported the finding, whoever the body names.
     * Deriving it is what the mismatch check used to protect: a triager cannot
     * attribute somebody else's finding to a friend on a public feed.
     */
    @Test
    void recognitionCannotBeAttributedToSomebodyElse() {

        UserProfile reporter = new UserProfile();
        reporter.setId(UUID.randomUUID());
        report.setReporter(reporter);

        recognitionService.awardRecognition(
                new CreateRecognitionRequest(
                        researcherId,
                        programId,
                        reportId,
                        "SQL injection in the billing export",
                        null
                ),
                triagerId
        );

        ArgumentCaptor<Recognition> saved =
                ArgumentCaptor.forClass(Recognition.class);
        verify(recognitionRepository).saveAndFlush(saved.capture());
        assertEquals(reporter.getId(), saved.getValue().getUserId());

        verify(userProfileRepository)
                .incrementRecognitionCount(reporter.getId());
        verify(userProfileRepository, never())
                .incrementRecognitionCount(researcherId);
    }

    /**
     * The report is the only id the caller has to get right.
     */
    @Test
    void reportIdAloneIsEnoughToThankAResearcher() {

        recognitionService.awardRecognition(
                new CreateRecognitionRequest(
                        null,
                        null,
                        reportId,
                        "SQL injection in the billing export",
                        null
                ),
                triagerId
        );

        ArgumentCaptor<Recognition> saved =
                ArgumentCaptor.forClass(Recognition.class);
        verify(recognitionRepository).saveAndFlush(saved.capture());
        assertEquals(researcherId, saved.getValue().getUserId());
        assertEquals(programId, saved.getValue().getProgramId());
        assertEquals(reportId, saved.getValue().getReportId());
    }

    @Test
    void unresolvedReportCannotBeRecognised() {

        report.setState(ReportState.TRIAGING);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> recognitionService.awardRecognition(request(), triagerId)
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    }

    @Test
    void unsettledSeverityCannotBePriced() {

        // The reconcile_report_severity trigger leaves severity NULL while the
        // reported and triage severities disagree.
        report.setSeverity(null);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> recognitionService.awardRecognition(request(), triagerId)
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(userProfileRepository, never())
                .incrementRecognitionCount(any());
    }

    @Test
    void theSameReportCannotBeRecognisedTwice() {

        when(recognitionRepository.existsByReportId(reportId))
                .thenReturn(true);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> recognitionService.awardRecognition(request(), triagerId)
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(userProfileRepository, never())
                .incrementRecognitionCount(any());
    }

    @Test
    void concurrentDuplicateSurfacesAsConflictNotServerError() {

        // Two triagers award at once: both pass existsByReportId, and the
        // unique constraint is what stops the second.
        when(recognitionRepository.saveAndFlush(any(Recognition.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "uq_recognitions_report"
                ));

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> recognitionService.awardRecognition(request(), triagerId)
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(userProfileRepository, never())
                .incrementRecognitionCount(any());
    }

    @Test
    void profileVanishingMidAwardRollsTheAwardBack() {

        when(userProfileRepository.incrementRecognitionCount(any()))
                .thenReturn(0);

        assertThrows(
                ResourceNotFoundException.class,
                () -> recognitionService.awardRecognition(request(), triagerId)
        );
    }

    @Test
    void listingRecognitionsForAnUnknownUserIsNotFound() {

        UUID unknown = UUID.randomUUID();
        when(userProfileRepository.existsById(unknown)).thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> recognitionService.getRecognitionsByUser(
                        unknown,
                        org.springframework.data.domain.PageRequest.of(0, 10)
                )
        );

        verify(recognitionRepository, never())
                .findAllByUserId(eq(unknown), any());
    }

    // ----------------------------------------------- hall of thanks

    private RecognitionRepository.ThanksTally thanks(
            UUID userId,
            Severity severity,
            long count,
            LocalDateTime lastAwardedAt
    ) {
        return new RecognitionRepository.ThanksTally() {

            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public Severity getSeverity() {
                return severity;
            }

            @Override
            public long getThanks() {
                return count;
            }

            @Override
            public LocalDateTime getLastAwardedAt() {
                return lastAwardedAt;
            }
        };
    }

    private UserProfile activeProfile(UUID id, String username) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setUsername(username);
        profile.setFullName("Researcher " + username);
        profile.setStatus(UserStatus.ACTIVE);
        return profile;
    }

    @Test
    void theThanksBoardRanksByHowOftenSomebodyWasThanked() {

        UUID often = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID once = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        LocalDateTime when = LocalDateTime.now();

        when(programRepository.existsById(programId)).thenReturn(true);
        when(recognitionRepository.tallyThanksByProgram(programId))
                .thenReturn(List.of(
                        thanks(once, Severity.CRITICAL, 1, when),
                        thanks(often, Severity.LOW, 3, when)
                ));
        when(userProfileRepository.findAllById(any()))
                .thenReturn(List.of(
                        activeProfile(often, "often"),
                        activeProfile(once, "once")
                ));

        List<ThanksResponse> board = recognitionService
                .getProgramThanks(programId, PageRequest.of(0, 10))
                .getContent();

        // Three thanks beat one, whatever the severities. Nobody can farm
        // this: a researcher cannot thank themselves.
        assertEquals(List.of(often, once), board.stream()
                .map(ThanksResponse::id)
                .toList());
        assertEquals(1, board.getFirst().rank());
        assertEquals(3, board.getFirst().recognitions());
        assertEquals(
                Map.of(Severity.LOW, 3L),
                board.getFirst().bySeverity()
        );
    }

    @Test
    void anEqualCountIsBrokenByTheHarderFinding() {

        UUID deep = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
        UUID shallow = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
        LocalDateTime when = LocalDateTime.now();

        when(programRepository.existsById(programId)).thenReturn(true);
        when(recognitionRepository.tallyThanksByProgram(programId))
                .thenReturn(List.of(
                        thanks(shallow, Severity.LOW, 2, when),
                        thanks(deep, Severity.CRITICAL, 2, when)
                ));
        when(userProfileRepository.findAllById(any()))
                .thenReturn(List.of(
                        activeProfile(deep, "deep"),
                        activeProfile(shallow, "shallow")
                ));

        List<ThanksResponse> board = recognitionService
                .getProgramThanks(programId, PageRequest.of(0, 10))
                .getContent();

        assertEquals(List.of(deep, shallow), board.stream()
                .map(ThanksResponse::id)
                .toList());
    }

    /**
     * A suspended account is dropped before the ranks are numbered, so its
     * absence does not leave a hole where rank 2 should be.
     */
    @Test
    void suspendedAccountsDoNotAppearAndLeaveNoGapInTheRanks() {

        UUID active = UUID.fromString("00000000-0000-0000-0000-0000000000e5");
        UUID suspended =
                UUID.fromString("00000000-0000-0000-0000-0000000000f6");
        LocalDateTime when = LocalDateTime.now();

        UserProfile removed = activeProfile(suspended, "gone");
        removed.setStatus(UserStatus.SUSPENDED);

        when(programRepository.existsById(programId)).thenReturn(true);
        when(recognitionRepository.tallyThanksByProgram(programId))
                .thenReturn(List.of(
                        thanks(suspended, Severity.CRITICAL, 9, when),
                        thanks(active, Severity.HIGH, 1, when)
                ));
        when(userProfileRepository.findAllById(any()))
                .thenReturn(List.of(activeProfile(active, "here"), removed));

        Page<ThanksResponse> board = recognitionService
                .getProgramThanks(programId, PageRequest.of(0, 10));

        assertEquals(1, board.getTotalElements());
        assertEquals(active, board.getContent().getFirst().id());
        assertEquals(1, board.getContent().getFirst().rank());
    }

    @Test
    void aProgramNobodyHasBeenThankedOnIsAnEmptyBoard() {

        when(programRepository.existsById(programId)).thenReturn(true);
        when(recognitionRepository.tallyThanksByProgram(programId))
                .thenReturn(List.of());

        Page<ThanksResponse> board = recognitionService
                .getProgramThanks(programId, PageRequest.of(0, 10));

        assertTrue(board.getContent().isEmpty());
        assertEquals(0, board.getTotalElements());
    }

    /**
     * An empty board and a mistyped id are different answers, and rendering
     * "nobody has been thanked yet" for the second is a lie.
     */
    @Test
    void thanksForAnUnknownProgramIsNotFound() {

        UUID unknown = UUID.randomUUID();
        when(programRepository.existsById(unknown)).thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> recognitionService.getProgramThanks(
                        unknown,
                        PageRequest.of(0, 10)
                )
        );

        verify(recognitionRepository, never())
                .tallyThanksByProgram(unknown);
    }

    @Test
    void thanksForAnUnknownOrganizationIsNotFound() {

        UUID unknown = UUID.randomUUID();
        when(organizationRepository.existsById(unknown)).thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> recognitionService.getOrganizationThanks(
                        unknown,
                        PageRequest.of(0, 10)
                )
        );

        verify(recognitionRepository, never())
                .tallyThanksByOrganization(unknown);
    }

    /**
     * One researcher thanked at several severities is one row, not one per
     * severity — the tally comes back grouped both ways.
     */
    @Test
    void severitiesForOneResearcherFoldIntoASingleRow() {

        UUID researcher =
                UUID.fromString("00000000-0000-0000-0000-000000000a17");
        LocalDateTime older = LocalDateTime.now().minusDays(3);
        LocalDateTime newer = LocalDateTime.now();

        when(organizationRepository.existsById(organizationId))
                .thenReturn(true);
        when(recognitionRepository.tallyThanksByOrganization(organizationId))
                .thenReturn(List.of(
                        thanks(researcher, Severity.HIGH, 2, older),
                        thanks(researcher, Severity.CRITICAL, 1, newer)
                ));
        when(userProfileRepository.findAllById(any()))
                .thenReturn(List.of(activeProfile(researcher, "one")));

        ThanksResponse row = recognitionService
                .getOrganizationThanks(organizationId, PageRequest.of(0, 10))
                .getContent()
                .getFirst();

        assertEquals(3, row.recognitions());
        assertEquals(
                Map.of(Severity.HIGH, 2L, Severity.CRITICAL, 1L),
                row.bySeverity()
        );
        // The most recent of the two, not whichever row was read last.
        assertEquals(newer, row.lastThankedAt());
    }
}

