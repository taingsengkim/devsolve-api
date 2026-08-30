package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.listing.ListingSort;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CachedProblem;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemListingSlice;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * The public problem feed, behind a cache. A separate bean because
 * {@code @Cacheable} is proxy-applied.
 *
 * <p>What is cached is the search and the associations, never the viewer's
 * half: a problem response says whether you voted, whether you bookmarked and
 * whether you may edit it, and one reader's copy handed to the next would be a
 * leak rather than a stale number. Every response leaves here with its viewer
 * state blank, and the caller fills it in per request.
 */
@Component
@RequiredArgsConstructor
public class ProblemListingCache {

    /**
     * Only the first pages are cached, and only unfiltered ones. Repeated in
     * the {@code condition} below, which SpEL cannot read this field from;
     * {@code ProblemListingCacheTest} pins the two together.
     */
    public static final int CACHED_PAGES = 10;

    private final ProblemRepository problemRepository;
    private final ProblemResponseAssembler assembler;

    /**
     * Cached only when nothing is filtered: a search, a tag, a technology or a
     * category makes every request its own key, read once and never hit again.
     *
     * <p>{@code status} is in the key rather than the condition — it is a
     * small enum and a feed people actually browse, not an open-ended filter.
     *
     * <p>{@code ordering} is in the key because only the score-ordered sorts
     * are fixed by the query; the rest take their order from the pageable, so
     * keying on {@link ListingSort} alone would serve "oldest first" to
     * someone who asked for "by title".
     */
    @Cacheable(
            cacheNames = CacheNames.PROBLEM_LISTING,
            key = "#ordering + ':' + #status + ':' + #page + ':' + #size",
            condition = "#categoryId == null && #sdlcPhase == null "
                    + "&& #tagSlug == null && #technologyName == null "
                    + "&& #queryPattern == null && !#unansweredOnly "
                    + "&& #page < 10",
            sync = true
    )
    @Transactional(readOnly = true)
    public ProblemListingSlice load(
            UUID categoryId,
            SdlcPhase sdlcPhase,
            String tagSlug,
            String technologyName,
            String queryPattern,
            ProblemStatus status,
            boolean unansweredOnly,
            ListingSort sort,
            String ordering,
            int page,
            int size,
            Pageable columnPageable
    ) {
        Page<Problem> problems = sort.isScoreOrdered()
                ? problemRepository.findPublishedByScore(
                        categoryId,
                        sdlcPhase,
                        tagSlug,
                        technologyName,
                        queryPattern,
                        status,
                        unansweredOnly,
                        windowStart(sort),
                        VoteType.PROBLEM,
                        PageRequest.of(page, size)
                )
                : problemRepository.findPublished(
                        categoryId,
                        sdlcPhase,
                        tagSlug,
                        technologyName,
                        queryPattern,
                        status,
                        unansweredOnly,
                        columnPageable
                );

        List<Problem> rows = problems.getContent();
        ProblemResponseAssembler.Associations associations =
                assembler.load(rows);

        List<CachedProblem> content = rows.stream()
                .map(problem -> new CachedProblem(
                        assembler.toResponse(problem, associations),
                        problem.getAuthorId()
                ))
                .toList();

        return new ProblemListingSlice(content, problems.getTotalElements());
    }

    private static Instant windowStart(ListingSort sort) {
        LocalDateTime window = sort.windowStart();
        return window == null
                ? null
                : window.atZone(ZoneOffset.UTC).toInstant();
    }
}
