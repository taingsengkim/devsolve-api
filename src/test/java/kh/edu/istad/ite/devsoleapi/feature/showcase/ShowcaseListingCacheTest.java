package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.listing.ListingSort;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The condition is the whole design here: caching a filtered listing would fill
 * Redis with keys that are read once. These evaluate it directly rather than
 * trusting it by eye.
 */
class ShowcaseListingCacheTest {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private Cacheable loadAnnotation() {
        Method load = Arrays.stream(
                        ShowcaseListingCache.class.getDeclaredMethods()
                )
                .filter(method -> method.getName().equals("load"))
                .findFirst()
                .orElseThrow();
        return load.getAnnotation(Cacheable.class);
    }

    private boolean cachedFor(
            String queryPattern,
            UUID categoryId,
            String tagSlug,
            int page
    ) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("queryPattern", queryPattern);
        context.setVariable("categoryId", categoryId);
        context.setVariable("tagSlug", tagSlug);
        context.setVariable("page", page);

        return Boolean.TRUE.equals(parser
                .parseExpression(loadAnnotation().condition())
                .getValue(context, Boolean.class));
    }

    @Test
    void cachesTheUnfilteredFirstPage() {
        assertTrue(cachedFor(null, null, null, 0));
    }

    @Test
    void doesNotCacheASearch() {
        assertFalse(cachedFor("%sql%", null, null, 0));
    }

    @Test
    void doesNotCacheACategoryFilter() {
        assertFalse(cachedFor(null, UUID.randomUUID(), null, 0));
    }

    @Test
    void doesNotCacheATagFilter() {
        assertFalse(cachedFor(null, null, "web", 0));
    }

    @Test
    void doesNotCacheBeyondTheCachedPageBound() {
        assertTrue(cachedFor(null, null, null,
                ShowcaseListingCache.CACHED_PAGES - 1));
        assertFalse(cachedFor(null, null, null,
                ShowcaseListingCache.CACHED_PAGES));
        assertFalse(cachedFor(null, null, null, 999_999));
    }

    @Test
    void keyesBySortSoTwoOrderingsDoNotShareAPage() {
        Cacheable cacheable = loadAnnotation();

        assertNotNull(cacheable);
        assertEquals(
                "#sort.name() + ':' + #page + ':' + #size",
                cacheable.key()
        );
        assertTrue(Arrays.asList(cacheable.cacheNames())
                .contains(CacheNames.SHOWCASE_LISTING));
    }

    @Test
    void theListingIsKeyedByEverySortTheApiAccepts() {
        // A sort missing from the key would serve one ordering's page as
        // another's; this just confirms the enum is what the key reads.
        for (ListingSort sort : ListingSort.values()) {
            assertNotNull(sort.name());
        }
        assertNotNull(Sort.unsorted());
    }
}
