package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.DuplicateVerdict;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;

import java.util.UUID;

/**
 * One existing problem, offered to somebody drafting a new one.
 *
 * <p>The first six fields come from the database and are always filled. The
 * last three come from the model and are null together when
 * {@link DuplicateCheckResponse#aiReviewed()} is false — a client that only
 * ever renders the title and the solved badge needs no branch for that case.
 *
 * @param solved     derived from {@code status} so the frontend does not have
 *                   to know which statuses count as solved
 * @param verdict    how the model related this to the draft
 * @param confidence 0-100, and about the classification rather than about how
 *                   alike the words are. Sort within a verdict, do not print.
 * @param reason     one sentence for the author, naming what the two share.
 *                   Plain text, safe to render as-is.
 */
public record DuplicateSuggestion(
        UUID id,
        String title,
        ProblemStatus status,
        boolean solved,
        long solutionCount,
        long viewCount,

        @Schema(nullable = true, description = "Null when aiReviewed is false.")
        DuplicateVerdict verdict,

        @Schema(nullable = true, description = "Null when aiReviewed is false.")
        Integer confidence,

        @Schema(nullable = true, description = "Null when aiReviewed is false.")
        String reason
) {

    /** The shape served when the model was not consulted. */
    public static DuplicateSuggestion unreviewed(
            UUID id,
            String title,
            ProblemStatus status,
            long solutionCount,
            long viewCount
    ) {
        return new DuplicateSuggestion(
                id,
                title,
                status,
                status == ProblemStatus.RESOLVED,
                solutionCount,
                viewCount,
                null,
                null,
                null
        );
    }
}
