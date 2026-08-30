package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;

/**
 * The seam between what a problem response holds for everyone and what it
 * holds for the reader.
 *
 * <p>Nine of its components come from {@link ProblemResponseMetrics}: four
 * counts anyone may see, and five — the bookmark, the vote, and the three
 * permissions — that belong to one viewer. A cached response is built with
 * {@link ProblemResponseMetrics#empty()} and has all nine filled in here, per
 * request. Copying the other components by hand is the price of keeping that
 * boundary in one visible place rather than trusting every call site.
 */
final class ProblemResponses {

    private ProblemResponses() {
    }

    static ProblemResponse withMetrics(
            ProblemResponse response,
            ProblemResponseMetrics metrics
    ) {
        return new ProblemResponse(
                response.id(),
                response.author(),
                response.category(),
                response.title(),
                response.description(),
                response.problemType(),
                response.sdlcPhase(),
                response.severity(),
                response.expectedBehavior(),
                response.actualBehavior(),
                response.reproductionSteps(),
                response.environment(),
                response.attemptsTried(),
                response.errorMessage(),
                response.repositoryUrl(),
                response.status(),
                response.viewCount(),
                response.technologies(),
                response.tags(),
                response.attachments(),
                response.contentWarnings(),
                metrics.solutionCount(),
                metrics.commentCount(),
                metrics.voteScore(),
                metrics.bookmarkCount(),
                response.acceptedSolutionIds(),
                metrics.bookmarkedByViewer(),
                metrics.viewerVote(),
                metrics.canEdit(),
                metrics.canDelete(),
                metrics.canAcceptSolution(),
                response.publishedAt(),
                response.deletedAt(),
                response.version(),
                response.createdAt(),
                response.updatedAt()
        );
    }

    /** True when nothing viewer-specific has been filled in yet. */
    static boolean carriesNoViewerState(ProblemResponse response) {
        return !response.isBookmarkedByViewer()
                && response.viewerVote() == null
                && !response.canEdit()
                && !response.canDelete()
                && !response.canAcceptSolution();
    }
}
