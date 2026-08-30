package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseDetailParts;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagService;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowCaseStepRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The tags and steps behind a showcase detail, cached per showcase.
 *
 * <p>A separate bean because {@code @Cacheable} is proxy-applied: annotating a
 * method the service calls on itself would cache nothing, silently.
 *
 * <p>Only these two reads are cached. The showcase row is loaded fresh by the
 * caller on every request, so a soft delete, a moderation change or an edit to
 * the showcase itself takes effect immediately no matter what is cached here.
 */
@Component
@RequiredArgsConstructor
public class ShowcaseDetailCache {

    private final ShowCaseStepRepository showcaseStepRepository;
    private final ShowcaseStepMapper showcaseStepMapper;
    private final ShowcaseTagService showcaseTagService;

    @Cacheable(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
    @Transactional(readOnly = true)
    public ShowcaseDetailParts load(UUID showcaseId) {
        return new ShowcaseDetailParts(
                List.copyOf(showcaseTagService.tagsOfShowcase(showcaseId)),
                showcaseStepRepository
                        .findByShowcase_IdOrderByStepNumberAsc(showcaseId)
                        .stream()
                        .map(showcaseStepMapper::mapShowcaseStepToShowcaseStepResponse)
                        .toList()
        );
    }
}
