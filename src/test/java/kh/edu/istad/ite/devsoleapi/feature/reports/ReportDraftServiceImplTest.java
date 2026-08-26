package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SaveReportDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportDraft;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportDraftServiceImplTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();

    @Mock
    private ReportDraftRepository reportDraftRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ReportService reportService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * The whole point of promoting through {@code reportService.create} rather
     * than writing a report directly: a draft is exempt from the submission
     * rules only while it stays a draft.
     */
    @Test
    void anIncompleteDraftIsRefusedAndNoReportIsFiled() {
        UUID reporterId = UUID.randomUUID();
        authenticate("USER", reporterId);

        ReportDraft draft = ReportDraft.builder()
                .id(UUID.randomUUID())
                .stepsToReproduce("Open the profile page and paste the payload.")
                .build();
        when(reportDraftRepository.findByIdAndReporterId(
                draft.getId(),
                reporterId
        )).thenReturn(Optional.of(draft));

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> service().submit(draft.getId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        String reason = String.valueOf(thrown.getReason());
        assertTrue(reason.contains("Title is required"), reason);
        assertTrue(
                reason.contains("Vulnerability information is required"),
                reason
        );
        assertTrue(reason.contains("Reported severity is required"), reason);

        verify(reportService, never()).create(any(), any());
        verify(reportDraftRepository, never()).delete(any());
    }

    @Test
    void aCompleteDraftIsFiledWithItsFieldsAndThenDiscarded() {
        UUID reporterId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID weaknessId = UUID.randomUUID();
        authenticate("USER", reporterId);

        Program program = mock(Program.class);
        when(program.getId()).thenReturn(programId);

        ReportDraft draft = ReportDraft.builder()
                .id(UUID.randomUUID())
                .program(program)
                .title("Stored XSS in the profile bio")
                .vulnerabilityInformation("The bio renders unescaped HTML.")
                .reportedSeverity(Severity.HIGH)
                .weaknessId(weaknessId)
                .referenceLinks(List.of("https://owasp.org/xss"))
                .build();
        when(reportDraftRepository.findByIdAndReporterId(
                draft.getId(),
                reporterId
        )).thenReturn(Optional.of(draft));
        when(reportService.create(eq(programId), any(CreateReportRequest.class)))
                .thenReturn(null);

        service().submit(draft.getId());

        ArgumentCaptor<CreateReportRequest> filed =
                ArgumentCaptor.forClass(CreateReportRequest.class);
        verify(reportService).create(eq(programId), filed.capture());
        assertEquals("Stored XSS in the profile bio", filed.getValue().title());
        assertEquals(Severity.HIGH, filed.getValue().reportedSeverity());
        assertEquals(weaknessId, filed.getValue().weaknessId());
        assertEquals(
                List.of("https://owasp.org/xss"),
                filed.getValue().referenceLinks()
        );

        verify(reportDraftRepository).delete(draft);
    }

    /**
     * Ownership is part of the query, so someone else's draft is reported as
     * missing rather than forbidden — a reporter cannot use the difference to
     * learn that a draft exists.
     */
    @Test
    void aDraftBelongingToAnotherReporterIsNotFound() {
        UUID reporterId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        authenticate("USER", reporterId);
        when(reportDraftRepository.findByIdAndReporterId(draftId, reporterId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().findById(draftId)
        );
    }

    @Test
    void draftsPerProgramAreCapped() {
        UUID reporterId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        authenticate("USER", reporterId);

        when(userProfileRepository.findById(reporterId))
                .thenReturn(Optional.of(mock(UserProfile.class)));
        when(programRepository.findById(programId))
                .thenReturn(Optional.of(mock(Program.class)));
        when(reportDraftRepository.countByReporterIdAndProgramId(
                reporterId,
                programId
        )).thenReturn(20L);

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> service().create(programId, emptyRequest())
        );

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        verify(reportDraftRepository, never()).save(any());
    }

    /**
     * Autosave has to survive a form nobody has typed into yet, so an entirely
     * empty save is a valid one.
     */
    @Test
    void anEmptyDraftIsAcceptedBecauseAutosaveFiresBeforeAnythingIsTyped() {
        Validator validator = VALIDATOR_FACTORY.getValidator();
        assertTrue(validator.validate(emptyRequest()).isEmpty());
    }

    private SaveReportDraftRequest emptyRequest() {
        return new SaveReportDraftRequest(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null
        );
    }

    private ReportDraftServiceImpl service() {
        return new ReportDraftServiceImpl(
                reportDraftRepository,
                programRepository,
                userProfileRepository,
                reportService,
                VALIDATOR_FACTORY.getValidator()
        );
    }

    private void authenticate(String role, UUID userId) {
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
