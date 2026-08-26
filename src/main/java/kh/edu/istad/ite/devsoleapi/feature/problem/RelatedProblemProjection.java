package kh.edu.istad.ite.devsoleapi.feature.problem;

import java.util.UUID;

/**
 * One row of the "related problems" panel.
 *
 * <p>Deliberately narrow. The panel re-queries on almost every keystroke of a
 * draft title, so it must not drag in the per-viewer counters
 * {@link ProblemResponseEnricher} loads for the feed — those cost five extra
 * round trips per page and none of them mean anything in a suggestion list.
 * The solution count is the one counter worth carrying, and it rides along as
 * a scalar subquery in the same statement.
 */
public interface RelatedProblemProjection {

    UUID getId();

    String getTitle();

    /**
     * The raw column value. Native results arrive as text, so the enum is
     * rebuilt in the service rather than here.
     */
    String getStatus();

    long getSolutionCount();

    long getViewCount();
}
