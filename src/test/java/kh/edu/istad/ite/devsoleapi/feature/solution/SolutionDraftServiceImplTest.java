package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SaveSolutionDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.VerificationStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ApproachType;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolutionDraftServiceImplTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();

    @Mock
    private SolutionDraftRepository solutionDraftRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private SolutionService solutionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * The minimums are where a draft and a solution genuinely differ. A summary
     * of three words and a two-line body are a perfectly good draft and not a
     * postable answer.
     */
    @Test
    void aDraftBelowTheSolutionMinimumsIsRefusedAndNothingIsPosted() {
        UUID authorId = UUID.randomUUID();
        authenticate("USER", authorId);

        SolutionDraft draft = SolutionDraft.builder()
                .id(UUID.randomUUID())
                .problemId(UUID.randomUUID())
                .summary("Too short")
                .bodyMarkdown("Not enough.")
                .build();
        when(solutionDraftRepository.findByIdAndAuthor_Id(
                draft.getId(),
                authorId
        )).thenReturn(Optional.of(draft));

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> service().submit(draft.getId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        String reason = String.valueOf(thrown.getReason());
        assertTrue(reason.contains("Summary must be between"), reason);
        assertTrue(reason.contains("Solution body must be between"), reason);
        assertTrue(reason.contains("Approach type is required"), reason);

        verify(solutionService, never()).createSolution(any(), any());
        verify(solutionDraftRepository, never()).delete(any());
    }

    @Test
    void aCompleteDraftIsPostedAgainstItsProblemAndThenDiscarded() {
        UUID authorId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        authenticate("USER", authorId);

        SolutionDraft draft = SolutionDraft.builder()
                .id(UUID.randomUUID())
                .problemId(problemId)
                .summary("Register the listener after commit instead")
                .bodyMarkdown(
                        "A transactional event listener never fires when the "
                                + "publisher has no transaction, so the fix is "
                                + "to open one."
                )
                .approachType(ApproachType.WORKAROUND)
                .verificationSteps(List.of(
                        new VerificationStepRequest(
                                "Submit a report",
                                "The timeline shows one entry"
                        )
                ))
                .build();
        when(solutionDraftRepository.findByIdAndAuthor_Id(
                draft.getId(),
                authorId
        )).thenReturn(Optional.of(draft));

        service().submit(draft.getId());

        ArgumentCaptor<SolutionRequest> posted =
                ArgumentCaptor.forClass(SolutionRequest.class);
        verify(solutionService).createSolution(eq(problemId), posted.capture());
        assertEquals(
                "Register the listener after commit instead",
                posted.getValue().summary()
        );
        assertEquals(ApproachType.WORKAROUND, posted.getValue().approachType());
        assertEquals(1, posted.getValue().verificationSteps().size());

        verify(solutionDraftRepository).delete(draft);
    }

    /**
     * A whole-document replace, not a patch. A field the client stops sending
     * is cleared, so an autosave that drops a value means the author deleted
     * it.
     */
    @Test
    void savingReplacesTheWholeDraftRatherThanPatchingIt() {
        UUID authorId = UUID.randomUUID();
        authenticate("USER", authorId);

        SolutionDraft draft = SolutionDraft.builder()
                .id(UUID.randomUUID())
                .problemId(UUID.randomUUID())
                // Only because saving maps the draft back to a response, which
                // reads the author's id.
                .author(mock(UserProfile.class))
                .summary("First pass")
                .tradeoffs("Slower on cold start")
                .approachType(ApproachType.WORKAROUND)
                .build();
        when(solutionDraftRepository.findByIdAndAuthor_Id(
                draft.getId(),
                authorId
        )).thenReturn(Optional.of(draft));
        when(solutionDraftRepository.save(draft)).thenReturn(draft);

        service().save(
                draft.getId(),
                new SaveSolutionDraftRequest(
                        "Second pass",
                        null, null, null, null, null, null
                )
        );

        assertEquals("Second pass", draft.getSummary());
        assertNull(draft.getTradeoffs());
        assertNull(draft.getApproachType());
    }

    @Test
    void aDraftBelongingToAnotherAuthorIsNotFound() {
        UUID authorId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        authenticate("USER", authorId);
        when(solutionDraftRepository.findByIdAndAuthor_Id(draftId, authorId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().findById(draftId)
        );
    }

    @Test
    void draftsAreCapped() {
        UUID authorId = UUID.randomUUID();
        authenticate("USER", authorId);
        when(solutionDraftRepository.countByAuthor_Id(authorId))
                .thenReturn(20L);

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> service().create(UUID.randomUUID(), emptyRequest())
        );

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        verify(solutionDraftRepository, never()).save(any());
    }

    /**
     * Autosave fires before anything is typed, and a half-typed verification
     * step is exactly what it exists to keep — so neither the minimums nor the
     * nested {@code @NotBlank} rules are cascaded into a draft.
     */
    @Test
    void anEmptyDraftAndAHalfTypedStepAreBothAccepted() {
        Validator validator = VALIDATOR_FACTORY.getValidator();

        assertTrue(validator.validate(emptyRequest()).isEmpty());
        assertTrue(validator.validate(new SaveSolutionDraftRequest(
                "x",
                "y",
                null,
                List.of(new VerificationStepRequest("Run it", "")),
                null,
                null,
                null
        )).isEmpty());
    }

    private SaveSolutionDraftRequest emptyRequest() {
        return new SaveSolutionDraftRequest(
                null, null, null, null, null, null, null
        );
    }

    private SolutionDraftServiceImpl service() {
        return new SolutionDraftServiceImpl(
                solutionDraftRepository,
                userProfileRepository,
                solutionService,
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
