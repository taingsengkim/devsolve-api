package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CachedProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * One problem's viewer-independent half, cached per problem.
 *
 * <p>A separate bean because {@code @Cacheable} is proxy-applied: annotating a
 * method the service calls on itself would cache nothing, silently.
 *
 * <p>The caller loads the problem row and checks visibility before asking for
 * this, so a draft, a soft delete or a moderation change decides access on
 * every request no matter what is cached.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailCache {

    private final ProblemResponseAssembler assembler;

    /**
     * @param problem the row the caller has already loaded and authorised;
     *                taken as an argument rather than re-read so this cannot
     *                serve a problem the caller was not allowed to see
     */
    @Cacheable(
            cacheNames = CacheNames.PROBLEM_DETAIL,
            key = "#problemId",
            sync = true
    )
    @Transactional(readOnly = true)
    public CachedProblem load(UUID problemId, Problem problem) {
        return new CachedProblem(
                assembler.toResponse(problem, assembler.load(List.of(problem))),
                problem.getAuthorId()
        );
    }
}
