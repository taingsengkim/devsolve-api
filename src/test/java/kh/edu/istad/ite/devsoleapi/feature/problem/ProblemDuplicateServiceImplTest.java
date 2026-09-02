package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.ai.ClaudeUnavailableException;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.DuplicateCheckRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.DuplicateCheckResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.DuplicateSuggestion;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.DuplicateVerdict;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProblemDuplicateServiceImplTest {

    private static final UUID SOLVED_ID = UUID.randomUUID();
    private static final UUID OPEN_ID = UUID.randomUUID();

    private ProblemDuplicateCandidates candidates;
    private ProblemDuplicateReviewer reviewer;
    private ProblemDuplicateRateLimiter rateLimiter;
    private ProblemDuplicateServiceImpl service;

    @BeforeEach
    void setUp() {
        candidates = mock(ProblemDuplicateCandidates.class);
        reviewer = mock(ProblemDuplicateReviewer.class);
        rateLimiter = mock(ProblemDuplicateRateLimiter.class);

        when(reviewer.isEnabled()).thenReturn(true);
        when(candidates.find(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of(solved(), open()));

        service = new ProblemDuplicateServiceImpl(
                candidates,
                reviewer,
                rateLimiter
        );

        // The service reads the token subject to rate limit on, so the
        // reviewed path needs an authenticated context. The two-argument
        // constructor specifically: the one-argument one leaves the token
        // unauthenticated, which reads as anonymous.
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        Jwt.withTokenValue("token")
                                .header("alg", "none")
                                .subject("author-1")
                                .build(),
                        List.of()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void answersEmptyWithoutRetrievingAnythingWhenTheTitleIsTooShort() {
        DuplicateCheckResponse response = service.check(
                new DuplicateCheckRequest("npm", null, null)
        );

        assertFalse(response.aiReviewed());
        assertTrue(response.suggestions().isEmpty());
        verifyNoInteractions(candidates);
        verifyNoInteractions(reviewer);
    }

    @Test
    void servesKeywordMatchesAndSaysSoWhenTheModelIsNotConfigured() {
        when(reviewer.isEnabled()).thenReturn(false);

        DuplicateCheckResponse response = service.check(request());

        assertFalse(response.aiReviewed());
        assertEquals(2, response.suggestions().size());
        DuplicateSuggestion first = response.suggestions().getFirst();
        assertEquals(SOLVED_ID, first.id());
        assertTrue(first.solved());
        assertNull(first.verdict());
        assertNull(first.reason());
        // Nothing was spent, so nothing is metered.
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void fallsBackToKeywordMatchesWhenTheModelCannotAnswer() {
        when(reviewer.review(anyString(), anyString(), anyString(), any()))
                .thenThrow(new ClaudeUnavailableException("timed out"));

        DuplicateCheckResponse response = service.check(request());

        assertFalse(response.aiReviewed());
        assertEquals(2, response.suggestions().size());
        assertNull(response.suggestions().getFirst().verdict());
    }

    @Test
    void metersTheModelPathBeforeSpendingOnIt() {
        when(reviewer.review(anyString(), anyString(), anyString(), any()))
                .thenReturn(DuplicateJudgements.empty());

        service.check(request());

        verify(rateLimiter).check("author-1");
    }

    @Test
    void keepsOnlyVerdictsAboutCandidatesItActuallyOffered() {
        when(reviewer.review(anyString(), anyString(), anyString(), any()))
                .thenReturn(new DuplicateJudgements(List.of(
                        // An id that was never in the candidate list. A model
                        // that invents one is not trusted to have invented a
                        // matching verdict either.
                        judgement(UUID.randomUUID(), DuplicateVerdict.DUPLICATE, 95),
                        judgement(OPEN_ID, DuplicateVerdict.RELATED, 80)
                )));

        DuplicateCheckResponse response = service.check(request());

        assertTrue(response.aiReviewed());
        assertEquals(1, response.suggestions().size());
        assertEquals(OPEN_ID, response.suggestions().getFirst().id());
    }

    @Test
    void dropsVerdictsTheModelWasNotSureOf() {
        when(reviewer.review(anyString(), anyString(), anyString(), any()))
                .thenReturn(new DuplicateJudgements(List.of(
                        judgement(OPEN_ID, DuplicateVerdict.DUPLICATE, 20)
                )));

        DuplicateCheckResponse response = service.check(request());

        assertTrue(response.aiReviewed());
        assertTrue(response.suggestions().isEmpty());
    }

    @Test
    void takesTheTitleAndStatusFromTheDatabaseRatherThanFromTheModel() {
        when(reviewer.review(anyString(), anyString(), anyString(), any()))
                .thenReturn(new DuplicateJudgements(List.of(
                        judgement(SOLVED_ID, DuplicateVerdict.DUPLICATE, 90)
                )));

        DuplicateSuggestion suggestion =
                service.check(request()).suggestions().getFirst();

        assertEquals("PKCE verifier lost on the OAuth callback", suggestion.title());
        assertEquals(ProblemStatus.RESOLVED, suggestion.status());
        assertTrue(suggestion.solved());
        assertEquals(3L, suggestion.solutionCount());
        assertEquals("Same PKCE state loss, and it is answered.", suggestion.reason());
    }

    @Test
    void putsDuplicatesBeforeMerelyRelatedProblemsWhateverOrderTheModelUsed() {
        when(reviewer.review(anyString(), anyString(), anyString(), any()))
                .thenReturn(new DuplicateJudgements(List.of(
                        judgement(OPEN_ID, DuplicateVerdict.RELATED, 99),
                        judgement(SOLVED_ID, DuplicateVerdict.DUPLICATE, 55)
                )));

        List<DuplicateSuggestion> suggestions =
                service.check(request()).suggestions();

        assertEquals(SOLVED_ID, suggestions.get(0).id());
        assertEquals(OPEN_ID, suggestions.get(1).id());
    }

    @Test
    void doesNotCallTheModelWhenNothingWasRetrieved() {
        when(candidates.find(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        DuplicateCheckResponse response = service.check(request());

        assertFalse(response.aiReviewed());
        assertTrue(response.suggestions().isEmpty());
        verify(reviewer, never())
                .review(anyString(), anyString(), anyString(), any());
        verifyNoInteractions(rateLimiter);
    }

    private static DuplicateCheckRequest request() {
        return new DuplicateCheckRequest(
                "OAuth callback loses the PKCE verifier",
                "Authorization code flow with PKCE fails intermittently.",
                null
        );
    }

    private static DuplicateJudgements.DuplicateJudgement judgement(
            UUID id,
            DuplicateVerdict verdict,
            int confidence
    ) {
        return new DuplicateJudgements.DuplicateJudgement(
                id.toString(),
                verdict,
                confidence,
                "Same PKCE state loss, and it is answered."
        );
    }

    private static DuplicateCandidate solved() {
        return new DuplicateCandidate(
                SOLVED_ID,
                "PKCE verifier lost on the OAuth callback",
                "The verifier is gone by the time the callback runs.",
                null,
                ProblemStatus.RESOLVED,
                3L,
                120L
        );
    }

    private static DuplicateCandidate open() {
        return new DuplicateCandidate(
                OPEN_ID,
                "Session cookie dropped on redirect",
                "SameSite=Lax drops the cookie on the cross-site hop.",
                "missing session",
                ProblemStatus.PUBLISHED,
                0L,
                40L
        );
    }
}
