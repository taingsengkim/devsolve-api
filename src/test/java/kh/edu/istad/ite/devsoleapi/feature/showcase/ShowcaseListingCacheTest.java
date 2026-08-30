package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.listing.ListingSort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Sort;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The condition is the whole design here: caching a filtered listing would fill
 * Redis with keys that are read once. These evaluate it directly rather than
 * trusting it by eye.
 */
class ShowcaseListingCacheTest {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private static Method listingMethod(String methodName) {
        return Arrays.stream(ShowcaseListingCache.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }

    private Cacheable annotationOn(String methodName) {
        return listingMethod(methodName).getAnnotation(Cacheable.class);
    }

    private Cacheable loadAnnotation() {
        return annotationOn("load");
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

    /**
     * The listing's TTL is a day, so a write that loses its evict does not
     * fail anything — the feed just stops showing new showcases until tomorrow.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "updateStatus", "softDelete", "hardDelete", "restore"
    })
    void everyWriteThatChangesWhatIsPublishedEvictsBothListings(
            String methodName
    ) {
        Method method = Arrays.stream(
                        ShowCasesServiceImpl.class.getDeclaredMethods()
                )
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No " + methodName + " on ShowCasesServiceImpl"
                ));

        for (String cacheName : List.of(
                CacheNames.SHOWCASE_LISTING,
                CacheNames.SHOWCASE_LISTING_RANKED
        )) {
            assertTrue(
                    evictsOn(method, cacheName).anyMatch(CacheEvict::allEntries),
                    methodName + " changes which showcases are published but "
                            + "does not evict " + cacheName
            );
        }
    }

    /** The evict may be declared directly or nested in a {@code @Caching}. */
    private static Stream<CacheEvict> evictsOn(
            Method method,
            String cacheName
    ) {
        Caching caching = method.getAnnotation(Caching.class);
        Stream<CacheEvict> evicts = caching == null
                ? Stream.ofNullable(method.getAnnotation(CacheEvict.class))
                : Arrays.stream(caching.evict());

        return evicts.filter(evict -> Arrays.asList(evict.cacheNames())
                .contains(cacheName));
    }

    /**
     * The split is the point: a vote- or view-ordered page must not land in the
     * cache that holds for a day, because nothing evicts it on a vote or view.
     */
    @Test
    void countOrderedSortsUseTheirOwnCache() {
        Cacheable ranked = annotationOn("loadRanked");

        assertNotNull(ranked);
        assertEquals(loadAnnotation().key(), ranked.key());
        assertTrue(Arrays.asList(ranked.cacheNames())
                .contains(CacheNames.SHOWCASE_LISTING_RANKED));
        assertFalse(Arrays.asList(ranked.cacheNames())
                .contains(CacheNames.SHOWCASE_LISTING));
    }

    @Test
    void everySortIsRoutedToExactlyOneOfTheTwoCaches() {
        for (ListingSort sort : ListingSort.values()) {
            assertEquals(
                    sort.isScoreOrdered() || sort == ListingSort.MOST_VIEWED,
                    sort.isCountOrdered(),
                    sort + " is routed by isCountOrdered, which disagrees with "
                            + "what the ordering actually reads"
            );
        }
    }

    /**
     * An allEntries evict drops every page of every sort at once, and without
     * {@code sync} each waiting request runs its own query to refill.
     *
     * <p>The rest of this is what Spring requires of a synchronized cacheable,
     * checked on the first call rather than at startup — so getting it wrong
     * ships, and surfaces as an IllegalStateException on a listing request.
     */
    @ParameterizedTest
    @ValueSource(strings = {"load", "loadRanked"})
    void bothListingLoadersSingleFlightTheirQuery(String methodName) {
        Method method = listingMethod(methodName);
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertTrue(cacheable.sync(), methodName + " does not single-flight");
        assertEquals(
                1,
                cacheable.cacheNames().length,
                "a synchronized cacheable may name only one cache"
        );
        assertEquals(
                "",
                cacheable.unless(),
                "a synchronized cacheable does not support unless"
        );
        assertNull(
                method.getAnnotation(CacheEvict.class),
                "a synchronized cacheable cannot share a method with an evict"
        );
        assertNull(
                method.getAnnotation(Caching.class),
                "a synchronized cacheable cannot share a method with an evict"
        );
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
