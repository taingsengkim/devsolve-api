package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.DuplicateVerdict;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;

import java.util.UUID;

/**
 * One existing problem, offered to somebody drafting a new one.
 *
 * <p>The first seven fields come from the database and are always filled. The
 * last three come from the model and are null together when
 * {@link DuplicateCheckResponse#aiReviewed()} is false — a client that only
 * ever renders the title and the solved badge needs no branch for that case.
 *
 * @param excerpt    the opening of the description, whitespace collapsed and
 *                   cut at a word boundary. Enough to tell two similarly titled
 *                   problems apart without opening either. Null when the
 *                   problem has no description.
 * @param solved     true when the problem reached {@code RESOLVED} <em>or</em>
 *                   carries an accepted solution. Derived here so the frontend
 *                   does not have to know which of those count — badge on this
 *                   one field and it stays correct if the rule changes.
 * @param acceptedSolutionCount
 *                   answers the author marked as the one that worked. Non-zero
 *                   is the strongest "this is really answered" a card can show,
 *                   and is worth saying differently from a bare reply count.
 * @param verdict    how the model related this to the draft
 * @param confidence 0-100, and about the classification rather than about how
 *                   alike the words are. Sort within a verdict, do not print.
 * @param reason     one sentence for the author, naming what the two share.
 *                   Plain text, safe to render as-is.
 */
public record DuplicateSuggestion(
        UUID id,
        String title,

        @Schema(nullable = true, description = "Plain text, whitespace collapsed. Clamp it; do not render as markdown.")
        String excerpt,

        ProblemStatus status,
        boolean solved,
        long solutionCount,
        long acceptedSolutionCount,
        long viewCount,

        @Schema(nullable = true, description = "Null when aiReviewed is false.")
        DuplicateVerdict verdict,

        @Schema(nullable = true, description = "Null when aiReviewed is false.")
        Integer confidence,

        @Schema(nullable = true, description = "Null when aiReviewed is false.")
        String reason
) {
}
