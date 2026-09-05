package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.RelatedShowcaseResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseDetailParts;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseTagResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagService;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowCaseStepRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The tags, steps and neighbours behind a showcase detail, cached per showcase.
 *
 * <p>A separate bean because {@code @Cacheable} is proxy-applied: annotating a
 * method the service calls on itself would cache nothing, silently.
 *
 * <p>Nothing here depends on who is asking, so one entry serves everyone. The
 * showcase row is loaded fresh by the caller on every request, and so is every
 * counter and viewer flag, so a soft delete, a moderation change, a vote or an
 * edit to the showcase itself takes effect immediately no matter what is
 * cached here.
 */
@Component
@RequiredArgsConstructor
public class ShowcaseDetailCache {

    /**
     * Six fills two rows of cards on a desktop layout and scrolls once on a
     * phone. Enough to be worth a strip, few enough that the query stays a
     * limited index scan.
     */
    private static final int RELATED_LIMIT = 6;

    private final ShowCasesRepository showCaseRepository;
    private final ShowCaseStepRepository showcaseStepRepository;
    private final ShowcaseStepMapper showcaseStepMapper;
    private final ShowcaseTagService showcaseTagService;
    private final ShowCasesMapper showCasesMapper;

    /**
     * @param categoryId the showcase's own category, passed in rather than
     *                   re-read: the caller already holds the row. It is not
     *                   part of the key — a showcase moving category evicts the
     *                   entry like any other edit to it.
     */
    @Cacheable(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
    @Transactional(readOnly = true)
    public ShowcaseDetailParts load(UUID showcaseId, UUID categoryId) {
        List<ShowcaseTagResponse> tags = List.copyOf(
                showcaseTagService.tagsOfShowcase(showcaseId)
        );

        return new ShowcaseDetailParts(
                tags,
                showcaseStepRepository
                        .findByShowcase_IdOrderByStepNumberAsc(showcaseId)
                        .stream()
                        .map(showcaseStepMapper::mapShowcaseStepToShowcaseStepResponse)
                        .toList(),
                related(showcaseId, categoryId, tags)
        );
    }

    /**
     * Matches on tags first, and falls back to the category for a showcase that
     * has none — a tagless showcase would otherwise match on an empty tag list,
     * which is both an empty {@code IN} and a question with no answer.
     */
    private List<RelatedShowcaseResponse> related(
            UUID showcaseId,
            UUID categoryId,
            List<ShowcaseTagResponse> tags
    ) {
        PageRequest limit = PageRequest.of(0, RELATED_LIMIT);
        List<ShowCases> neighbours;

        if (!tags.isEmpty()) {
            neighbours = showCaseRepository.findRelatedByTags(
                    showcaseId,
                    categoryId,
                    tags.stream().map(ShowcaseTagResponse::id).toList(),
                    ReviewStatus.APPROVED,
                    limit
            );
        } else if (categoryId != null) {
            neighbours = showCaseRepository.findRelatedByCategory(
                    showcaseId,
                    categoryId,
                    ReviewStatus.APPROVED,
                    limit
            );
        } else {
            neighbours = List.of();
        }

        return neighbours.stream()
                .map(showCasesMapper::mapShowCaseToRelatedResponse)
                .toList();
    }
}
