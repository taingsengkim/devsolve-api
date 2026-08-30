package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemSeverity;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemType;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code withMetrics} copies thirty-odd components by hand, and a forgotten
 * one would blank a field on every cached read without failing anything.
 */
class ProblemResponsesTest {

    private static final UUID PROBLEM_ID = UUID.randomUUID();
    private static final UUID ACCEPTED_SOLUTION_ID = UUID.randomUUID();

    private static ProblemResponse cached() {
        return new ProblemResponse(
                PROBLEM_ID,
                new ProblemResponse.AuthorSummary(
                        UUID.randomUUID(),
                        "Sok Dara",
                        "https://example.test/avatar.png",
                        420
                ),
                new ProblemResponse.CategorySummary(
                        UUID.randomUUID(),
                        "Web Security",
                        "web-security",
                        CategoryScope.PROBLEM
                ),
                "JWT verification fails",
                "The token is rejected",
                ProblemType.BUG,
                SdlcPhase.DEVELOPMENT,
                ProblemSeverity.HIGH,
                "It should verify",
                "It throws",
                List.of("Sign a token", "Verify it"),
                List.of(new ProblemResponse.EnvironmentSummary("Java", "21")),
                "Tried rotating the key",
                "SignatureException",
                "https://github.test/acme/api",
                ProblemStatus.PUBLISHED,
                94L,
                List.of(new ProblemResponse.TechnologySummary(
                        UUID.randomUUID(), "Spring", "6.2"
                )),
                List.of(new ProblemResponse.TagSummary(
                        UUID.randomUUID(), "JWT", "jwt"
                )),
                List.of(new ProblemResponse.AttachmentSummary(
                        UUID.randomUUID(),
                        "stack.txt",
                        "text/plain",
                        1_024L,
                        UUID.randomUUID(),
                        Instant.parse("2026-08-30T09:00:00Z"),
                        "https://example.test/stack.txt"
                )),
                List.of("profanity"),
                // The nine metric components, blank as the cache stores them.
                0L,
                0L,
                0L,
                0L,
                List.of(ACCEPTED_SOLUTION_ID),
                false,
                null,
                false,
                false,
                false,
                Instant.parse("2026-08-29T10:00:00Z"),
                null,
                7L,
                LocalDateTime.of(2026, 8, 29, 9, 0),
                LocalDateTime.of(2026, 8, 30, 11, 30)
        );
    }

    @Test
    void fillsInEveryMetricComponent() {
        ProblemResponse result = ProblemResponses.withMetrics(
                cached(),
                new ProblemResponseMetrics(
                        3L,
                        12L,
                        45L,
                        6L,
                        true,
                        "UP",
                        true,
                        true,
                        true
                )
        );

        assertEquals(3L, result.solutionCount());
        assertEquals(12L, result.commentCount());
        assertEquals(45L, result.voteScore());
        assertEquals(6L, result.bookmarkCount());
        assertTrue(result.isBookmarkedByViewer());
        assertEquals("UP", result.viewerVote());
        assertTrue(result.canEdit());
        assertTrue(result.canDelete());
        assertTrue(result.canAcceptSolution());
    }

    @Test
    void carriesEveryOtherComponentThroughUnchanged() {
        ProblemResponse original = cached();
        ProblemResponse result = ProblemResponses.withMetrics(
                original,
                ProblemResponseMetrics.empty()
        );

        // With empty metrics the two must be identical, which is the cheapest
        // way to assert that no component was dropped or reordered.
        assertEquals(original, result);
    }

    @Test
    void recognisesAResponseThatStillHasNoViewerState() {
        assertTrue(ProblemResponses.carriesNoViewerState(cached()));
    }

    @Test
    void recognisesAResponseThatHasBeenGivenViewerState() {
        ProblemResponse enriched = ProblemResponses.withMetrics(
                cached(),
                new ProblemResponseMetrics(
                        0L, 0L, 0L, 0L, true, "DOWN", false, false, false
                )
        );

        assertFalse(ProblemResponses.carriesNoViewerState(enriched));
    }

    @Test
    void keepsTheAcceptedSolutionsWhichAreNotAMetric() {
        ProblemResponse result = ProblemResponses.withMetrics(
                cached(),
                new ProblemResponseMetrics(
                        1L, 1L, 1L, 1L, true, "UP", true, true, true
                )
        );

        // These come off the problem, not the enricher, so they must survive
        // a metrics pass rather than being reset with the counts around them.
        assertEquals(List.of(ACCEPTED_SOLUTION_ID), result.acceptedSolutionIds());
        assertNull(result.deletedAt());
        assertEquals(7L, result.version());
    }
}
