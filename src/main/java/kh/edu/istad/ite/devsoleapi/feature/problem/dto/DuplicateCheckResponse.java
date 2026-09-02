package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What the platform already holds that looks like the draft.
 *
 * <p>{@code aiReviewed} is the honest part of this record. The endpoint answers
 * whether or not a model is configured and whether or not it was reachable, and
 * the two answers are worth different amounts: a reviewed list has been read
 * for meaning and the noise taken out of it, an unreviewed one is keyword
 * matching and no more. Callers are told which they got rather than having to
 * infer it from whether {@code reason} happens to be filled.
 *
 * @param suggestions most useful first. Empty is a normal, common answer.
 */
public record DuplicateCheckResponse(

        @Schema(description = "True when a model read the candidates. False means keyword matches only.")
        boolean aiReviewed,

        List<DuplicateSuggestion> suggestions
) {

    public static DuplicateCheckResponse none() {
        return new DuplicateCheckResponse(false, List.of());
    }
}
