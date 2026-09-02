package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateCandidateTest {

    @Test
    void collapsesTheMarkdownWhitespaceThatWouldWreckACard() {
        String description = """
                The verifier is gone by the time the callback runs.

                ```java
                var verifier = session.get("pkce");
                ```
                """;

        String excerpt = candidate(description).excerpt();

        assertFalse(excerpt.contains("\n"));
        assertFalse(excerpt.contains("  "));
        assertTrue(excerpt.startsWith("The verifier is gone"));
    }

    @Test
    void cutsAtAWordBoundaryWithoutAddingAnEllipsis() {
        String excerpt = candidate("word ".repeat(200)).excerpt();

        assertTrue(excerpt.length() <= 240);
        assertTrue(excerpt.endsWith("word"));
        // Whether truncation is drawn is the client's styling decision.
        assertFalse(excerpt.endsWith("..."));
        assertFalse(excerpt.endsWith("…"));
    }

    /**
     * A stack trace or a long URL has no space to break on. Breaking at the
     * last one would return almost nothing, so the cut is taken hard.
     */
    @Test
    void takesAHardCutWhenThereIsNoWordBoundaryToUse() {
        String excerpt = candidate("x".repeat(1_000)).excerpt();

        assertEquals(240, excerpt.length());
    }

    @Test
    void leavesAShortDescriptionWholeAndAnAbsentOneNull() {
        assertEquals("Short one.", candidate("  Short one.  ").excerpt());
        assertNull(candidate(null).excerpt());
        assertNull(candidate("   ").excerpt());
    }

    /**
     * The case that made {@code solved()} more than a status check: plenty of
     * authors accept an answer and never come back to close the problem. Read
     * only the status column and exactly the problems this panel exists to
     * surface are the ones it hides.
     */
    @Test
    void countsAnAcceptedAnswerAsSolvedWhateverTheStatusColumnSays() {
        assertTrue(candidate("body", ProblemStatus.PUBLISHED, 1L).solved());
        assertTrue(candidate("body", ProblemStatus.RESOLVED, 0L).solved());
        assertFalse(candidate("body", ProblemStatus.PUBLISHED, 0L).solved());
        assertFalse(candidate("body", ProblemStatus.CLOSED, 0L).solved());
    }

    private static DuplicateCandidate candidate(String description) {
        return candidate(description, ProblemStatus.RESOLVED, 1L);
    }

    private static DuplicateCandidate candidate(
            String description,
            ProblemStatus status,
            long acceptedSolutionCount
    ) {
        return new DuplicateCandidate(
                UUID.randomUUID(),
                "PKCE verifier lost on the OAuth callback",
                description,
                null,
                status,
                3L,
                acceptedSolutionCount,
                120L
        );
    }
}
