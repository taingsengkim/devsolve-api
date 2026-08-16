package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.DisputeMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ResolveDisputeRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeServiceImplTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorization;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorCanRuleOnASeverityNeitherSideClaimed() {
        UUID adminId = UUID.randomUUID();
        Dispute dispute = openDispute(Severity.CRITICAL, Severity.LOW);
        authenticate(adminId, "ADMIN");
        stubAdmin(adminId, dispute);

        service().resolve(
                dispute.getId(),
                new ResolveDisputeRequest(
                        DisputeStatus.RESOLVED,
                        Severity.MEDIUM,
                        "Exploitable, but only for an authenticated user."
                )
        );

        assertEquals(DisputeStatus.RESOLVED, dispute.getStatus());
        assertEquals(Severity.MEDIUM, dispute.getResolvedSeverity());
        assertEquals(adminId, dispute.getResolvedBy().getId());
        assertNotNull(dispute.getResolvedAt());
        // The report unfreezes on the ruled severity, which is what makes it
        // payable again.
        assertEquals(Severity.MEDIUM, dispute.getReport().getSeverity());
        verify(reportRepository).saveAndFlush(dispute.getReport());
    }

    @Test
    void dismissingADisputeLetsTheTriageSeverityStand() {
        UUID adminId = UUID.randomUUID();
        Dispute dispute = openDispute(Severity.CRITICAL, Severity.LOW);
        authenticate(adminId, "ADMIN");
        stubAdmin(adminId, dispute);

        service().resolve(
                dispute.getId(),
                new ResolveDisputeRequest(
                        DisputeStatus.DISMISSED,
                        null,
                        "The reported impact needs a privilege the reporter "
                                + "did not have."
                )
        );

        assertEquals(DisputeStatus.DISMISSED, dispute.getStatus());
        assertEquals(Severity.LOW, dispute.getResolvedSeverity());
        assertEquals(Severity.LOW, dispute.getReport().getSeverity());
    }

    @Test
    void bothSidesAreToldHowTheDisputeWasDecided() {
        UUID adminId = UUID.randomUUID();
        UUID triagerId = UUID.randomUUID();
        Dispute dispute = openDispute(Severity.HIGH, Severity.LOW);
        authenticate(adminId, "ADMIN");
        stubAdmin(adminId, dispute);
        when(organizationAuthorization.findUserIdsWithPermission(
                dispute.getReport().getProgram().getOrganizationId(),
                OrganizationPermission.TRIAGE_REPORTS
        )).thenReturn(Set.of(triagerId));

        service().resolve(
                dispute.getId(),
                new ResolveDisputeRequest(
                        DisputeStatus.RESOLVED,
                        Severity.MEDIUM,
                        "Split the difference."
                )
        );

        ArgumentCaptor<NotificationEvent> captor =
                ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        NotificationEvent event = captor.getValue();
        assertEquals(NotificationType.DISPUTE, event.type());
        assertTrue(event.recipientIds().contains(
                dispute.getReport().getReporter().getId()
        ));
        assertTrue(event.recipientIds().contains(triagerId));
    }

    @Test
    void aRulingCannotBeMadeWithoutSayingWhy() {
        UUID adminId = UUID.randomUUID();
        Dispute dispute = openDispute(Severity.HIGH, Severity.LOW);
        authenticate(adminId, "ADMIN");
        when(disputeRepository.findById(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().resolve(
                        dispute.getId(),
                        new ResolveDisputeRequest(
                                DisputeStatus.RESOLVED,
                                Severity.MEDIUM,
                                "   "
                        )
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertNull(dispute.getReport().getSeverity());
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    void resolvingWithoutAFinalSeverityIsRejected() {
        UUID adminId = UUID.randomUUID();
        Dispute dispute = openDispute(Severity.HIGH, Severity.LOW);
        authenticate(adminId, "ADMIN");
        when(disputeRepository.findById(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().resolve(
                        dispute.getId(),
                        new ResolveDisputeRequest(
                                DisputeStatus.RESOLVED,
                                null,
                                "Agreed with the reporter."
                        )
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void aSettledDisputeCannotBeDecidedTwice() {
        UUID adminId = UUID.randomUUID();
        Dispute dispute = openDispute(Severity.HIGH, Severity.LOW);
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolvedSeverity(Severity.MEDIUM);
        authenticate(adminId, "ADMIN");
        when(disputeRepository.findById(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().resolve(
                        dispute.getId(),
                        new ResolveDisputeRequest(
                                DisputeStatus.RESOLVED,
                                Severity.CRITICAL,
                                "Changed my mind."
                        )
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(Severity.MEDIUM, dispute.getResolvedSeverity());
    }

    @Test
    void nonAdministratorsCannotSettleADispute() {
        Dispute dispute = openDispute(Severity.HIGH, Severity.LOW);
        authenticate(UUID.randomUUID(), "COMPANY");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().resolve(
                        dispute.getId(),
                        new ResolveDisputeRequest(
                                DisputeStatus.RESOLVED,
                                Severity.LOW,
                                "Our assessment is right."
                        )
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(disputeRepository, never()).findById(any());
    }

    private void stubAdmin(UUID adminId, Dispute dispute) {
        when(disputeRepository.findById(dispute.getId()))
                .thenReturn(Optional.of(dispute));
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(user(adminId)));
    }

    private DisputeServiceImpl service() {
        return new DisputeServiceImpl(
                disputeRepository,
                reportRepository,
                userProfileRepository,
                organizationAuthorization,
                new DisputeMapper(),
                eventPublisher
        );
    }

    private Dispute openDispute(
            Severity reportedSeverity,
            Severity triageSeverity
    ) {
        Program program = new Program();
        program.setId(UUID.randomUUID());
        program.setOrganizationId(UUID.randomUUID());
        program.setName("Example program");
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);

        Report report = Report.builder()
                .id(UUID.randomUUID())
                .program(program)
                .reporter(user(UUID.randomUUID()))
                .title("Security issue")
                .vulnerabilityInformation("Steps to reproduce")
                .reportedSeverity(reportedSeverity)
                .triageSeverity(triageSeverity)
                .state(ReportState.VALID_CONFIRMED)
                .build();

        return Dispute.builder()
                .id(UUID.randomUUID())
                .report(report)
                .raisedBy(report.getReporter())
                .reason("Severities differ")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
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
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }
}
