package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import java.util.List;

/**
 * One cached page of the public problem feed.
 *
 * @param totalElements carried alongside the rows because the caller rebuilds
 *                      the {@code Page} from this, and a lost total is a
 *                      silently truncated feed rather than a visible error.
 */
public record ProblemListingSlice(
        List<CachedProblem> content,
        long totalElements
) {
}
