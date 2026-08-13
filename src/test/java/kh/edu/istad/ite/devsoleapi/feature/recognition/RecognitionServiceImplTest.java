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
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(userProfileRepository.applyRecognition(any(), anyInt(), anyInt()))
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
    void awardMovesReputationBySeverityAndRecordsTheFinding() {

        recognitionService.awardRecognition(request(), triagerId);

        // HIGH is worth 40, and it is not critical, so criticalReports must
        // not move.
        verify(userProfileRepository)
                .applyRecognition(researcherId, 40, 0);

        ArgumentCaptor<Recognition> saved =
                ArgumentCaptor.forClass(Recognition.class);
        verify(recognitionRepository).saveAndFlush(saved.capture());
        assertEquals(Severity.HIGH, saved.getValue().getSeverity());
        assertEquals(triagerId, saved.getValue().getAwardedBy());

        verify(hacktivityRepository).save(any(Hacktivity.class));
    }

    @Test
    void criticalFindingAlsoIncrementsTheCriticalCounter() {

        report.setSeverity(Severity.CRITICAL);

        recognitionService.awardRecognition(request(), triagerId);

        verify(userProfileRepository)
                .applyRecognition(researcherId, 100, 1);
    }

    @Test
    void informationalFindingIsRecordedButScoresNothing() {

        report.setSeverity(Severity.NONE);

        recognitionService.awardRecognition(request(), triagerId);

        verify(userProfileRepository)
                .applyRecognition(researcherId, 0, 0);
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
                .applyRecognition(any(), anyInt(), anyInt());
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

    @Test
    void reportFromAnotherProgramIsRejected() {

        Program otherProgram = Program.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .build();
        report.setProgram(otherProgram);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> recognitionService.awardRecognition(request(), triagerId)
        );

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verify(userProfileRepository, never())
                .applyRecognition(any(), anyInt(), anyInt());
    }

    @Test
    void recognitionCannotBeAttributedToSomebodyElse() {

        UserProfile bystander = new UserProfile();
        bystander.setId(UUID.randomUUID());
        report.setReporter(bystander);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> recognitionService.awardRecognition(request(), triagerId)
        );

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verify(userProfileRepository, never())
                .applyRecognition(any(), anyInt(), anyInt());
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
                .applyRecognition(any(), anyInt(), anyInt());
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
                .applyRecognition(any(), anyInt(), anyInt());
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
                .applyRecognition(any(), anyInt(), anyInt());
    }

    @Test
    void profileVanishingMidAwardRollsTheAwardBack() {

        when(userProfileRepository.applyRecognition(any(), anyInt(), anyInt()))
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
}
