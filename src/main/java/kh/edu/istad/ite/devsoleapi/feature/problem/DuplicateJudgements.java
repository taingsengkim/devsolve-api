package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.DuplicateVerdict;

import java.util.List;

/**
 * What the model hands back, and the schema it is constrained to.
 *
 * <p>These records are the contract in both directions. {@code GeminiSchemas}
 * derives a JSON schema from their components and the model is held to it, so
 * there is no "it returned prose today" branch to write — the single parse that
 * remains, in the client, is of a document the API has already promised matches
 * that schema. They are also what goes into Redis, keyed by a fingerprint of
 * the draft and the candidate ids, which is why they hold ids rather than
 * object references.
 *
 * <p>Wrapped in a record rather than being a bare list because a JSON schema
 * describes an object; a top-level array has nowhere to hang the field
 * descriptions the model reads.
 */
public record DuplicateJudgements(List<DuplicateJudgement> matches) {

    public static DuplicateJudgements empty() {
        return new DuplicateJudgements(List.of());
    }

    public List<DuplicateJudgement> safeMatches() {
        return matches == null ? List.of() : matches;
    }

    /**
     * @param id         copied from the candidate list. Validated against it on
     *                   the way back — a model that invents one is not trusted
     *                   to have invented a matching verdict either.
     * @param confidence 0-100, about the classification rather than about how
     *                   alike the words are
     * @param reason     one sentence for the author, naming what the two share
     */
    public record DuplicateJudgement(
            String id,
            DuplicateVerdict verdict,
            int confidence,
            String reason
    ) {
    }
}
