package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.ai.AiReviewClient;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProblemDuplicateReviewerTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();

    private AiReviewClient ai;
    private ProblemDuplicateReviewer reviewer;

    @BeforeEach
    void setUp() {
        ai = mock(AiReviewClient.class);
        when(ai.ask(anyString(), anyString(), any()))
                .thenReturn(DuplicateJudgements.empty());
        reviewer = new ProblemDuplicateReviewer(ai);
    }

    @Test
    void doesNotSpendARequestOnAnEmptyCandidateList() {
        assertTrue(reviewer.review("fp", "a title", "a description", List.of())
                .safeMatches()
                .isEmpty());

        verifyNoInteractions(ai);
    }

    @Test
    void putsTheDraftAndEveryCandidateIdInFrontOfTheModel() {
        reviewer.review(
                "fp",
                "OAuth callback loses the PKCE verifier",
                "It fails about one time in five.",
                List.of(candidate("The verifier is gone by the callback."))
        );

        String prompt = capturedUserMessage();

        assertTrue(prompt.contains("OAuth callback loses the PKCE verifier"));
        assertTrue(prompt.contains("It fails about one time in five."));
        assertTrue(prompt.contains(CANDIDATE_ID.toString()));
        // The verdict turns on whether the older problem is answered, so the
        // model has to be told.
        assertTrue(prompt.contains("RESOLVED"));
        assertTrue(prompt.contains("3 published solutions"));
    }

    @Test
    void clipsACandidateBodyRatherThanPayingForAllOfIt() {
        String essay = "x".repeat(5_000);

        reviewer.review("fp", "a long standing problem", "", List.of(candidate(essay)));

        String prompt = capturedUserMessage();

        assertTrue(prompt.contains("x".repeat(600)));
        assertFalse(prompt.contains("x".repeat(601)));
    }

    @Test
    void asksForTheJudgementShapeItKnowsHowToRead() {
        reviewer.review("fp", "a long standing problem", "", List.of(candidate("body")));

        verify(ai).ask(anyString(), anyString(), eq(DuplicateJudgements.class));
    }

    private String capturedUserMessage() {
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(ai).ask(anyString(), user.capture(), any());
        return user.getValue();
    }

    private static DuplicateCandidate candidate(String description) {
        return new DuplicateCandidate(
                CANDIDATE_ID,
                "PKCE verifier lost on the OAuth callback",
                description,
                null,
                ProblemStatus.RESOLVED,
                3L,
                120L
        );
    }
}
