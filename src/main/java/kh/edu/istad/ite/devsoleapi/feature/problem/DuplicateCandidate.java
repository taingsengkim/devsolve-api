package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;

import java.util.UUID;

/**
 * One published problem that might be what the author is about to write, as it
 * travels from the retrieval step to the model and on into the response.
 *
 * <p>Plain values, detached from Hibernate on purpose. The model call that
 * reads these takes seconds, and holding a transaction — and the connection
 * under it — open across a network call to a third party is how a pool runs
 * dry under load that a database would not have noticed.
 */
public record DuplicateCandidate(
        UUID id,
        String title,
        String description,
        String errorMessage,
        ProblemStatus status,
        long solutionCount,
        long acceptedSolutionCount,
        long viewCount
) {

    /**
     * Two ways a problem can already be answered, and the second one matters.
     *
     * <p>{@code RESOLVED} is the author closing the loop, and plenty of authors
     * never come back to do it. An accepted solution is the same claim made by
     * the same person through a different action, and a problem carrying one is
     * answered whatever its status column says. Reading only the status hides
     * exactly the problems this panel exists to surface.
     *
     * <p>Note that {@code GET /api/v1/problems/related} still means
     * {@code RESOLVED} alone by this word — it runs on a deliberately narrow
     * projection that does not load accepted solutions.
     */
    public boolean solved() {
        return status == ProblemStatus.RESOLVED || acceptedSolutionCount > 0;
    }

    /**
     * The opening of the description, cleaned up enough to sit under a title in
     * a suggestion card.
     *
     * <p>Whitespace is collapsed because a description is markdown: it arrives
     * full of newlines, indented code fences and blank lines, and dropping that
     * into a two-line card produces a ragged mess or a card that renders as a
     * single word. Collapsing gives the client one clean paragraph to clamp.
     *
     * <p>Cut at a word boundary and returned without an ellipsis — whether the
     * text is truncated visibly is a styling decision, and CSS line clamping
     * already draws one.
     */
    public String excerpt() {
        if (description == null) {
            return null;
        }
        String collapsed = description.strip().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) {
            return null;
        }
        if (collapsed.length() <= EXCERPT_LENGTH) {
            return collapsed;
        }
        // A description whose first 240 characters hold no space at all is one
        // long token — a stack trace or a URL — and breaking it at the last
        // space would return almost nothing. Take the hard cut instead.
        int lastSpace = collapsed.lastIndexOf(' ', EXCERPT_LENGTH);
        int cut = lastSpace < EXCERPT_LENGTH / 2 ? EXCERPT_LENGTH : lastSpace;
        return collapsed.substring(0, cut);
    }

    /** About two lines in a card, which is what the panel has room for. */
    private static final int EXCERPT_LENGTH = 240;
}
