package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.listing.ListingSort;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseListingSlice;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseTagResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagService;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The showcase listing query, behind a cache. A separate bean because
 * {@code @Cacheable} is proxy-applied.
 *
 * <p>Comment counts are not in here — the caller applies them per request, so
 * the busiest number on a card never comes from the cache.
 */
@Component
@RequiredArgsConstructor
public class ShowcaseListingCache {

    /**
     * Only the first pages are cached, and only unfiltered ones. Repeated in
     * the {@code condition} below, which SpEL cannot read this field from;
     * {@code ShowcaseListingCacheTest} pins the two together.
     */
    public static final int CACHED_PAGES = 10;

    private final ShowCasesRepository showCaseRepository;
    private final ShowCasesMapper showCasesMapper;
    private final ShowcaseTagService showcaseTagService;

    /**
     * Cached only when nothing is filtered: a search or a tag makes every
     * request its own key, read once and never hit again.
     */
    @Cacheable(
            cacheNames = CacheNames.SHOWCASE_LISTING,
            key = "#sort.name() + ':' + #page + ':' + #size",
            condition = "#queryPattern == null && #categoryId == null "
                    + "&& #tagSlug == null && #page < 10"
    )
    @Transactional(readOnly = true)
    public ShowcaseListingSlice load(
            String queryPattern,
            UUID categoryId,
            String tagSlug,
            ListingSort sort,
            int page,
            int size,
            Sort columnSort
    ) {
        Page<ShowCases> showcases = sort.isScoreOrdered()
                ? showCaseRepository.searchPublishedByScore(
                        ReviewStatus.APPROVED,
                        queryPattern,
                        categoryId,
                        tagSlug,
                        sort.windowStart(),
                        VoteType.SHOWCASE,
                        PageRequest.of(page, size)
                )
                : showCaseRepository.searchPublished(
                        ReviewStatus.APPROVED,
                        queryPattern,
                        categoryId,
                        tagSlug,
                        PageRequest.of(page, size, columnSort)
                );

        Map<UUID, List<ShowcaseTagResponse>> tagsByShowcaseId =
                showcaseTagService.tagsOfShowcases(
                        showcases.getContent().stream()
                                .map(ShowCases::getId)
                                .toList()
                );

        List<ShowCasesSummaryResponse> content = showcases.getContent().stream()
                .map(showcase -> showCasesMapper.mapShowCaseToSummaryResponse(
                        showcase,
                        tagsByShowcaseId.getOrDefault(
                                showcase.getId(),
                                List.of()
                        )
                ))
                .toList();

        return new ShowcaseListingSlice(content, showcases.getTotalElements());
    }
}
